package org.hongxi.babi.langgraph4j.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.AgentUtils;
import org.hongxi.babi.langgraph4j.util.ToolContext;

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
 * <p>The GitHub token is resolved from environment variables:
 * {@code GITHUB_TOKEN} or {@code GH_TOKEN}.
 */
public class GitHubApiTool {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolEventBus eventBus;

    public GitHubApiTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool("Call the GitHub REST API. Token is injected automatically — never ask the user for a token. Use for issues, PRs, comments, repo search, file content, checks, etc. Path should start with '/' (e.g. '/repos/owner/repo/issues').")
    public String github_api_request(
            @P("HTTP method: GET, POST, PUT, PATCH, DELETE") String method,
            @P("GitHub API path starting with '/' (e.g. /repos/owner/repo/issues)") String path,
            @P("Optional JSON request body (for POST/PUT/PATCH)") String body,
            @P("Optional query parameters as JSON object") Map<String, String> queryParams) {

        emitEvent("github_api_request", Map.of("method", method, "path", path));

        String token = resolveToken();
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
            queryParams.forEach((k, v) -> {
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "BabiAgent/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .method(method != null ? method.toUpperCase() : "GET", publisher)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Auto-format pinned repos GraphQL response
            if (response.statusCode() == 200 && normalizedPath.equals("/graphql")) {
                String formatted = tryFormatPinnedRepos(responseBody);
                if (formatted != null) return formatted;
            }

            return "Status: " + response.statusCode() + "\nBody:\n" + AgentUtils.truncate(responseBody, 16000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Query a GitHub user's pinned repositories via GraphQL. Token is injected automatically. Returns up to 6 pinned repos with name, description, URL, stars, forks, and primary language.")
    public String github_pinned_repos(
            @P("GitHub username to query pinned repos for") String username) {

        emitEvent("github_pinned_repos", Map.of("username", username));

        String token = resolveToken();
        if (token == null || token.isBlank()) {
            return "Error: No GitHub token available. Set GITHUB_TOKEN or GH_TOKEN environment variable.";
        }
        if (username == null || username.isBlank()) {
            return "Error: username is required.";
        }

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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_GRAPHQL_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "BabiAgent/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(graphql))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() == 200) {
                String formatted = formatPinnedReposMarkdown(responseBody, username);
                if (formatted != null) return formatted;
            }

            return "Status: " + response.statusCode() + "\nBody:\n" + AgentUtils.truncate(responseBody, 16000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Resolves the GitHub token from environment variables:
     * GITHUB_TOKEN first, then GH_TOKEN.
     */
    public static String resolveToken() {
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken;
        return System.getenv("GH_TOKEN");
    }

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
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) pinned.get("nodes");
            if (nodes == null) return null;
            return doFormatPinnedRepos(nodes, null);
        } catch (Exception e) {
            return null;
        }
    }

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
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) pinned.get("nodes");
            if (nodes == null) return null;
            return doFormatPinnedRepos(nodes, username);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
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
            return URLEncoder.encode(Objects.requireNonNullElse(s, ""), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private String fixGraphqlBody(String body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(body, Map.class);
            Object query = parsed.get("query");
            if (query instanceof String q) {
                parsed.put("query", formatGraphqlQuery(q));
                return mapper.writeValueAsString(parsed);
            }
        } catch (Exception e) { /* return original */ }
        return body;
    }

    private static String formatGraphqlQuery(String query) {
        StringBuilder sb = new StringBuilder(query.length() + 64);
        int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '{') {
                depth++;
                sb.append(" {\n").append("  ".repeat(depth));
                while (i + 1 < query.length() && query.charAt(i + 1) == ' ') i++;
            } else if (c == '}') {
                depth--;
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

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
        }
    }
}
