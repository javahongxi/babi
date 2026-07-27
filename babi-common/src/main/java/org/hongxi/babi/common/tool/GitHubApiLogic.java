package org.hongxi.babi.common.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hongxi.babi.common.util.AgentUtils;

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
 * Shared logic for GitHub REST & GraphQL API operations.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both AgentScope and LangGraph4j tool wrappers.
 */
public final class GitHubApiLogic {

    private GitHubApiLogic() {}

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";

    /**
     * Executes a GitHub API request.
     *
     * @param client      the HTTP client to use
     * @param token       the GitHub API token
     * @param method      HTTP method (GET, POST, PUT, PATCH, DELETE)
     * @param path        GitHub API path starting with '/'
     * @param body        optional JSON request body
     * @param queryParams optional query parameters
     * @return formatted response string
     */
    public static String githubApiRequest(
            HttpClient client,
            String token,
            String method,
            String path,
            String body,
            Map<String, String> queryParams) {

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
            actualBody = HttpRequestLogic.fixGraphqlBody(body);
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

    /**
     * Queries a GitHub user's pinned repositories via GraphQL.
     *
     * @param client   the HTTP client to use
     * @param token    the GitHub API token
     * @param username GitHub username to query pinned repos for
     * @return formatted Markdown with pinned repos, or error message
     */
    public static String githubPinnedRepos(HttpClient client, String token, String username) {
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
            ObjectMapper mapper = new ObjectMapper();
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
     * Creates a standard HTTP client configured for GitHub API requests.
     */
    public static HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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

    /**
     * Tries to format a GraphQL response as pinned repos Markdown.
     * Returns null if the response is not a pinned repos query.
     */
    @SuppressWarnings("unchecked")
    private static String tryFormatPinnedRepos(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
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

    /**
     * Parses pinned repos GraphQL JSON response and returns Markdown.
     * Returns null if parsing fails.
     */
    @SuppressWarnings("unchecked")
    private static String formatPinnedReposMarkdown(String json, String username) {
        try {
            ObjectMapper mapper = new ObjectMapper();
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

    /**
     * Shared formatter: takes repo nodes and optional username, returns Markdown.
     */
    private static String doFormatPinnedRepos(List<Map<String, Object>> nodes, String username) {
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
}
