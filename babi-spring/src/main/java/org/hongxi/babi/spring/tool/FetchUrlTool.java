package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.FetchUrlLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.http.HttpClient;

/**
 * Tool for fetching web page content via Spring AI @Tool annotation.
 * Delegates to {@link FetchUrlLogic} for actual HTTP request and HTML processing.
 */
public class FetchUrlTool {

    private final HttpClient client;

    public FetchUrlTool() {
        this.client = FetchUrlLogic.createHttpClient();
    }

    @Tool(description =
            "Fetch a URL and return its content as readable text with structure preserved"
                    + " (headings, code blocks, lists). Works with most web pages including"
                    + " blogs and documentation. For APIs, use http_request instead."
                    + " NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String fetchUrl(
            @ToolParam(description = "URL to fetch") String url) {
        return FetchUrlLogic.fetchUrl(client, url);
    }
}
