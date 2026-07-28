package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.GitHubApiLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * GitHub REST & GraphQL API tools via Spring AI @Tool annotation.
 *
 * <p>The GitHub token is resolved from {@code GITHUB_TOKEN} / {@code GH_TOKEN}
 * environment variables automatically. The agent never sees the raw token.
 *
 * <p>Delegates to {@link GitHubApiLogic} for actual API request processing.
 */
public class GitHubApiTool {

    private final HttpClient client;

    public GitHubApiTool() {
        this.client = GitHubApiLogic.createHttpClient();
    }

    @Tool(description =
            "Call the GitHub REST API. Token is injected automatically — never ask the"
                    + " user for a token. Use for issues, PRs, comments, repo search,"
                    + " file content, checks, etc. Path should start with '/' (e.g."
                    + " '/repos/owner/repo/issues').")
    public String github_api_request(
            @ToolParam(description = "HTTP method: GET, POST, PUT, PATCH, DELETE") String method,
            @ToolParam(description = "GitHub API path starting with '/' (e.g. /repos/owner/repo/issues)") String path,
            @ToolParam(description = "Optional JSON request body (for POST/PUT/PATCH)", required = false) String body,
            @ToolParam(description = "Optional query parameters as JSON object", required = false) Map<String, String> query_params) {
        String token = GitHubApiLogic.resolveToken();
        return GitHubApiLogic.githubApiRequest(client, token, method, path, body, query_params);
    }

    @Tool(description =
            "Query a GitHub user's pinned repositories via GraphQL. Token is injected"
                    + " automatically. Returns up to 6 pinned repos with name, description,"
                    + " URL, stars, forks, and primary language.")
    public String github_pinned_repos(
            @ToolParam(description = "GitHub username to query pinned repos for") String username) {
        String token = GitHubApiLogic.resolveToken();
        return GitHubApiLogic.githubPinnedRepos(client, token, username);
    }
}
