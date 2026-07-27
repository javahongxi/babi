package org.hongxi.babi.spring.eventbus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * Event bus for broadcasting tool-call events to the frontend via SSE.
 *
 * <p>Uses Reactor Sinks for publish/subscribe:
 * <ul>
 *   <li>Producer: ToolNotificationAdvisor publishes events before tool execution</li>
 *   <li>Consumer: SSE Controller subscribes and pushes to frontend</li>
 * </ul>
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
