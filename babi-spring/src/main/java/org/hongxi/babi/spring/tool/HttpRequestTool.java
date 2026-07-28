package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.HttpRequestLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Tool for making arbitrary HTTP requests via Spring AI @Tool annotation.
 * Delegates to {@link HttpRequestLogic} for actual HTTP request processing.
 */
public class HttpRequestTool {

    private final HttpClient client;

    public HttpRequestTool() {
        this.client = HttpRequestLogic.createHttpClient();
    }

    @Tool(name = "http_request", description =
            "Make an HTTP request (GET, POST, PUT, DELETE, PATCH) to any URL."
                    + " Use for API calls with custom methods, headers, or request bodies."
                    + " NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String httpRequest(
            @ToolParam(description = "HTTP method: GET, POST, PUT, DELETE, PATCH") String method,
            @ToolParam(description = "Full URL to request") String url,
            @ToolParam(description = "Optional request headers as JSON object", required = false) Map<String, String> headers,
            @ToolParam(description = "Optional request body (string)", required = false) String body) {
        return HttpRequestLogic.httpRequest(client, method, url, headers, body);
    }
}
