package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.AgentUtils;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for fetching web page content with smart extraction.
 */
public class FetchUrlTool {

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Pattern MULTI_BLANK = Pattern.compile("[\r\n]{3,}");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \t]{2,}");
    private static final Pattern[] CONTENT_AREA_PATTERNS = {
            Pattern.compile("<article[^>]*>(.*?)</article>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            Pattern.compile("<div[^>]*class=\"[^\"]*(?:article|blog|post|content|entry|markdown)[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
            Pattern.compile("<main[^>]*>(.*?)</main>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
    };

    private final HttpClient client;
    private final ToolEventBus eventBus;

    public FetchUrlTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool("Fetch a URL and return its content as readable text with structure preserved (headings, code blocks, lists). Works with most web pages including blogs and documentation. For APIs, use http_request instead. NOTE: Do NOT use this for github.com URLs — use github_api_request instead.")
    public String fetchUrl(@P("URL to fetch") String url) {
        emitEvent("fetch_url", Map.of("url", url));
        String githubRedirect = GitHubUrlChecker.check(url);
        if (githubRedirect != null) return githubRedirect;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return "Error: HTTP " + response.statusCode() + " for " + url;
            }
            String text = extractAndConvert(response.body());
            if (text.isBlank()) {
                return "Warning: Page returned empty content. The site may require JavaScript rendering or authentication.";
            }
            return AgentUtils.truncate(text, 30000);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "Error fetching URL: " + e.getMessage();
        }
    }

    private static String extractAndConvert(String html) {
        if (html == null) return "";
        String cleaned = html;
        for (String tag : new String[]{"script", "style", "nav", "footer", "header", "aside", "iframe", "noscript"}) {
            cleaned = cleaned.replaceAll("(?is)<" + tag + "[^>]*>.*?</" + tag + ">", "");
        }
        String content = null;
        for (Pattern pattern : CONTENT_AREA_PATTERNS) {
            Matcher m = pattern.matcher(cleaned);
            if (m.find()) {
                String candidate = m.group(1);
                if (content == null || candidate.length() > content.length()) content = candidate;
            }
        }
        if (content == null) content = cleaned;
        return htmlToText(content);
    }

    private static String htmlToText(String html) {
        String text = decodeEntities(html);
        text = text.replaceAll("(?is)<h1[^>]*>(.*?)</h1>", "\n# $1\n");
        text = text.replaceAll("(?is)<h2[^>]*>(.*?)</h2>", "\n## $1\n");
        text = text.replaceAll("(?is)<h3[^>]*>(.*?)</h3>", "\n### $1\n");
        text = text.replaceAll("(?is)<h4[^>]*>(.*?)</h4>", "\n#### $1\n");
        text = text.replaceAll("(?is)<h5[^>]*>(.*?)</h5>", "\n##### $1\n");
        text = text.replaceAll("(?is)<h6[^>]*>(.*?)</h6>", "\n###### $1\n");
        text = text.replaceAll("(?is)<pre[^>]*>\\s*<code[^>]*>(.*?)</code>\\s*</pre>", "\n```\n$1\n```\n");
        text = text.replaceAll("(?is)<pre[^>]*>(.*?)</pre>", "\n```\n$1\n```\n");
        text = text.replaceAll("(?is)<code[^>]*>(.*?)</code>", "`$1`");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?is)</p>", "\n\n");
        text = text.replaceAll("(?is)<p[^>]*>", "");
        text = text.replaceAll("(?is)<li[^>]*>", "\n- ");
        text = text.replaceAll("(?is)<hr[^>]*/?>", "\n---\n");
        text = text.replaceAll("(?is)<a[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2($1)");
        text = text.replaceAll("(?is)<img[^>]*alt=\"([^\"]*)\"[^>]*/?>", "[$1]");
        text = text.replaceAll("(?is)<img[^>]*>", "");
        text = text.replaceAll("<[^>]+>", " ");
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = text.replaceAll("(?m)^[ \\t]+$", "");
        text = MULTI_BLANK.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    private static String decodeEntities(String text) {
        return text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&mdash;", "—").replace("&ndash;", "–")
                .replace("&hellip;", "…").replace("&laquo;", "«").replace("&raquo;", "»")
                .replace("&#x2F;", "/");
    }

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
        }
    }
}
