package org.hongxi.babi.graph.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.graph.service.BabiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web API for the Babi Agent (LangGraph4J edition, WebFlux SSE).
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

    private final BabiService babiService;
    private final ToolEventBus toolEventBus;
    private final Path workspacePath;

    public BabiAgentController(BabiService babiService, ToolEventBus toolEventBus, Path workspacePath) {
        this.babiService = babiService;
        this.toolEventBus = toolEventBus;
        this.workspacePath = workspacePath;
    }

    /**
     * SSE streaming chat endpoint.
     *
     * <p>Merges tool call events and agent streaming tokens into one SSE output.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        log.info(">>> streamChat: message='{}', sessionId='{}'", message, sessionId);

        // 1. Tool event stream from ToolEventBus
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

        // 2. Agent streaming events from BabiService
        Flux<ServerSentEvent<String>> agentEvents = babiService.streamChat(message, sessionId)
                .map(data -> {
                    String type = (String) data.getOrDefault("type", "token");
                    return switch (type) {
                        case "token" -> sse("token", Map.of("type", "token", "data", data.getOrDefault("data", "")));
                        case "error" -> sse("error", Map.of("type", "error", "data", data.getOrDefault("data", "")));
                        default -> sse("done", Map.of("type", "done"));
                    };
                })
                .share();

        // 3. Merge: tool events end when agent completes
        Flux<ServerSentEvent<String>> sharedAgentEvents = agentEvents.share();
        return Flux.merge(
                toolEvents.takeUntilOther(sharedAgentEvents.then()),
                sharedAgentEvents
        );
    }

    /**
     * Synchronous chat endpoint.
     */
    @GetMapping("/send")
    public Mono<String> sendChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        // Collect all tokens into a single string
        return babiService.streamChat(message, sessionId)
                .filter(data -> "token".equals(data.get("type")))
                .map(data -> (String) data.getOrDefault("data", ""))
                .reduce(String::concat);
    }

    /**
     * Delete a session.
     */
    @DeleteMapping("/session")
    public Mono<Map<String, String>> deleteSession(@RequestParam String sessionId) {
        babiService.clearMemory(sessionId);
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
