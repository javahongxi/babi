package org.hongxi.babi.langgraph4j.eventbus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * Event bus for tool call notifications (Reactor Sinks based pub/sub).
 *
 * <p>Used to decouple tool call event production (from tool methods)
 * and consumption (SSE controller pushing to frontend).
 */
public class ToolEventBus {

    private final Sinks.Many<ToolEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    public void publish(ToolEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<ToolEvent> subscribe(String sessionId) {
        return sink.asFlux().filter(e -> sessionId.equals(e.sessionId()));
    }

    public record ToolEvent(
            String sessionId,
            String eventType,
            String toolName,
            Map<String, Object> data) {

        public static ToolEvent toolCall(String sessionId, String toolName, Map<String, Object> input) {
            return new ToolEvent(sessionId, "TOOL_CALL", toolName, input);
        }
    }
}
