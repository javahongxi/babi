package org.hongxi.babi.agent.tool;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hongxi.babi.agent.util.AgentUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GitHub REST & GraphQL API tool.
 *
 * <p>The GitHub token is resolved from the session context (via {@code RuntimeContext.extra("github_token")})
 * or falls back to {@code GITHUB_TOKEN} / {@code GH_TOKEN} environment variables.
 * The agent never sees the raw token.
 */
public class GitHubApiTool {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubApiTool() {
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Tool(
            description =
                    "Call the GitHub REST API. Token is injected automatically — never ask the"
                            + " user for a token. Use for issues, PRs, comments, repo search,"
                            + " file content, checks, etc. Path should start with '/' (e.g."
                            + " '/repos/owner/repo/issues').")
    public String github_api_request(
            RuntimeContext runtimeContext,
            @ToolParam(name = "method", description = "HTTP method: GET, POST, PUT, PATCH, DELETE")
                    String method,
            @ToolParam(
                            name = "path",
                            description =
                                    "GitHub API path starting with '/' (e.g."
                                            + " /repos/owner/repo/issues)")
                    String path,
            @ToolParam(
                            name = "body",
                            description = "Optional JSON request body (for POST/PUT/PATCH)",
                            required = false)
                    String body,
            @ToolParam(
                            name = "query_params",
                            description = "Optional query parameters as JSON object",
                            required = false)
                    Map<String, String> queryParams) {
        String token = resolveToken(runtimeContext);
        if (token == null || token.isBlank()) {
            return "Error: No GitHub token available. Set GITHUB_TOKEN or GH_TOKEN environment variable.";
        }

        String normalizedPath = path != null ? path : "/";
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        String urlString = GITHUB_API_BASE + normalizedPath;
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder qs = new StringBuilder("?");
            queryParams.forEach(
                    (k, v) -> {
                        if (qs.length() > 1) qs.append("&");
                        qs.append(urlEncode(k)).append("=").append(urlEncode(v));
                    });
            urlString += qs;
        }

        // Intercept GraphQL POST requests — GitHub requires multi-line queries
        String actualBody = body;
        if (normalizedPath.equals("/graphql") && "POST".equalsIgnoreCase(method) && body != null) {
            actualBody = fixGraphqlBody(body);
        }

        try {
            HttpRequest.BodyPublisher publisher =
                    (actualBody != null && !actualBody.isEmpty())
                            ? HttpRequest.BodyPublishers.ofString(actualBody)
                            : HttpRequest.BodyPublishers.noBody();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(urlString))
                            .header("Authorization", "Bearer " + token)
                            .header("Accept", "application/vnd.github.v3+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "BabiAgent/1.0")
                            .timeout(Duration.ofSeconds(30))
                            .method(method != null ? method.toUpperCase() : "GET", publisher)
                            .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();

            // Auto-format pinned repos GraphQL response
            if (response.statusCode() == 200 && normalizedPath.equals("/graphql")) {
                String formatted = tryFormatPinnedRepos(responseBody);
                if (formatted != null) return formatted;
            }

            return "Status: "
                    + response.statusCode()
                    + "\nBody:\n"
                    + AgentUtils.truncate(responseBody, 16000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Error: " + e.getMessage();
        }
    }

