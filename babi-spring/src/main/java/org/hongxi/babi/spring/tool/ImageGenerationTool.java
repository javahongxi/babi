package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.ImageGenerationLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.http.HttpClient;

/**
 * Tool for generating images using the DashScope Image Generation API.
 *
 * <p>Delegates to {@link ImageGenerationLogic} for the actual HTTP call.
 * Supports models like {@code qwen-image-3.0-pro}, {@code wan2.7-image-pro}, etc.
 */
public class ImageGenerationTool {

    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final Boolean promptExtend;

    public ImageGenerationTool(String apiKey, String model, Boolean promptExtend) {
        this.apiKey = apiKey;
        this.model = model;
        this.promptExtend = promptExtend;
        this.client = ImageGenerationLogic.createHttpClient();
    }

    @Tool(name = "generate_image", description = "Generate images from a text description using AI. "
            + "Returns a list of image URLs. Use this when the user asks to create, generate, or draw an image.")
    public String generateImage(
            @ToolParam(description = "Detailed text description of the image to generate. The more detailed, the better.") String prompt) {
        return ImageGenerationLogic.generateImage(client, apiKey, model, promptExtend, prompt);
    }
}
