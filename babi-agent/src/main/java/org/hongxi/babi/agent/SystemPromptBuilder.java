package org.hongxi.babi.agent;

import org.hongxi.babi.agent.skill.SkillLoader.Skill;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

/**
 * System prompt builder for BabiAgent.
 *
 * <p>With HarnessAgent, workspace context (AGENTS.md, MEMORY.md, KNOWLEDGE.md)
 * is automatically injected by the framework. This builder only provides
 * babi-specific rules and custom tool guidance that go beyond the workspace context.
 *
 * <p>To customize the prompt without recompiling, place a file at
 * {@code prompts/custom-instructions.md} on the classpath (e.g. under
 * {@code src/main/resources/prompts/}). Its content will be appended to the
 * built-in prompt.
 */
public final class SystemPromptBuilder {

    private SystemPromptBuilder() {}

    /**
     * Builds the system prompt with loaded skills.
     *
     * @param skills the collection of loaded skills (maybe empty)
     */
    public static String build(Collection<Skill> skills) {
        String custom = loadCustomInstructions();
        return String.join("\n\n",
                coreRulesSection(),
                githubSection(),
                skillsSection(skills),
                guidelinesSection(),
                custom
        ).strip();
    }

    /**
     * Builds the system prompt with no skills.
     */
    public static String build() {
        return build(Collections.emptyList());
    }

    // -----------------------------------------------------------------
    //  Sections
    // -----------------------------------------------------------------

    private static String coreRulesSection() {
        return """
                CRITICAL RULES (you MUST follow these):

                1. TOOL-FIRST RULE: When the user provides a URL (any URL), you MUST call fetch_url
                   (or http_request as fallback) FIRST before responding. NEVER assume a tool is
                   unavailable — always TRY calling it. Only report failure AFTER the tool returns
                   an error.
                   EXCEPTION: If the URL is a github.com URL, do NOT use fetch_url — instead use
                   github_api_request (see Rule 5). GitHub web pages require authentication and
                   JavaScript rendering; the REST API is the correct approach.

                2. NO HALLUCINATION: NEVER fabricate, guess, or infer content from a URL, file, or
                   any resource you have not actually accessed via a tool call. If a tool returns
                   an error or empty content, report that fact honestly to the user.

                3. NO SELF-DENIAL: NEVER claim that a registered tool is "unavailable", "disabled",
                   or "cannot be used" unless you have actually tried calling it and received an
                   error. All registered tools are available — try them before judging.

                4. HONEST REPORTING: If fetch_url returns empty, incomplete, or garbled content
                   (e.g., from JavaScript-rendered pages like CSDN), tell the user exactly what
                   the tool returned. Do NOT fill in the gaps with your own assumptions.
                """;
    }

    private static String githubSection() {
        return """
                5. GITHUB API = YOUR PRIMARY TOOL FOR GITHUB: When the user asks ANYTHING related
                   to GitHub (repos, issues, PRs, profile, search, stars, orgs, etc.), you MUST
                   call github_api_request IMMEDIATELY. Do NOT explain limitations first — just
                   call the tool.

                   The github_api_request tool calls api.github.com (NOT github.com web pages).
                   It sends authenticated HTTP requests with a Bearer token and returns JSON.
                   It is completely different from web-scraping and works reliably.

                   URL-to-API mapping (when user gives a github.com URL, convert it):
                   - https://github.com/{user}              → method=GET, path=/users/{user}
                   - https://github.com/{user}?tab=repositories → method=GET, path=/users/{user}/repos
                   - https://github.com/{user}/{repo}        → method=GET, path=/repos/{user}/{repo}
                   - https://github.com/{user}/{repo}/issues → method=GET, path=/repos/{user}/{repo}/issues
                   - https://github.com/{user}/{repo}/pulls  → method=GET, path=/repos/{user}/{repo}/pulls

                   Other common endpoints:
                   - "list my repos"       → method=GET, path=/user/repos, query_params={"per_page":"30"}
                   - "my GitHub profile"   → method=GET, path=/user
                   - "search repos"        → method=GET, path=/search/repositories, query_params={"q":"keyword"}
                   - "star a repo"         → method=PUT, path=/user/starred/{owner}/{repo}

                   For PINNED REPOS, use github_pinned_repos tool directly:
                   - "my pinned repos"     → call github_pinned_repos with username
                   - "user X's pinned repos" → call github_pinned_repos with username=X

                   NEVER use fetch_url or http_request for github.com URLs.
                   NEVER say "I cannot access your GitHub repos" — CALL THE TOOL FIRST.
                   If the token is missing, the tool will return a clear error message —
                   let the tool tell you that, do not preemptively deny the capability.
                """;
    }

    private static String skillsSection(Collection<Skill> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                SKILLS SYSTEM:
                Skills are reusable workflow instructions stored as Markdown files.
                They are loaded from two directories:
                - ~/.agents/skills/ — global shared skills (cross-project reuse)
                - ~/.babi/skills/   — Babi-specific skills (higher priority, overrides global)

                IMPORTANT: When the user's request matches ANY of the skills below, you MUST
                call use_skill(skill_name) FIRST to load the full instructions, then follow them.
                Do NOT wait for the user to ask you to "use a skill" — match the intent yourself.
                """);

        if (skills != null && !skills.isEmpty()) {
            sb.append("\nCurrently loaded skills (").append(skills.size()).append("):\n");
            for (Skill skill : skills) {
                sb.append("- ").append(skill.name()).append(": ").append(skill.description()).append("\n");
            }
            sb.append("\nCall use_skill(skill_name) to get full instructions before executing.");
        } else {
            sb.append("\nNo skills currently installed. Use list_skills to check, or add .md files to ~/.agents/skills/.");
        }
        return sb.toString();
    }

    private static String guidelinesSection() {
        return """
                General guidelines:
                - Always explain what you're doing before executing commands
                - Be cautious with destructive commands (rm, etc.)
                - When reading code, provide clear analysis and suggestions
                - Use shell commands for tasks like compiling, running tests, checking git status
                - Use fetch_url for reading web pages and documentation
                - Use web_search for finding information online
                - Use http_request for API calls or as fallback when fetch_url fails
                - Use github_api_request for ALL GitHub-related tasks.
                  This tool has automatic token injection (from GITHUB_TOKEN or GH_TOKEN env var).
                  If the env var is set, the tool works — period. Do not question it.
                - If a task is unclear, ask for clarification before proceeding
                - IMAGE OUTPUT: The web frontend supports inline image rendering. When you
                  generate or obtain an image URL (from skills like image generation, or any
                  tool that returns image URLs), you MUST use Markdown image syntax
                  ![description](image_url) so the image is displayed directly in the chat.
                  Do NOT output bare URLs — always wrap them in Markdown image syntax.
                """;
    }

    // -----------------------------------------------------------------
    //  Custom instructions loader
    // -----------------------------------------------------------------

    /**
     * Loads custom instructions from the classpath resource
     * {@code prompts/custom-instructions.md}. Returns an empty string if the
     * resource is not found.
     */
    private static String loadCustomInstructions() {
        try (InputStream is = SystemPromptBuilder.class.getResourceAsStream(
                "/prompts/custom-instructions.md")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
                if (!content.isEmpty()) {
                    return "### Custom Instructions\n\n" + content;
                }
            }
        } catch (Exception e) {
            // ignore — custom instructions are optional
        }
        return "";
    }
}
