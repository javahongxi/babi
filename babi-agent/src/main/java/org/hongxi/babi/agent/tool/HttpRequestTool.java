package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.HttpRequestLogic;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Tool for making arbitrary HTTP requests.
 *
 * <p>Supports GET, POST, PUT, DELETE, PATCH methods with custom headers and body.
 * Useful for API calls and web service interactions.
 *
 * <p>Delegates to {@link HttpRequestLogic} for the actual HTTP request processing.
 */
public class HttpRequestTool {

    private final HttpClient client;

    public HttpRequestTool() {
        this.client = HttpRequestLogic.createHttpClient();
    }

    @Tool(
            name = "http_request",
            description =
                    "Make an HTTP request (GET, POST, PUT, DELETE, PATCH) to any URL."
                            + " Use for API calls with custom methods, headers, or request bodies."
                            + " NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String httpRequest(
            @ToolParam(name = "method", description = "HTTP method: GET, POST, PUT, DELETE, PATCH")
                    String method,
            @ToolParam(name = "url", description = "Full URL to request") String url,
            @ToolParam(name = "headers", description = "Optional request headers as JSON object", required = false)
                    Map<String, String> headers,
            @ToolParam(name = "body", description = "Optional request body (string)", required = false)
                    String body) {
        return HttpRequestLogic.httpRequest(client, method, url, headers, body);
    }
}
