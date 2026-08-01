package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.HttpRequestLogic;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Tool for making arbitrary HTTP requests.
 *
 * <p>Delegates to {@link HttpRequestLogic} for the actual HTTP request processing.
 */
public class HttpRequestTool extends AbstractNotifyingTool {

    private final HttpClient client;

    public HttpRequestTool(ToolEventBus eventBus) {
        super(eventBus);
        this.client = HttpRequestLogic.createHttpClient();
    }

    @Tool(name = "http_request", value = "Make an HTTP request (GET, POST, PUT, DELETE, PATCH) to any URL. Use for API calls with custom methods, headers, or request bodies. NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String httpRequest(
            @P("HTTP method: GET, POST, PUT, DELETE, PATCH") String method,
            @P("Full URL to request") String url,
            @P("Optional request headers as JSON object") Map<String, String> headers,
            @P("Optional request body (string)") String body) {

        emitEvent("http_request", Map.of("method", method, "url", url));
        return HttpRequestLogic.httpRequest(client, method, url, headers, body);
    }
}