    @Tool(
            description =
                    "Query a GitHub user's pinned repositories via GraphQL. Token is injected"
                            + " automatically. Returns up to 6 pinned repos with name, description,"
                            + " URL, stars, forks, and primary language.")
    public String github_pinned_repos(
            RuntimeContext runtimeContext,
            @ToolParam(name = "username", description = "GitHub username to query pinned repos for")
                    String username) {
        String token = resolveToken(runtimeContext);
        if (token == null || token.isBlank()) {
            return "Error: No GitHub token available. Set GITHUB_TOKEN or GH_TOKEN environment variable.";
        }
        if (username == null || username.isBlank()) {
            return "Error: username is required.";
        }

        // Use Jackson ObjectMapper to build JSON — no manual escaping needed
        String query = "query GetPinned($login: String!) {\n"
                + "  user(login: $login) {\n"
                + "    pinnedItems(first: 6, types: REPOSITORY) {\n"
                + "      nodes {\n"
                + "        ... on Repository {\n"
                + "          name description url stargazerCount forkCount\n"
                + "          primaryLanguage { name }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";
        String graphql;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", query);
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("login", username);
            payload.put("variables", vars);
            graphql = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "Error: Failed to build GraphQL request: " + e.getMessage();
        }

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(GITHUB_GRAPHQL_URL))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "BabiAgent/1.0")
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(graphql))
                            .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();

            if (response.statusCode() == 200) {
                String formatted = formatPinnedReposMarkdown(responseBody, username);
                if (formatted != null) return formatted;
            }

            return "Status: "
                    + response.statusCode()
                    + "\nBody:\n"
                    + AgentUtils.truncate(responseBody, 16000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Tries to format a GraphQL response as pinned repos Markdown.
     * Returns null if the response is not a pinned repos query.
     */
    @SuppressWarnings("unchecked")
    private String tryFormatPinnedRepos(String json) {
        try {
            Map<String, Object> root = mapper.readValue(json, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) return null;
            Map<String, Object> user = (Map<String, Object>) data.get("user");
            if (user == null) return null;
            Map<String, Object> pinned = (Map<String, Object>) user.get("pinnedItems");
            if (pinned == null) return null;
            List<Map<String, Object>> nodes =
                    (List<Map<String, Object>>) pinned.get("nodes");
            if (nodes == null) return null;
            return doFormatPinnedRepos(nodes, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves the GitHub token from (in priority order):
     *
     * <ol>
     *   <li>RuntimeContext extra {@code github_token}
     *   <li>{@code GITHUB_TOKEN} environment variable
     *   <li>{@code GH_TOKEN} environment variable
     * </ol>
     */
    public static String resolveToken(RuntimeContext ctx) {
        if (ctx != null) {
            Map<String, Object> extra = ctx.getExtra();
            if (extra != null) {
                Object sessionToken = extra.get("github_token");
                if (sessionToken instanceof String t && !t.isBlank()) {
                    return t;
                }
            }
        }
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken;
        return System.getenv("GH_TOKEN");
    }

    /**
     * Parses pinned repos GraphQL JSON response and returns Markdown.
     * Returns null if parsing fails.
     */
    @SuppressWarnings("unchecked")
    private String formatPinnedReposMarkdown(String json, String username) {
        try {
            Map<String, Object> root = mapper.readValue(json, Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) return null;
            Map<String, Object> user = (Map<String, Object>) data.get("user");
            if (user == null) return null;
            Map<String, Object> pinned = (Map<String, Object>) user.get("pinnedItems");
            if (pinned == null) return null;
            List<Map<String, Object>> nodes =
                    (List<Map<String, Object>>) pinned.get("nodes");
            if (nodes == null) return null;
            return doFormatPinnedRepos(nodes, username);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Shared formatter: takes repo nodes and optional username, returns Markdown.
     */
    private String doFormatPinnedRepos(List<Map<String, Object>> nodes, String username) {
        if (nodes == null || nodes.isEmpty()) {
            return (username != null ? "**@" + username + "** " : "") + "目前没有置顶任何仓库。";
        }
        StringBuilder sb = new StringBuilder();
        if (username != null) {
            sb.append("## 📌 @").append(username).append(" 的置顶仓库 (").append(nodes.size()).append(" 个)\n\n");
        } else {
            sb.append("## 📌 置顶仓库 (").append(nodes.size()).append(" 个)\n\n");
        }
        for (Map<String, Object> repo : nodes) {
            String name = String.valueOf(repo.getOrDefault("name", ""));
            String desc = String.valueOf(repo.getOrDefault("description", ""));
            String url = String.valueOf(repo.getOrDefault("url", ""));
            Object stars = repo.getOrDefault("stargazerCount", 0);
            Object forks = repo.getOrDefault("forkCount", 0);
            Map<String, Object> lang = (Map<String, Object>) repo.get("primaryLanguage");
            String langName = (lang != null) ? String.valueOf(lang.getOrDefault("name", "")) : "";
            if ("null".equals(langName)) langName = "";

            sb.append("### [").append(name).append("](").append(url).append(")\n\n");
            if (!desc.isEmpty() && !"null".equals(desc)) {
                sb.append(desc).append("\n\n");
            }
            sb.append("`Language: ").append(langName.isEmpty() ? "N/A" : langName).append("`")
              .append("  |  ⭐ ").append(stars)
              .append("  |  🔀 ").append(forks).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(
                    Objects.requireNonNullElse(s, ""), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * Fixes a GraphQL request body by reformatting the query to multi-line format.
     * GitHub's GraphQL parser requires newlines — single-line queries fail with "Expected NAME".
     */
    private String fixGraphqlBody(String body) {
        try {
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
