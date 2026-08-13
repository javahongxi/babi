package org.hongxi.babi.common.model;

import java.util.List;

/**
 * Catalog of available DashScope models for page-level model selection.
 *
 * <p>Provides model metadata (name, display name, multimodal flag, description)
 * to the frontend via {@code /api/ui/config}. The frontend renders a dropdown
 * selector; the selected model name is sent with each chat request.
 *
 * <p>This is the single source of truth for the model list shared by all babi
 * modules (agent, graph, spring).
 */
public final class ModelCatalog {

    private ModelCatalog() {}

    private static final List<ModelInfo> MODELS = List.of(
            new ModelInfo("qwen3.7-plus", "Qwen3.7 Plus", true, "多模态，性价比高"),
            new ModelInfo("qwen3.8-max", "Qwen3.8 Max", true, "多模态，旗舰模型"),
            new ModelInfo("qwen3.7-max", "Qwen3.7 Max", false, "擅长代码编写，处理复杂任务"),
            new ModelInfo("qwen3.6-flash", "Qwen3.6 Flash", true, "适用于简单任务，响应速度快"),
            new ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro", false, "推理性能全面领先"),
            new ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", false, "推理性能全面领先"),
            new ModelInfo("glm-5.2", "GLM 5.2", false, "面向长程任务设计")
    );

    public static List<ModelInfo> list() {
        return MODELS;
    }

    public static boolean isMultimodalModel(String name) {
        ModelInfo modelInfo = find(name);
        if (modelInfo == null) {
            throw new IllegalArgumentException("Unknown model: " + name);
        }
        return modelInfo.multimodal;
    }

    public static ModelInfo find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return MODELS.stream()
                .filter(m -> m.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public record ModelInfo(
            String name,
            String displayName,
            boolean multimodal,
            String description
    ) {}
}
