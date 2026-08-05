package org.hongxi.babi.graph.model;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A decorator around {@link StreamingChatModel} that intercepts
 * {@code onPartialThinking} callbacks and routes thinking content
 * to per-session sinks, without modifying the LangGraph4J framework.
 *
 * <p>LangGraph4J's {@code StreamingChatGenerator} only consumes
 * {@code onPartialResponse} tokens; thinking content is silently
 * discarded by the framework. This decorator captures it at the
 * model layer before it reaches the generator.
 *
 * <p>Usage:
 * <ol>
 *   <li>Register a sink via {@link #registerSink(String, Sinks.Many)} before streaming</li>
 *   <li>Unregister via {@link #unregisterSink(String)} when done</li>
 * </ol>
 */
public class ThinkingCaptureChatModel implements StreamingChatModel {

    /** Per-session sinks for thinking content, set by BabiService before graph execution */
    private static final Map<String, Sinks.Many<String>> THINKING_SINKS = new ConcurrentHashMap<>();

    private final StreamingChatModel delegate;

    public ThinkingCaptureChatModel(StreamingChatModel delegate) {
        this.delegate = delegate;
    }

    /**
     * Register a thinking sink for a session.
     * Must be called before graph.stream() for that session.
     */
    public static void registerSink(String sessionId, Sinks.Many<String> sink) {
        THINKING_SINKS.put(sessionId, sink);
    }

    /**
     * Unregister the thinking sink for a session.
     */
    public static void unregisterSink(String sessionId) {
        THINKING_SINKS.remove(sessionId);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        StreamingChatResponseHandler capturingHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                String text = partialThinking.text();
                if (text != null && !text.isEmpty()) {
                    // Route thinking content to all registered sinks
                    // (typically only one session is active per model call)
                    for (Sinks.Many<String> sink : THINKING_SINKS.values()) {
                        sink.tryEmitNext(text);
                    }
                }
                // Also delegate to original handler (no-op in LangGraph4J, but safe)
                handler.onPartialThinking(partialThinking);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        delegate.chat(request, capturingHandler);
    }
}
