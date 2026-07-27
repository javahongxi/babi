package org.hongxi.babi.agent.tool;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.GitHubApiLogic;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * GitHub REST & GraphQL API tool.
 *
 * <p>The GitHub token is resolved from the session context (via {@code RuntimeContext.extra("github_token")})
 * or falls back to {@code GITHUB_TOKEN} / {@code GH_TOKEN} environment variables.
 * The agent never sees the raw token.
 *
 * <p>Delegates to {@link GitHubApiLogic} for the actual API request processing.
 */
public class GitHubApiTool {

    private final HttpClient client;

    public GitHubApiTool() {
        this.client = GitHubApiLogic.createHttpClient();
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
        return GitHubApiLogic.githubApiRequest(client, token, method, path, body, queryParams);
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
        return GitHubApiLogic.githubPinnedRepos(client, token, username);
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
    private static String resolveToken(RuntimeContext ctx) {
        if (ctx != null) {
            Map<String, Object> extra = ctx.getExtra();
            if (extra != null) {
                Object sessionToken = extra.get("github_token");
                if (sessionToken instanceof String t && !t.isBlank()) {
                    return t;
                }
            }
        }
        return GitHubApiLogic.resolveToken();
    }
}
