package org.hongxi.babi.spring.util;

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

    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static void clear() {
        SESSION_ID.remove();
    }

    private SessionContextHolder() {}
}
