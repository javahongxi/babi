package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.WebSearchLogic;

import java.net.http.HttpClient;

/**
 * Tool for searching the web using the Tavily Search API.
 *
 * <p>Delegates to {@link WebSearchLogic} for the actual search logic.
 *
 * <p>Note: babi-agent does NOT need this tool — its DashScope model has built-in
 * search enabled via {@code enableSearch(true)}. This tool is only for the
 * LangGraph4j module where the model lacks native search capability.
 */
public class WebSearchTool {

    private final HttpClient client;

    public WebSearchTool() {
        this.client = WebSearchLogic.createHttpClient();
    }

    @Tool(name = "web_search", value = "Search the web for real-time information using Tavily AI search. "
            + "Use when you need up-to-date information about current events, news, "
            + "weather, prices, or any topic requiring recent data beyond your training cutoff.")
    public String webSearch(
            @P("Search query. Be specific and concise. For time-sensitive queries, include the year (e.g. '2026年最新电影').") String query,
            @P("Maximum number of results to return (1-10, default 5).") int maxResults,
            @P("Limit results to the last N days (0 = no limit, 7 = recent news, 30 = monthly). Use 0 unless freshness matters.") int days) {

        return WebSearchLogic.webSearch(client, query, maxResults, days);
    }
}
