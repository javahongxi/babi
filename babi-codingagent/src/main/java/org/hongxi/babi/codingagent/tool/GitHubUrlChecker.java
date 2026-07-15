package org.hongxi.babi.codingagent.tool;

import java.net.URI;

/**
 * Shared utility to detect github.com web URLs and suggest using
 * {@code github_api_request} with the correct REST API path instead.
 *
 * <p>GitHub web pages require authentication and JavaScript rendering,
 * so fetch_url / http_request cannot extract useful content from them.
 * The GitHub REST API (api.github.com) is the correct approach.
 */
public final class GitHubUrlChecker {

    private GitHubUrlChecker() {}

    /**
     * Check if the URL is a github.com web URL (not api.github.com).
     *
     * @return redirect message if GitHub URL, null otherwise
     */
    public static String check(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (!lower.contains("github.com")) return null;
        // Skip if it's already an API URL
        if (lower.contains("api.github.com")) return null;

        // Extract path after github.com/
        String path = "";
        try {
            URI uri = URI.create(url);
            path = uri.getPath();
            if (path == null) path = "";
        } catch (Exception e) {
            // fallback: extract path manually
            int idx = lower.indexOf("github.com");
            if (idx >= 0) {
                path = url.substring(idx + "github.com".length());
                int qIdx = path.indexOf('?');
                if (qIdx >= 0) path = path.substring(0, qIdx);
            }
        }

        // Parse the path segments
        String[] segments = path.split("/");
        // segments[0] is empty (before leading /)
        String user = segments.length > 1 ? segments[1] : "";
        String repo = segments.length > 2 ? segments[2] : "";
        String sub = segments.length > 3 ? segments[3] : "";

        // Build suggestion
        StringBuilder sb = new StringBuilder();
        sb.append("[REDIRECT] github.com URLs cannot be fetched as web pages ");
        sb.append("(GitHub requires authentication and JavaScript rendering).\n");
        sb.append("Use github_api_request instead. Here is the mapping:\n\n");

        if (user.isEmpty()) {
            sb.append("- Profile: method=GET, path=/user\n");
            sb.append("- List your repos: method=GET, path=/user/repos\n");
        } else if (repo.isEmpty()) {
            sb.append("- User profile: method=GET, path=/users/").append(user).append("\n");
            sb.append("- User repos: method=GET, path=/users/").append(user).append("/repos");
            sb.append(", query_params={\"per_page\":\"30\"}\n");
        } else if (sub.isEmpty() || sub.equals("tree") || sub.equals("blob")) {
            sb.append("- Repo details: method=GET, path=/repos/").append(user).append("/").append(repo).append("\n");
            sb.append("- Repo topics: method=GET, path=/repos/").append(user).append("/").append(repo).append("/topics\n");
        } else if (sub.equals("issues")) {
            sb.append("- List issues: method=GET, path=/repos/").append(user).append("/").append(repo).append("/issues\n");
        } else if (sub.equals("pulls")) {
            sb.append("- List PRs: method=GET, path=/repos/").append(user).append("/").append(repo).append("/pulls\n");
        } else if (sub.equals("actions")) {
            sb.append("- List workflows: method=GET, path=/repos/").append(user).append("/").append(repo).append("/actions/workflows\n");
        } else {
            sb.append("- Repo details: method=GET, path=/repos/").append(user).append("/").append(repo).append("\n");
        }

        sb.append("\nToken is auto-injected. Just call github_api_request with the parameters above.");
        return sb.toString();
    }
}
