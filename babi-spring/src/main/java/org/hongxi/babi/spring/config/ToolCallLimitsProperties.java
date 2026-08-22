package org.hongxi.babi.spring.config;

import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for tool call limits, mirroring Spring AI's
 * {@code spring.ai.tools.limits.*} which is not applicable here because
 * babi-spring registers a custom {@code ToolCallingManager} bean
 * (the auto-configuration backs off on {@code @ConditionalOnMissingBean}).
 *
 * <p>Defaults follow Spring AI: a single tool may be called at most 40 times
 * and all tools at most 150 times in total within a turn (session-scoped,
 * counted across the conversation history). Set a limit to {@code -1} to
 * disable it entirely.
 *
 * @param maxCallsPerTool   maximum number of times any single tool can be called within a turn
 * @param maxTotalToolCalls maximum number of tool calls, across all tools combined, within a turn
 * @param onLimitExceeded   what to do when a limit is exceeded: {@code THROW} aborts with an
 *                          exception; {@code RETURN_ERROR_RESPONSE} skips the call and lets the
 *                          model see the rejection
 */
@ConfigurationProperties(ToolCallLimitsProperties.PREFIX)
public record ToolCallLimitsProperties(
        int maxCallsPerTool,
        int maxTotalToolCalls,
        ToolCallLimitBehavior onLimitExceeded
) {

    static final String PREFIX = "babi.agent.tool-limits";

    /** Mirrors {@code DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL}. */
    public static final int DEFAULT_MAX_CALLS_PER_TOOL = 40;
    /** Mirrors {@code DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS}. */
    public static final int DEFAULT_MAX_TOTAL_TOOL_CALLS = 150;
    /** Value meaning "no limit". */
    public static final int UNLIMITED = -1;

    public ToolCallLimitsProperties {
        if (maxCallsPerTool == 0) {
            maxCallsPerTool = DEFAULT_MAX_CALLS_PER_TOOL;
        }
        if (maxTotalToolCalls == 0) {
            maxTotalToolCalls = DEFAULT_MAX_TOTAL_TOOL_CALLS;
        }
        if (onLimitExceeded == null) {
            onLimitExceeded = ToolCallLimitBehavior.THROW;
        }
    }
}
