package org.hongxi.babi.common.util;

/**
 * ThreadLocal holder for the current session ID.
 *
 * <p>Spring AI 2.0 does not expose a tool-call interceptor like AgentScope's Middleware.
 * Tools need the session ID to publish events to ToolEventBus, but they are invoked
 * internally by Spring AI's ToolCallingAdvisor without direct access to the request context.
 *
 * <p>BabiService sets the session ID before calling ChatClient;
 * tools read it via {@link #getSessionId()} when emitting events.
 */
public final class SessionContextHolder {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> MODEL_OVERRIDE = new ThreadLocal<>();

    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    /**
     * Sets the per-request model override (used by the graph module to pass
     * the user-selected model name to DashScopeChatModel via ThreadLocal,
     * since LangGraph4J's AgentExecutor does not support per-request
     * ChatRequestParameters injection from graph state).
     */
    public static void setModelOverride(String model) {
        MODEL_OVERRIDE.set(model);
    }

    public static String getModelOverride() {
        return MODEL_OVERRIDE.get();
    }

    public static void clear() {
        SESSION_ID.remove();
        MODEL_OVERRIDE.remove();
    }

    private SessionContextHolder() {}
}
