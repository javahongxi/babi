package org.hongxi.babi.graph.hook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.util.SessionContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A LangGraph4J {@link EdgeHook.WrapCall} that intercepts the "action" edge
 * (tool execution node) and publishes tool-call events to the {@link ToolEventBus}.
 *
 * <p>This replaces the previous approach where each tool had to extend
 * {@code AbstractNotifyingTool} and manually call {@code emitEvent()}.
 * By hooking into the graph's action edge, all tool calls are captured
 * in a single, centralized location — keeping tool classes as plain POJOs.
 *
 * <p>The session ID is read from {@link SessionContextHolder} (ThreadLocal),
 * which is set by {@code BabiService} before invoking the graph.
 */
public class ToolNotificationEdgeHook implements EdgeHook.WrapCall<AgentExecutor.State> {

    private static final Logger log = LoggerFactory.getLogger(ToolNotificationEdgeHook.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolEventBus eventBus;

    public ToolNotificationEdgeHook(ToolEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public CompletableFuture<Command> applyWrap(String sourceId,
                                                 AgentExecutor.State state,
                                                 org.bsc.langgraph4j.RunnableConfig config,
                                                 AsyncCommandAction<AgentExecutor.State> action) {
        publishToolCallEvents(state);
        return action.apply(state, config);
    }

    /**
     * Extract tool execution requests from the last AI message and publish events.
     */
    private void publishToolCallEvents(AgentExecutor.State state) {
        String sessionId = SessionContextHolder.getSessionId();
        if (sessionId == null || eventBus == null) {
            return;
        }

        state.lastMessage()
                .filter(m -> ChatMessageType.AI == m.type())
                .map(m -> (AiMessage) m)
                .filter(AiMessage::hasToolExecutionRequests)
                .ifPresent(ai -> {
                    for (var request : ai.toolExecutionRequests()) {
                        Map<String, Object> args = parseArguments(request.arguments());
                        try {
                            eventBus.publish(ToolEventBus.ToolEvent.toolCall(
                                    sessionId, request.name(), args));
                            log.debug("Published TOOL_CALL event: session={}, tool={}",
                                    sessionId, request.name());
                        } catch (Exception e) {
                            log.debug("Failed to publish tool event for {}: {}",
                                    request.name(), e.getMessage());
                        }
                    }
                });
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
