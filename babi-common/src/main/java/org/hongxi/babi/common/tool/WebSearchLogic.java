package org.hongxi.babi.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared logic for searching the web using the Tavily Search API.
 *
 * <p>Provides real-time web search results optimized for AI agents.
 * Requires {@code TAVILY_API_KEY} environment variable (free tier: 1000 calls/month).
 * Get your API key at https://tavily.com.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both LangGraph4j and Spring AI tool wrappers.
 */
public final class WebSearchLogic {

    private WebSearchLogic() {}

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates an HTTP client configured for web search.
     */
    public static HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Searches the web using the Tavily API.
     *
     * @param client     the HTTP client to use
     * @param query      search query
     * @param maxResults maximum number of results (1–10), may be {@code null} (defaults to 5)
     * @param days       limit to last N days (0 = no limit), may be {@code null} (defaults to 0)
     * @return formatted search results, or an error message
     */
    public static String webSearch(HttpClient client, String query,
                                   Integer maxResults, Integer days) {
        String apiKey = System.getenv("TAVILY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "Error: TAVILY_API_KEY environment variable not set.\n"
                    + "Get your free API key from: https://tavily.com\n"
                    + "Then set it with: export TAVILY_API_KEY=your_api_key";
        }

        int clamped = Math.max(1, Math.min(maxResults != null ? maxResults : 5, 10));
        int effectiveDays = days != null ? days : 0;

        try {
            var bodyNode = MAPPER.createObjectNode();
            bodyNode.put("api_key", apiKey);
            bodyNode.put("query", query);
            bodyNode.put("max_results", clamped);
            if (effectiveDays > 0) {
                bodyNode.put("days", effectiveDays);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_API_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(bodyNode)))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "Tavily API returned status " + response.statusCode()
                        + ": " + truncate(response.body(), 500);
            }

            return parseResults(query, response.body());

        } catch (Exception e) {
            return "Search failed: " + e.getMessage();
        }
    }

    private static String parseResults(String query, String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return "No search results found. Try rephrasing your query.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append("\n");

            int idx = 1;
            for (JsonNode r : results) {
                String title = r.has("title") ? r.get("title").asText() : "No title";
                String content = r.has("content") ? r.get("content").asText() : "";
                String url = r.has("url") ? r.get("url").asText() : "";
                sb.append("[").append(idx++).append("] ").append(title)
                        .append("\n    ").append(content)
                        .append("\n    URL: ").append(url).append("\n");
            }
            return sb.toString().strip();

        } catch (Exception e) {
            return "Failed to parse search results: " + e.getMessage();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
