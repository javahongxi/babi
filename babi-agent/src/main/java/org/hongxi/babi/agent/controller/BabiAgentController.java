package org.hongxi.babi.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.hongxi.babi.agent.eventbus.ToolEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web API for the Babi Agent (WebFlux).
 *
 * <p>All agent infrastructure (HarnessAgent, AgentStateStore, ToolEventBus, workspace)
 * is assembled by {@link org.hongxi.babi.agent.config.AgentConfiguration} and injected here.
 * This controller only handles HTTP concerns: request deduplication, SSE streaming, and response formatting.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/chat/stream} — SSE streaming (form params: message, sessionId)</li>
 *   <li>{@code GET /api/chat/send} — synchronous reply (form params: message, sessionId)</li>
 *   <li>{@code DELETE /api/chat/session} — delete a session</li>
 *   <li>{@code DELETE /api/chat/memory} — clear session memory</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class BabiAgentController {

    private static final Logger log = LoggerFactory.getLogger(BabiAgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HarnessAgent agent;
    private final AgentStateStore stateStore;
    private final ToolEventBus toolEventBus;
    private final Path workspacePath;

    /**
     * Tracks in-flight requests per session to prevent duplicate message processing.
     */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public BabiAgentController(
            HarnessAgent agent,
            AgentStateStore stateStore,
            ToolEventBus toolEventBus,
            Path workspacePath) {
        this.agent = agent;
        this.stateStore = stateStore;
        this.toolEventBus = toolEventBus;
        this.workspacePath = workspacePath;
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Merges two reactive streams into one SSE output:
     * <ol>
     *   <li><b>toolEvents</b> — tool call notifications from {@link ToolEventBus},
     *       published by ToolNotificationMiddleware before each tool execution</li>
     *   <li><b>agentEvents</b> — text deltas and tool results from {@code agent.streamEvents()}</li>
     * </ol>
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return SSE event stream
     */
    @GetMapping(path = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        log.info(">>> streamChat request: message='{}', sessionId='{}', activeSessions={}", message, sessionId, activeSessions);

        // Deduplication: reject if this session already has an in-flight request
        if (!activeSessions.add(sessionId)) {
            log.warn(">>> DUPLICATE request rejected: message='{}', sessionId='{}', activeSessions={}", message, sessionId, activeSessions);
            return Flux.just(sse("done", Map.of("type", "done", "duplicate", true)));
        }
        log.info(">>> Request accepted, starting agent for session='{}'", sessionId);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .build();

        // 1. 工具事件流 — 由 ToolNotificationMiddleware 发布到 ToolEventBus
        Flux<ServerSentEvent<String>> toolEvents = Flux.defer(() ->
                toolEventBus.subscribe(sessionId)
                        .map(event -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("type", "tool_call");
                            data.put("tool", event.toolName() != null ? event.toolName() : "unknown");
                            if (event.data() != null) {
                                data.put("toolInput", toJson(event.data()));
                            }
                            return sse("tool_call", data);
                        })
        );

        // 2. Agent 执行流 — 文本 delta + 工具结果
        Flux<ServerSentEvent<String>> agentEvents = Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                Msg userMsg = new UserMessage(message);
                for (AgentEvent event : agent.streamEvents(userMsg, ctx).toIterable()) {
                    if (sink.isCancelled()) {
                        break;
                    }
                    if (event instanceof TextBlockDeltaEvent e) {
                        String delta = e.getDelta() != null ? e.getDelta() : "";
                        sink.next(sse("token", Map.of("type", "token", "data", delta)));
                    } else if (event instanceof ToolResultEndEvent e) {
                        String toolName = e.getToolCallName() != null ? e.getToolCallName() : "unknown";
                        String state = e.getState() != null ? e.getState().name() : "UNKNOWN";
                        sink.next(sse("tool_result", Map.of(
                                "type", "tool_result",
                                "tool", toolName,
                                "state", state)));
                    }
                }
                if (!sink.isCancelled()) {
                    sink.next(sse("done", Map.of("type", "done")));
                    sink.complete();
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    log.debug("Stream interrupted (client likely disconnected)");
                } else if (isClientCancellation(e)) {
                    log.debug("Client disconnected during streaming: {}", e.getMessage());
                } else {
                    log.error("Streaming error: {}", e.getMessage(), e);
                    sink.error(e);
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 3. 合并两路流 — Agent 完成时 toolEvents 也自动结束
        // IMPORTANT: agentEvents must be shared() to avoid double subscription.
        // Flux.create is cold — each subscription triggers a new agent invocation.
        // Without share(), Flux.merge subscribes twice (once directly, once via
        // takeUntilOther's agentEvents.then()), causing duplicate message processing.
        Flux<ServerSentEvent<String>> sharedAgentEvents = agentEvents.share();
        return Flux.merge(
                toolEvents.takeUntilOther(sharedAgentEvents.then()),
                sharedAgentEvents
        ).doFinally(sig -> activeSessions.remove(sessionId));
    }

    /**
     * Synchronous chat endpoint.
     *
     * @param message   user message
     * @param sessionId session identifier (defaults to "default")
     * @return agent's full reply text
     */
    @GetMapping("/send")
    public Mono<String> sendChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        // Deduplication guard: reject if session already has an in-flight request
        if (!activeSessions.add(sessionId)) {
            log.debug("Rejecting duplicate send for session={}", sessionId);
            return Mono.just("");
        }
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .build();
        Msg userMsg = new UserMessage(message);
        return agent.call(userMsg, ctx)
                .map(reply -> reply.getTextContent() != null ? reply.getTextContent() : "")
                .doFinally(sig -> activeSessions.remove(sessionId));
    }

    /**
     * Delete a session and its persisted state.
     *
     * @param sessionId session identifier to delete
     * @return result message
     */
    @DeleteMapping("/session")
    public Mono<Map<String, String>> deleteSession(
            @RequestParam String sessionId) {
        stateStore.delete(null, sessionId);
        activeSessions.remove(sessionId);
        log.info("Session deleted: {}", sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Session '" + sessionId + "' deleted"));
    }

    /**
     * Delete workspace memory files (MEMORY.md) for a given session.
     *
     * @param sessionId session identifier (defaults to "default")
     * @return result message
     */
    @DeleteMapping("/memory")
    public Mono<Map<String, String>> deleteMemory(
            @RequestParam(defaultValue = "default") String sessionId) {
        Path memoryFile = workspacePath.resolve(sessionId).resolve("MEMORY.md");
        boolean deleted = false;
        try {
            deleted = Files.deleteIfExists(memoryFile);
        } catch (IOException e) {
            log.warn("Failed to delete memory file: {}", memoryFile, e);
        }
        if (deleted) {
            log.info("Memory cleared for session: {}", sessionId);
            return Mono.just(Map.of("status", "ok", "message", "Memory cleared for session '" + sessionId + "'"));
        } else {
            return Mono.just(Map.of("status", "ok", "message", "No memory file found for session '" + sessionId + "'"));
        }
    }

    private static ServerSentEvent<String> sse(String eventType, Object data) {
        return ServerSentEvent.<String>builder()
                .event(eventType)
                .data(toJson(data))
                .build();
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Check whether the exception was caused by the client disconnecting
     * (e.g. browser tab closed, network drop).
     */
    private static boolean isClientCancellation(Throwable e) {
        while (e != null) {
            if (e instanceof InterruptedException) {
                return true;
            }
            String name = e.getClass().getName();
            if (name.contains("CancelException") || name.contains("CancellationException")) {
                return true;
            }
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Broken pipe")
                    || msg.contains("Connection reset")
                    || msg.contains("Connection prematurely closed")
                    || msg.contains("Cancelled")
                    || msg.contains("cancelled"))) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
