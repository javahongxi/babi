package org.hongxi.babi.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool for generating images using the DashScope Image Generation API.
 *
 * <p>Calls the DashScope REST API directly via {@link HttpClient}.
 * Supports models like {@code qwen-image-3.0-pro}, {@code wan2.7-image-pro}, etc.
 */
public class ImageGenerationTool {

    private static final String DEFAULT_BASE_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final Boolean promptExtend;

    public ImageGenerationTool(String apiKey, String model, Boolean promptExtend) {
        this.apiKey = apiKey;
        this.model = model;
        this.promptExtend = promptExtend;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Tool(name = "generate_image", description = "Generate images from a text description using AI. "
            + "Returns a list of image URLs. Use this when the user asks to create, generate, or draw an image.")
    public String generateImage(
            @ToolParam(name = "prompt", description = "Detailed text description of the image to generate. The more detailed, the better.") String prompt) {
        try {
            String requestBody = buildRequestBody(model, prompt, promptExtend);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_BASE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return "Error generating image: HTTP " + response.statusCode() + " - " + response.body();
            }

            List<String> imageUrls = extractImageUrls(response.body());
            if (imageUrls.isEmpty()) {
                return "No images were generated. Response: " + response.body();
            }

            return formatResult(imageUrls);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Image generation was interrupted.";
        } catch (Exception e) {
            return "Error generating image: " + e.getMessage();
        }
    }

    private static String buildRequestBody(String model, String prompt, Boolean promptExtend) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);

        ObjectNode input = root.putObject("input");
        ArrayNode messages = input.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        ObjectNode textContent = content.addObject();
        textContent.put("text", prompt);

        if (Boolean.TRUE.equals(promptExtend)) {
            ObjectNode parameters = root.putObject("parameters");
            parameters.put("prompt_extend", true);
        }

        return MAPPER.writeValueAsString(root);
    }

    private static List<String> extractImageUrls(String responseBody) throws Exception {
        List<String> urls = new ArrayList<>();
        JsonNode root = MAPPER.readTree(responseBody);

        JsonNode output = root.get("output");
        if (output == null) {
            return urls;
        }

        JsonNode choices = output.get("choices");
        if (choices != null && choices.isArray()) {
            for (JsonNode choice : choices) {
                JsonNode message = choice.get("message");
                if (message != null && message.has("content")) {
                    JsonNode contentArr = message.get("content");
                    if (contentArr.isArray()) {
                        for (JsonNode item : contentArr) {
                            if (item.has("image")) {
                                urls.add(item.get("image").asText());
                            }
                        }
                    }
                }
            }
        }

        return urls;
    }

    private static String formatResult(List<String> imageUrls) {
        StringBuilder sb = new StringBuilder("Generated image(s):\n");
        for (int i = 0; i < imageUrls.size(); i++) {
            sb.append(i + 1).append(". ").append(imageUrls.get(i)).append("\n");
        }
        return sb.toString();
    }
}