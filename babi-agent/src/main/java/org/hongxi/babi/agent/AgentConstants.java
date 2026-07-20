package org.hongxi.babi.agent;

import java.nio.file.Path;

/**
 * Shared constants and utilities for BabiAgent.
 */
public final class AgentConstants {

    private AgentConstants() {}

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
}
