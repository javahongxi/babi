package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.agent.util.AgentUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for fetching web page content.
 *
 * <p>Fetches a URL and returns a simplified text representation with structure preserved
 * (headings, code blocks, lists). Uses smart content extraction to focus on the main
 * article body, reducing noise from navigation, ads, and sidebars.
 */
public class FetchUrlTool {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private static final Pattern MULTI_BLANK = Pattern.compile("[\r\n]{3,}");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \t]{2,}");

    // Content area extraction patterns (ordered by priority)
    private static final Pattern[] CONTENT_AREA_PATTERNS = {
            Pattern.compile(
                    "<article[^>]*>(.*?)</article>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "<div[^>]*class=\"[^\"]*(?:article|blog|post|content|entry|markdown|blog-content|article-content)[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "<main[^>]*>(.*?)</main>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "<div[^>]*id=\"[^\"]*(?:article|content|main|post)[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
    };

    private final HttpClient client;

    public FetchUrlTool() {
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Tool(
            name = "fetch_url",
            description =
                    "Fetch a URL and return its content as readable text with structure preserved"
                            + " (headings, code blocks, lists). Works with most web pages including"
                            + " blogs and documentation. For APIs, use http_request instead."
                            + " NOTE: Do NOT use this for github.com URLs — use github_api_request instead.",
            readOnly = true)
    public String fetchUrl(
            @ToolParam(name = "url", description = "URL to fetch") String url) {
        // Intercept GitHub web URLs and redirect to github_api_request
        String githubRedirect = GitHubUrlChecker.check(url);
        if (githubRedirect != null) {
            return githubRedirect;
        }

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", BROWSER_UA)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return "Error: HTTP " + response.statusCode() + " for " + url;
            }

            String body = response.body();
            String text = extractAndConvert(body);
            if (text.isBlank()) {
                return "Warning: Page returned empty content. The site may require JavaScript rendering or authentication.";
            }
            return AgentUtils.truncate(text, 30000);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Error fetching URL: " + e.getMessage();
        }
    }

    /**
     * Extract main content area from HTML, then convert to readable text.
     */
    private static String extractAndConvert(String html) {
        if (html == null) return "";

        // Remove script, style, nav, footer, header, aside elements
        String cleaned = html;
        for (String tag : new String[]{"script", "style", "nav", "footer", "header", "aside", "iframe", "noscript"}) {
            cleaned = cleaned.replaceAll(
                    "(?is)<" + tag + "[^>]*>.*?</" + tag + ">", "");
        }

        // Try to extract main content area
        String content = null;
        for (Pattern pattern : CONTENT_AREA_PATTERNS) {
            Matcher m = pattern.matcher(cleaned);
            if (m.find()) {
                String candidate = m.group(1);
                // Pick the longest match (most likely the real content)
                if (content == null || candidate.length() > content.length()) {
                    content = candidate;
                }
            }
        }

        // Fallback to full cleaned HTML if no content area found
        if (content == null) {
            content = cleaned;
        }

        return htmlToText(content);
    }

    /**
     * Convert HTML to readable text, preserving structure for headings, code blocks, and lists.
     */
    private static String htmlToText(String html) {
        String text = html;

        // Decode common HTML entities first
        text = decodeEntities(text);

        // Convert block elements to text with newlines
        // Headings -> prefixed with #
        text = text.replaceAll("(?is)<h1[^>]*>(.*?)</h1>", "\n# $1\n");
        text = text.replaceAll("(?is)<h2[^>]*>(.*?)</h2>", "\n## $1\n");
        text = text.replaceAll("(?is)<h3[^>]*>(.*?)</h3>", "\n### $1\n");
        text = text.replaceAll("(?is)<h4[^>]*>(.*?)</h4>", "\n#### $1\n");
        text = text.replaceAll("(?is)<h5[^>]*>(.*?)</h5>", "\n##### $1\n");
        text = text.replaceAll("(?is)<h6[^>]*>(.*?)</h6>", "\n###### $1\n");

        // Code blocks -> preserve content in fenced code block format
        text = text.replaceAll("(?is)<pre[^>]*>\\s*<code[^>]*>(.*?)</code>\\s*</pre>", "\n```\n$1\n```\n");
        text = text.replaceAll("(?is)<pre[^>]*>(.*?)</pre>", "\n```\n$1\n```\n");

        // Inline code
        text = text.replaceAll("(?is)<code[^>]*>(.*?)</code>", "`$1`");

        // Line breaks and paragraphs
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?is)</p>", "\n\n");
        text = text.replaceAll("(?is)<p[^>]*>", "");

        // List items
        text = text.replaceAll("(?is)<li[^>]*>", "\n- ");

        // Horizontal rule
        text = text.replaceAll("(?is)<hr[^>]*/?>", "\n---\n");

        // Links -> text(url)
        text = text.replaceAll("(?is)<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2($1)");

        // Images -> alt text
        text = text.replaceAll("(?is)<img[^>]*alt=\"([^\"]*)\"[^>]*/?>", "[$1]");
        text = text.replaceAll("(?is)<img[^>]*>", "");

        // Strip remaining HTML tags
        text = text.replaceAll("<[^>]+>", " ");

        // Clean up whitespace
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");

        // Clean up lines that are only whitespace
        text = text.replaceAll("(?m)^[ \\t]+$", "");
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");

        return text.strip();
    }

    /**
     * Decode common HTML entities.
     */
    private static String decodeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&hellip;", "…")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&#x2F;", "/");
    }
}
