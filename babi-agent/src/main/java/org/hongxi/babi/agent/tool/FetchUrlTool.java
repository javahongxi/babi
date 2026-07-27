package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.FetchUrlLogic;

import java.net.http.HttpClient;

/**
 * Tool for fetching web page content.
 *
 * <p>Fetches a URL and returns a simplified text representation with structure preserved
 * (headings, code blocks, lists). Uses smart content extraction to focus on the main
 * article body, reducing noise from navigation, ads, and sidebars.
 *
 * <p>Delegates to {@link FetchUrlLogic} for the actual HTTP request and HTML processing.
 */
public class FetchUrlTool {

    private final HttpClient client;

    public FetchUrlTool() {
        this.client = FetchUrlLogic.createHttpClient();
    }

    @Tool(
            name = "fetch_url",
            description =
                    "Fetch a URL and return its content as readable text with structure preserved"
                            + " (headings, code blocks, lists). Works with most web pages including"
                            + " blogs and documentation. For APIs, use http_request instead."
                            + " NOTE: Do NOT use this for github.com URLs — use github_api_request instead.",
            readOnly = true)
    public String fetchUrl(
            @ToolParam(name = "url", description = "URL to fetch") String url) {
        return FetchUrlLogic.fetchUrl(client, url);
    }
}
