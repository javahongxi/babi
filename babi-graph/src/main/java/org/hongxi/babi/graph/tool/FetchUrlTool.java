package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.FetchUrlLogic;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Tool for fetching web page content with smart extraction.
 *
 * <p>Delegates to {@link FetchUrlLogic} for the actual HTTP request and HTML processing.
 */
public class FetchUrlTool extends AbstractNotifyingTool {

    private final HttpClient client;

    public FetchUrlTool(ToolEventBus eventBus) {
        super(eventBus);
        this.client = FetchUrlLogic.createHttpClient();
    }

    @Tool(name = "fetch_url", value = "Fetch a URL and return its content as readable text with structure preserved (headings, code blocks, lists). Works with most web pages including blogs and documentation. For APIs, use http_request instead. NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String fetchUrl(@P("URL to fetch") String url) {
        emitEvent("fetch_url", Map.of("url", url));
        return FetchUrlLogic.fetchUrl(client, url);
    }
}
