package org.hongxi.babi.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.agent.util.AgentUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Tool for making arbitrary HTTP requests.
 *
 * <p>Supports GET, POST, PUT, DELETE, PATCH methods with custom headers and body.
 * Useful for API calls and web service interactions.
 */
public class HttpRequestTool {

    private final HttpClient client;

    public HttpRequestTool() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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

        // Intercept GitHub web URLs and redirect to github_api_request
        String githubRedirect = GitHubUrlChecker.check(url);
        if (githubRedirect != null) {
            return githubRedirect;
        }

        // Intercept GraphQL POST requests to api.github.com/graphql
        String actualBody = body;
        if (url != null && url.toLowerCase().contains("api.github.com/graphql")
                && "POST".equalsIgnoreCase(method) && body != null) {
            actualBody = fixGraphqlBody(body);
        }

        try {
            HttpRequest.Builder reqBuilder =
                    HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(60));

            if (headers != null) {
                headers.forEach(reqBuilder::header);
            }
            // Add auth header for GitHub API if missing
            if (url != null && url.toLowerCase().contains("api.github.com")
                    && (headers == null || !headers.containsKey("Authorization"))) {
                String token = resolveToken();
                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }
            }

            HttpRequest.BodyPublisher publisher =
                    (actualBody != null && !actualBody.isEmpty())
                            ? HttpRequest.BodyPublishers.ofString(actualBody)
                            : HttpRequest.BodyPublishers.noBody();

            HttpRequest request =
                    reqBuilder
                            .method(method != null ? method.toUpperCase() : "GET", publisher)
                            .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            return "Status: "
                    + response.statusCode()
                    + "\nHeaders: "
                    + response.headers().map()
                    + "\nBody:\n"
                    + AgentUtils.truncate(response.body(), 16000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Resolves the GitHub token from environment variables:
     * GITHUB_TOKEN first, then GH_TOKEN.
     */
    private static String resolveToken() {
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken;
        return System.getenv("GH_TOKEN");
    }

    /**
     * Fixes a GraphQL request body by reformatting the query to multi-line format.
     * GitHub's GraphQL parser requires newlines — single-line queries fail with "Expected NAME".
     *
     * <p>Package-private: also used by {@link GitHubApiTool}.
     */
    static String fixGraphqlBody(String body) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(body, Map.class);
            Object query = parsed.get("query");
            if (query instanceof String q) {
                parsed.put("query", formatGraphqlQuery(q));
                return mapper.writeValueAsString(parsed);
            }
        } catch (Exception e) {
            // Failed to fix GraphQL body — return original
        }
        return body;
    }

    /**
     * Reformats a GraphQL query string to multi-line format.
     * Inserts newlines after '{' and before '}' so GitHub's parser can handle it.
     */
    private static String formatGraphqlQuery(String query) {
        StringBuilder sb = new StringBuilder(query.length() + 64);
        int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '{') {
                depth++;
                sb.append(" {\n");
                sb.append("  ".repeat(depth));
                // skip trailing whitespace
                while (i + 1 < query.length() && query.charAt(i + 1) == ' ') i++;
            } else if (c == '}') {
                depth--;
                // trim trailing whitespace on current line
                while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') sb.deleteCharAt(sb.length() - 1);
                sb.append("\n");
                if (depth >= 0) sb.append("  ".repeat(depth));
                sb.append('}');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}