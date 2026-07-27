package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.FetchUrlLogic;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Tool for fetching web page content with smart extraction.
 *
 * <p>Delegates to {@link FetchUrlLogic} for the actual HTTP request and HTML processing.
 */
public class FetchUrlTool {

    private final HttpClient client;
    private final ToolEventBus eventBus;

    public FetchUrlTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
        this.client = FetchUrlLogic.createHttpClient();
    }

    @Tool("Fetch a URL and return its content as readable text with structure preserved (headings, code blocks, lists). Works with most web pages including blogs and documentation. For APIs, use http_request instead. NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String fetchUrl(@P("URL to fetch") String url) {
        emitEvent("fetch_url", Map.of("url", url));
        return FetchUrlLogic.fetchUrl(client, url);
    }

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
        }
    }
}
