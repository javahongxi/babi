package org.hongxi.babi.graph.service;

import dev.langchain4j.data.message.UserMessage;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.hongxi.babi.common.util.SessionContextHolder;
import org.hongxi.babi.graph.model.DashScopeChatModel;
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
    private final MemorySaver memorySaver;
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    /** Tracks active generators per session for interrupt support */
    private final Map<String, AsyncGenerator.Cancellable<?>> activeGenerators = new ConcurrentHashMap<>();

    public BabiService(CompiledGraph<?> graph, MemorySaver memorySaver) {
        this.graph = graph;
        this.memorySaver = memorySaver;
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

        // Create sinks for capturing LLM content at the model layer
        Sinks.Many<String> thinkingSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<String> textSink = Sinks.many().unicast().onBackpressureBuffer();
        DashScopeChatModel.registerThinkingSink(sessionId, thinkingSink);
        DashScopeChatModel.registerTextSink(sessionId, textSink);

        // Thinking stream: map thinking text to "reasoning" events
        Flux<Map<String, Object>> thinkingEvents = thinkingSink.asFlux()
                .map(text -> Map.<String, Object>of("type", "reasoning", "data", text));

        // Text token stream: map text tokens to "token" events (true streaming)
        Flux<Map<String, Object>> tokenEvents = textSink.asFlux()
                .map(text -> Map.<String, Object>of("type", "token", "data", text));

        new Thread(() -> {
            try {
                // Set ThreadLocal for tool event emission
                SessionContextHolder.setSessionId(sessionId);

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
                // Save generator reference for interrupt support
                activeGenerators.put(sessionId, result);

                // Consume the AsyncGenerator to completion
                // Text tokens are streamed via TEXT_SINKS at the model layer
                result.stream().forEach(output -> {
                    // Just consume to drive the graph execution
                    // Actual text streaming happens via DashScopeChatModel.TEXT_SINKS
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
                activeGenerators.remove(sessionId);
                DashScopeChatModel.unregisterSinks(sessionId);
                thinkingSink.tryEmitComplete();
                textSink.tryEmitComplete();
                SessionContextHolder.clear();
            }
        }, "babi-agent-" + sessionId).start();

        // Merge token stream, thinking stream, and completion signal
        return Flux.merge(tokenEvents, thinkingEvents, sink.asFlux());
    }

    /**
     * Interrupt an in-flight request for a specific session.
     * This cancels the AsyncGenerator to stop LLM token generation.
     *
     * @param sessionId session identifier to interrupt
     */
    public void interrupt(String sessionId) {
        AsyncGenerator.Cancellable<?> generator = activeGenerators.remove(sessionId);
        if (generator != null) {
            log.info("Interrupting session: {}", sessionId);
            generator.cancel(true);
        } else {
            log.debug("No active generator found for session: {}", sessionId);
        }
    }

    /**
     * Clear checkpoint memory for a specific session.
     *
     * @param sessionId session identifier
     */
    public void clearMemory(String sessionId) {
        try {
            var config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();
            memorySaver.release(config);
            log.info("Memory cleared for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to clear memory for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
