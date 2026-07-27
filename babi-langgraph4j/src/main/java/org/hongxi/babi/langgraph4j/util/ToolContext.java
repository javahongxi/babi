package org.hongxi.babi.langgraph4j.util;

/**
 * ThreadLocal-based context for tool event emission.
 *
 * <p>Since LangGraph4J tools are plain objects with {@code @Tool} methods
 * (no framework-level middleware), we use a ThreadLocal to carry the
 * session ID from the controller thread to tool invocations.
 */
public final class ToolContext {

    private ToolContext() {}

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> WORKSPACE_PATH = new ThreadLocal<>();

    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static String getSessionId() {
        return SESSION_ID.get();
    }

    public static void setWorkspacePath(String path) {
        WORKSPACE_PATH.set(path);
    }

    public static String getWorkspacePath() {
        return WORKSPACE_PATH.get();
    }

    public static void clear() {
        SESSION_ID.remove();
        WORKSPACE_PATH.remove();
    }
}
