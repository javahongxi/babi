package org.hongxi.babi.agent.util;

import java.nio.file.Path;

/**
 * Shared constants and utilities for BabiAgent.
 */
public final class AgentUtils {

    private AgentUtils() {}

    public static final String AGENT_NAME = "BabiAgent";

    /**
     * Resolves a workspace path string, expanding {@code ~} to user.home
     * and converting relative paths to absolute.
     *
     * @param raw the workspace string (maybe {@code ~} or relative)
     * @return resolved absolute path string
     */
    public static String resolveWorkspace(String raw) {
        String expanded = raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize().toString();
    }

}
