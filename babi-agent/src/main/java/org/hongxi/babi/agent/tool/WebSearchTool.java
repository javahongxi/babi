package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.WebSearchLogic;

import java.net.http.HttpClient;

/**
 * Tool for searching the web using the Tavily Search API.
 *
 * <p>Delegates to {@link WebSearchLogic} for the actual search logic.
 *
 * <p>Provides real-time web search results optimized for AI agents.
 * Requires {@code TAVILY_API_KEY} environment variable (free tier: 1000 calls/month).
 * Get your API key at https://tavily.com.
 */
public class WebSearchTool {

    private final HttpClient client;

    public WebSearchTool() {
        this.client = WebSearchLogic.createHttpClient();
    }

    @Tool(name = "web_search", description = "Search the web for real-time information using Tavily AI search. "
            + "Use when you need up-to-date information about current events, news, "
            + "weather, prices, or any topic requiring recent data beyond your training cutoff.")
    public String webSearch(
            @ToolParam(name = "query", description = "Search query. Be specific and concise. "
                    + "For time-sensitive queries, include the year (e.g. '2026年最新电影').") String query,
            @ToolParam(name = "max_results", description = "Maximum number of results to return (1-10, default 5).", required = false) Integer maxResults,
            @ToolParam(name = "days", description = "Limit results to the last N days (0 = no limit, 7 = recent news, "
                    + "30 = monthly). Use 0 unless freshness matters.", required = false) Integer days) {
        return WebSearchLogic.webSearch(client, query, maxResults, days);
    }
}
