package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.GitHubApiLogic;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * GitHub REST & GraphQL API tool.
 *
 * <p>The GitHub token is resolved from environment variables:
 * {@code GITHUB_TOKEN} or {@code GH_TOKEN}.
 *
 * <p>Delegates to {@link GitHubApiLogic} for the actual API request processing.
 */
public class GitHubApiTool extends AbstractNotifyingTool {

    private final HttpClient client;

    public GitHubApiTool(ToolEventBus eventBus) {
        super(eventBus);
        this.client = GitHubApiLogic.createHttpClient();
    }

    @Tool(name = "github_api_request", value = "Call the GitHub REST API. Token is injected automatically — never ask the user for a token. Use for issues, PRs, comments, repo search, file content, checks, etc. Path should start with '/' (e.g. '/repos/owner/repo/issues').")
    public String githubApiRequest(
            @P("HTTP method: GET, POST, PUT, PATCH, DELETE") String method,
            @P("GitHub API path starting with '/' (e.g. /repos/owner/repo/issues)") String path,
            @P("Optional JSON request body (for POST/PUT/PATCH)") String body,
            @P("Optional query parameters as JSON object") Map<String, String> queryParams) {

        emitEvent("github_api_request", Map.of("method", method, "path", path));
        String token = GitHubApiLogic.resolveToken();
        return GitHubApiLogic.githubApiRequest(client, token, method, path, body, queryParams);
    }

    @Tool(name = "github_pinned_repos", value = "Query a GitHub user's pinned repositories via GraphQL. Token is injected automatically. Returns up to 6 pinned repos with name, description, URL, stars, forks, and primary language.")
    public String githubPinnedRepos(
            @P("GitHub username to query pinned repos for") String username) {

        emitEvent("github_pinned_repos", Map.of("username", username));
        String token = GitHubApiLogic.resolveToken();
        return GitHubApiLogic.githubPinnedRepos(client, token, username);
    }
}
