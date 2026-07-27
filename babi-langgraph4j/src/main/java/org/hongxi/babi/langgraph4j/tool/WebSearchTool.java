package org.hongxi.babi.langgraph4j.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Tool for searching the web using the Tavily Search API.
 *
 * <p>Provides real-time web search results optimized for AI agents.
 * Requires {@code TAVILY_API_KEY} environment variable (free tier: 1000 calls/month).
 * Get your API key at https://tavily.com.
 *
 * <p>Note: babi-agent does NOT need this tool — its DashScope model has built-in
 * search enabled via {@code enableSearch(true)}. This tool is only for the
 * LangGraph4j module where the model lacks native search capability.
 */
public class WebSearchTool {

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final ToolEventBus eventBus;

    public WebSearchTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Tool("Search the web for real-time information using Tavily AI search. "
            + "Use when you need up-to-date information about current events, news, "
            + "weather, prices, or any topic requiring recent data beyond your training cutoff.")
    public String webSearch(
            @P("Search query. Be specific and concise. For time-sensitive queries, include the year (e.g. '2026年最新电影').") String query,
            @P("Maximum number of results to return (1-10, default 5).") int maxResults,
            @P("Limit results to the last N days (0 = no limit, 7 = recent news, 30 = monthly). Use 0 unless freshness matters.") int days) {

        emitEvent("web_search", Map.of("query", query));

        String apiKey = System.getenv("TAVILY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "Error: TAVILY_API_KEY environment variable not set.\n"
                    + "Get your free API key from: https://tavily.com\n"
                    + "Then set it with: export TAVILY_API_KEY=your_api_key";
        }

        int clamped = Math.max(1, Math.min(maxResults, 10));

        try {
            var bodyNode = MAPPER.createObjectNode();
            bodyNode.put("api_key", apiKey);
            bodyNode.put("query", query);
            bodyNode.put("max_results", clamped);
            if (days > 0) {
                bodyNode.put("days", days);
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

    // -----------------------------------------------------------------
    //  Internal helpers
    // -----------------------------------------------------------------

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

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) {
                eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
            }
        }
    }
}
