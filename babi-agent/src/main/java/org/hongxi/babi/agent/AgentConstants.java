package org.hongxi.babi.agent;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;

import java.nio.file.Path;

/**
 * Shared constants and utilities for BabiAgent.
 */
public final class AgentConstants {

    private AgentConstants() {}

    public static final String AGENT_NAME = "BabiAgent";

    public static final String MODEL = "dashscope:qwen-plus";

    /** Default workspace directory when none is configured. */
    public static final String DEFAULT_WORKSPACE =
            Path.of(System.getProperty("user.home"), "babi-workspace").toString();

    /**
     * Builds the system prompt for the given workspace.
     */
    public static String systemPrompt(String workspace) {
        return SystemPromptBuilder.build(workspace);
    }

    /**
     * Resolves a workspace path string, expanding {@code ~} to user.home
     * and converting relative paths to absolute.
     *
     * @param raw the raw workspace string (may be {@code ~}, relative, or absolute)
     * @return resolved absolute path string
     */
    public static String resolveWorkspace(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_WORKSPACE;
        }
        String expanded = raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize().toString();
    }

    /**
     * Creates the DashScope chat model with built-in web search enabled.
     *
     * <p>Uses the existing {@code DASHSCOPE_API_KEY} environment variable,
     * so no additional API key (e.g. Tavily) is needed for web search.
     *
     * @return configured model instance
     */
    public static Model createModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "DASHSCOPE_API_KEY environment variable is not set.");
        }
        // Extract model name from "dashscope:qwen-plus" -> "qwen-plus"
        String modelName = MODEL.contains(":")
                ? MODEL.substring(MODEL.indexOf(':') + 1)
                : MODEL;
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableSearch(true)
                .build();
    }
}
