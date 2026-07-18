package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tool for web search using the Tavily API.
 *
 * <p>Requires {@code TAVILY_API_KEY} environment variable.
 * Get your API key at: https://tavily.com
 */
public class WebSearchTool {

    private static final String TAVILY_API = "https://api.tavily.com/search";
    private final HttpClient client;

    public WebSearchTool() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Tool(
            name = "web_search",
            description = "Search the web for information. Returns relevant results and snippets.",
            readOnly = true)
    public String webSearch(
            @ToolParam(name = "query", description = "Search query") String query,
            @ToolParam(
                            name = "max_results",
                            description = "Maximum number of results to return (default: 5)")
                    int maxResults) {
        String apiKey = System.getenv("TAVILY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "Error: TAVILY_API_KEY environment variable is not set. Web search is"
                    + " unavailable. Get your key at https://tavily.com";
        }

        int limit = maxResults > 0 ? Math.min(maxResults, 10) : 5;
        String body =
                "{\"query\":\""
                        + escapeJson(query)
                        + "\",\"max_results\":"
                        + limit
                        + ",\"search_depth\":\"basic\"}";

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(TAVILY_API))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "Error: Tavily API returned " + response.statusCode();
            }

            return formatResults(response.body());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Error: " + e.getMessage();
        }
    }

    private String formatResults(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            com.fasterxml.jackson.databind.JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "No results found.";
            }
            StringBuilder sb = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode r : results) {
                sb.append("## ").append(r.path("title").asText("(no title)")).append("\n");
                sb.append("URL: ").append(r.path("url").asText()).append("\n");
                sb.append(r.path("content").asText("")).append("\n\n");
            }
            return sb.toString().strip();
        } catch (Exception e) {
            return json;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
