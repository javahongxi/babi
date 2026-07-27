package org.hongxi.babi.langgraph4j.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared constants and utilities for BabiAgent.
 */
public final class AgentUtils {

    private static final Logger log = LoggerFactory.getLogger(AgentUtils.class);

    private AgentUtils() {}

    public static final String AGENT_NAME = "BabiAgent";

    /**
     * Resolves a workspace path string, expanding {@code ~} to user.home
     * and converting relative paths to absolute.
     */
    public static String resolveWorkspace(String raw) {
        String expanded = raw.startsWith("~")
                ? System.getProperty("user.home") + raw.substring(1)
                : raw;
        return Path.of(expanded).toAbsolutePath().normalize().toString();
    }

    /**
     * Truncates a string to the given maximum length.
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "\n... (truncated)" : s;
    }

    /**
     * Initialize AGENTS.md in the workspace directory if it doesn't exist.
     */
    public static void initAgentsMd(Path workspacePath) {
        Path agentsMd = workspacePath.resolve("AGENTS.md");
        if (Files.exists(agentsMd)) {
            return;
        }
        try {
            try (InputStream is = AgentUtils.class.getResourceAsStream("/workspace/AGENTS.md")) {
                if (is != null) {
                    Files.copy(is, agentsMd, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Initialized AGENTS.md from classpath resource");
                    return;
                }
            }
            String defaultContent = """
                    # BabiAgent
                    
                    You are BabiAgent, an expert coding assistant powered by LangGraph4J.
                    
                    ## Rules
                    
                    - When the user provides a URL, ALWAYS call fetch_url FIRST
                    - For GitHub URLs, use github_api_request (NOT fetch_url)
                    - NEVER fabricate content from resources you have not accessed
                    - Be cautious with destructive commands
                    - IMAGE OUTPUT: Wrap image URLs in Markdown syntax for inline rendering
                    """;
            Files.writeString(agentsMd, defaultContent);
            log.info("Created default AGENTS.md in workspace");
        } catch (IOException e) {
            log.warn("Failed to initialize AGENTS.md: {}", e.getMessage());
        }
    }
}
