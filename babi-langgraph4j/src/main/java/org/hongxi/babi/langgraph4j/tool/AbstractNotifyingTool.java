package org.hongxi.babi.langgraph4j.tool;

import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.util.SessionContextHolder;

import java.util.Map;

/**
 * Base class for LangGraph4J tools that publish tool-call events.
 *
 * <p>Since LangGraph4J tools are plain objects with {@code @Tool} methods
 * (no framework-level middleware), each tool calls {@link #emitEvent} to
 * publish events to the {@link ToolEventBus} via the session ID carried
 * in {@link SessionContextHolder}.
 */
public abstract class AbstractNotifyingTool {

    protected final ToolEventBus eventBus;

    protected AbstractNotifyingTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
    }

    protected void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = SessionContextHolder.getSessionId();
            if (sessionId != null) {
                eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
            }
        }
    }
}
