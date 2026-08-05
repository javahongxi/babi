package org.hongxi.babi.spring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.spring.service.BabiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.Disposable;
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
 * Web API for the Babi Agent (Spring AI 2.0 edition).
 *
 * <p>Provides SSE streaming chat and session management endpoints.
 * Tool-call notifications are delivered via {@link ToolEventBus}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/chat/stream} — SSE streaming (query params: message, sessionId)</li>
 *   <li>{@code POST /api/chat/stream} — SSE streaming (JSON body: {message, sessionId})</li>
 *   <li>{@code GET /api/chat/send} — synchronous reply</li>
 *   <li>{@code DELETE /api/chat/session} — delete a session</li>
 *   <li>{@code DELETE /api/chat/memory} — clear session memory</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class BabiAgentController {

    private static final Logger log = LoggerFactory.getLogger(BabiAgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BabiService babiService;
    private final ToolEventBus toolEventBus;
    private final Path workspacePath;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public BabiAgentController(BabiService babiService, ToolEventBus toolEventBus, Path workspacePath) {
        this.babiService = babiService;
        this.toolEventBus = toolEventBus;
        this.workspacePath = workspacePath;
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Merges two reactive streams:
     * <ol>
     *   <li>toolEvents — tool call notifications from ToolEventBus</li>
     *   <li>agentEvents — text deltas from BabiService.streamChat()</li>
     * </ol>
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChatGet(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        return doStreamChat(message, sessionId);
    }

    /**
     * SSE streaming chat endpoint (POST with JSON body).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChatPost(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "default");
        return doStreamChat(message, sessionId);
    }

    private Flux<ServerSentEvent<String>> doStreamChat(String message, String sessionId) {
        log.debug(">>> streamChat: message='{}', sessionId='{}'", message, sessionId);

        if (!activeSessions.add(sessionId)) {
            log.warn(">>> DUPLICATE request rejected: sessionId='{}'", sessionId);
            return Flux.just(sse("done", Map.of("type", "done", "duplicate", true)));
        }

        // 1. Tool events stream
        Flux<ServerSentEvent<String>> toolEvents = Flux.defer(() ->
                toolEventBus.subscribe(sessionId)
                        .map(event -> {
                            if ("TOOL_RESULT".equals(event.eventType())) {
                                String state = event.state() != null ? event.state().name() : "UNKNOWN";
                                return sse("tool_result", Map.of(
                                        "type", "tool_result",
                                        "tool", event.toolName() != null ? event.toolName() : "unknown",
                                        "state", state));
                            } else {
                                Map<String, Object> data = new LinkedHashMap<>();
                                data.put("type", "tool_call");
                                data.put("tool", event.toolName() != null ? event.toolName() : "unknown");
                                if (event.data() != null) {
                                    data.put("toolInput", toJson(event.data()));
                                }
                                return sse("tool_call", data);
                            }
                        })
        );

        // 2. Agent text delta stream (Spring AI 2.0 handles ReAct loop internally)
        Flux<ServerSentEvent<String>> agentEvents = Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                Disposable disposable = babiService.streamChat(message, sessionId)
                        .doOnNext(token -> {
                            if (!sink.isCancelled()) {
                                sink.next(sse("token", Map.of("type", "token", "data", token)));
                            }
                        })
                        .doOnComplete(() -> {
                            if (!sink.isCancelled()) {
                                sink.next(sse("done", Map.of("type", "done")));
                                sink.complete();
                            }
                        })
                        .doOnError(e -> {
                            if (!sink.isCancelled()) {
                                log.error("Streaming error: {}", e.getMessage(), e);
                                sink.error(e);
                            }
                        })
                        .subscribe();
                // Register subscription for interrupt support
                babiService.registerSubscription(sessionId, disposable);
                // Clean up subscription when sink is cancelled
                sink.onDispose(() -> {
                    if (!disposable.isDisposed()) {
                        disposable.dispose();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to start streaming: {}", e.getMessage(), e);
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());

        // 3. Merge tool events + agent events
        Flux<ServerSentEvent<String>> sharedAgentEvents = agentEvents.share();
        return Flux.merge(
                toolEvents.takeUntilOther(sharedAgentEvents.then()),
                sharedAgentEvents
        ).doFinally(sig -> activeSessions.remove(sessionId));
    }

    /**
     * Interrupt an in-flight request for a specific session.
     * This disposes the subscription and interrupts the thread to stop LLM token generation.
     */
    @PostMapping("/interrupt")
    public Mono<Map<String, String>> interrupt(
            @RequestParam(defaultValue = "default") String sessionId) {
        log.info("Interrupting session: {}", sessionId);
        babiService.interrupt(sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Session '" + sessionId + "' interrupted"));
    }

    /**
     * Synchronous chat endpoint.
     */
    @GetMapping("/send")
    public Mono<String> sendChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        if (!activeSessions.add(sessionId)) {
            return Mono.just("");
        }
        return Mono.fromCallable(() -> babiService.chat(message, sessionId))
                .doFinally(sig -> activeSessions.remove(sessionId));
    }

    /**
     * Delete a session.
     */
    @DeleteMapping("/session")
    public Mono<Map<String, String>> deleteSession(@RequestParam String sessionId) {
        babiService.clearMemory(sessionId);
        activeSessions.remove(sessionId);
        return Mono.just(Map.of("status", "ok", "message", "Session '" + sessionId + "' deleted"));
    }

    /**
     * Clear session memory (delete MEMORY.md file).
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
}
