package org.hongxi.babi.spring.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.util.SessionContextHolder;
import org.hongxi.babi.spring.service.BabiService;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import reactor.util.context.ContextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ToolCallingManager} decorator that publishes tool-call events
 * to the {@link ToolEventBus} before delegating execution to the wrapped manager.
 *
 * <p>This replaces AgentScope's {@code ToolNotificationMiddleware} which is not
 * available in Spring AI 2.0. The {@code ToolCallingAdvisor} calls
 * {@link #executeToolCalls(Prompt, ChatResponse)} for each tool-call round,
 * giving us the perfect interception point.
 *
 * <p>The session ID is read from {@link SessionContextHolder} (ThreadLocal),
 * which is set by {@code BabiService} before invoking the ChatClient.
 */
public class NotifyingToolCallingManager implements ToolCallingManager {

    private static final Logger log = LoggerFactory.getLogger(NotifyingToolCallingManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolCallingManager delegate;
    private final ToolEventBus eventBus;

    public NotifyingToolCallingManager(ToolCallingManager delegate, ToolEventBus eventBus) {
        this.delegate = delegate;
        this.eventBus = eventBus;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        publishToolCallEvents(chatResponse);
        try {
            ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
            publishToolResultEvents(chatResponse, ToolEventBus.ToolState.SUCCESS);
            return result;
        } catch (Exception e) {
            publishToolResultEvents(chatResponse, resolveToolState(e));
            throw e;
        }
    }

    /**
     * Resolve the session ID by checking multiple propagation mechanisms:
     * <ol>
     *   <li>Reactor Context (via {@link ToolCallReactiveContextHolder}) — for streaming mode
     *       where tool execution runs on a different thread</li>
     *   <li>{@link SessionContextHolder} (ThreadLocal) — for synchronous (blocking) mode</li>
     * </ol>
     */
    private String resolveSessionId() {
        // 1. Try Reactor Context (set by ToolCallingAdvisor on the tool-execution thread)
        try {
            ContextView ctx = ToolCallReactiveContextHolder.getContext();
            if (ctx != null && ctx.hasKey(BabiService.SESSION_ID_CTX_KEY)) {
                return ctx.get(BabiService.SESSION_ID_CTX_KEY);
            }
        } catch (Exception ignored) {
            // ToolCallReactiveContextHolder may throw if not set
        }
        // 2. Fall back to ThreadLocal (works for synchronous call path)
        return SessionContextHolder.getSessionId();
    }

    /**
     * Extract tool calls from the chat response and publish each as a ToolEvent.
     */
    private void publishToolCallEvents(ChatResponse chatResponse) {
        String sessionId = resolveSessionId();
        if (sessionId == null || chatResponse == null) {
            return;
        }

        for (Generation generation : chatResponse.getResults()) {
            AssistantMessage assistantMessage = generation.getOutput();
            if (assistantMessage == null || assistantMessage.getToolCalls() == null) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                String toolName = toolCall.name() != null ? toolCall.name() : "unknown";
                Map<String, Object> inputData = parseArguments(toolCall.arguments());
                try {
                    eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, inputData));
                    log.debug("Published TOOL_CALL event: session={}, tool={}", sessionId, toolName);
                } catch (Exception e) {
                    log.debug("Failed to publish tool event for {}: {}", toolName, e.getMessage());
                }
            }
        }
    }

    /**
     * Publish tool result events after tool execution completes.
     */
    private void publishToolResultEvents(ChatResponse chatResponse, ToolEventBus.ToolState resultState) {
        String sessionId = resolveSessionId();
        if (sessionId == null || chatResponse == null) {
            return;
        }

        for (Generation generation : chatResponse.getResults()) {
            AssistantMessage assistantMessage = generation.getOutput();
            if (assistantMessage == null || assistantMessage.getToolCalls() == null) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                String toolName = toolCall.name() != null ? toolCall.name() : "unknown";
                try {
                    eventBus.publish(ToolEventBus.ToolEvent.toolResult(sessionId, toolName, resultState));
                    log.debug("Published TOOL_RESULT event: session={}, tool={}, state={}",
                            sessionId, toolName, resultState);
                } catch (Exception e) {
                    log.debug("Failed to publish tool result for {}: {}", toolName, e.getMessage());
                }
            }
        }
    }

    /**
     * Resolve the tool execution state from the exception.
     * <ul>
     *   <li>{@link InterruptedException} or {@link java.util.concurrent.CancellationException}
     *       (or in cause chain) → {@link ToolEventBus.ToolState#INTERRUPTED}</li>
     *   <li>Other exception → {@link ToolEventBus.ToolState#ERROR}</li>
     * </ul>
     */
    private static ToolEventBus.ToolState resolveToolState(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof InterruptedException
                    || cause instanceof java.util.concurrent.CancellationException) {
                return ToolEventBus.ToolState.INTERRUPTED;
            }
            cause = cause.getCause();
        }
        return ToolEventBus.ToolState.ERROR;
    }

    private static Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(arguments, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", arguments);
        }
    }
}
