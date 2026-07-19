package org.hongxi.babi.agent;

/**
 * Shared constants for BabiAgent.
 */
public final class AgentConstants {

    private AgentConstants() {}

    /**
     * System prompt shared by both the Web controller and the CLI.
     * Built by {@link SystemPromptBuilder} from modular sections.
     */
    public static final String SYSTEM_PROMPT = SystemPromptBuilder.build();
}
