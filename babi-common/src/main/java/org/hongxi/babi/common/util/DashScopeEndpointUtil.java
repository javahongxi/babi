package org.hongxi.babi.common.util;

import java.util.Set;

/**
 * DashScope model endpoint routing utility.
 *
 * <p>Determines whether a model requires the multimodal-generation API endpoint
 * based on its name. This is the single source of truth shared by all babi modules
 * (agent, graph, spring).
 *
 * <p>When a new multimodal model is released by DashScope, add its prefix to
 * {@link #MULTIMODAL_MODEL_PREFIXES}.
 */
public final class DashScopeEndpointUtil {

    private DashScopeEndpointUtil() {}

    /**
     * Model name prefixes that require the multimodal API endpoint.
     *
     * <p>Models matching any of these prefixes use the multimodal-generation endpoint;
     * all others use the text-generation endpoint.
     */
    private static final Set<String> MULTIMODAL_MODEL_PREFIXES = Set.of(
            "qwen3.5", "qwen3.6", "qwen3.7", "qwen3.8",
            "qvq", "kimi-k2.5", "kimi-k2.6");

    /**
     * Determine if a model name requires the multimodal API endpoint.
     *
     * @param modelName the model name (e.g. "qwen3.8-max", "deepseek-v4-flash")
     * @return true if the model requires the multimodal-generation endpoint
     */
    public static boolean isMultimodalModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        String lower = modelName.toLowerCase();
        for (String prefix : MULTIMODAL_MODEL_PREFIXES) {
            if (lower.contains(prefix)) {
                return true;
            }
        }
        return false;
    }
}
