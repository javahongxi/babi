package org.hongxi.babi.langgraph4j.service;

import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.hongxi.babi.langgraph4j.util.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service wrapping the LangGraph4J CompiledGraph for chat interactions.
 *
 * <p>Uses MemorySaver checkpoint with threadId=sessionId for conversation history.
 */
@Service
public class BabiService {

    private static final Logger log = LoggerFactory.getLogger(BabiService.class);

    private final CompiledGraph<?> graph;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public BabiService(CompiledGraph<?> graph) {
        this.graph = graph;
    }

    /**
     * Stream a chat message and return SSE events as a Flux.
     *
     * @param message   user message
     * @param sessionId session identifier
     * @return Flux of SSE event data maps
     */
    @SuppressWarnings("unchecked")
    public Flux<Map<String, Object>> streamChat(String message, String sessionId) {
        if (!activeSessions.add(sessionId)) {
            return Flux.just(Map.of("type", "done", "duplicate", true));
        }

        Sinks.Many<Map<String, Object>> sink = Sinks.many().unicast().onBackpressureBuffer();

        new Thread(() -> {
            try {
                // Set ThreadLocal for tool event emission
                ToolContext.setSessionId(sessionId);

                // Clear stale FINAL_RESPONSE from previous turn (LangGraph4J bug:
                // executeTool routes based on state.finalResponse(), which persists
                // across turns via MemorySaver checkpoint, causing premature END)
                var input = new HashMap<String, Object>();
                input.put("messages", UserMessage.from(message));
                input.put("agent_response", null); // triggers removal from checkpoint state
                var config = RunnableConfig.builder()
                        .threadId(sessionId)
                        .build();

                var result = graph.stream(input, config);

                // Consume the AsyncGenerator via .stream() -> Java Stream
                result.stream()
                        .filter(output -> output instanceof StreamingOutput<?>)
                        .map(output -> (StreamingOutput<?>) output)
                        .forEach(so -> {
                            String chunk = so.chunk();
                            if (chunk != null && !chunk.isEmpty()) {
                                sink.tryEmitNext(Map.of("type", "token", "data", chunk));
                            }
                        });

                if (!sink.tryEmitNext(Map.of("type", "done")).isSuccess()) {
                    log.warn("Failed to emit done event for session={}", sessionId);
                }
                sink.tryEmitComplete();

            } catch (Exception e) {
                log.error("Agent error for session={}: {}", sessionId, e.getMessage(), e);
                sink.tryEmitNext(Map.of("type", "error", "data", e.getMessage() != null ? e.getMessage() : "Unknown error"));
                sink.tryEmitNext(Map.of("type", "done"));
                sink.tryEmitComplete();
            } finally {
                activeSessions.remove(sessionId);
                ToolContext.clear();
            }
        }, "babi-agent-" + sessionId).start();

        return sink.asFlux();
    }

    public boolean isActive(String sessionId) {
        return activeSessions.contains(sessionId);
    }
}
