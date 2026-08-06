package org.hongxi.babi.common.prompt;

import org.hongxi.babi.common.skill.SkillLoader.Skill;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

/**
 * Constructs the system prompt for the coding agent.
 *
 * <p>This shared prompt builder provides babi-specific rules and custom tool
 * guidance. It is used by both AgentScope and LangGraph4j implementations.
 *
 * <p>To customize the prompt without recompiling, place a file at
 * {@code prompts/custom-instructions.md} on the classpath (e.g. under
 * {@code src/main/resources/prompts/}). Its content will be appended to the
 * built-in prompt.
 */
public final class CodingSystemPrompt {

    private CodingSystemPrompt() {}

    /**
     * Builds the system prompt with loaded skills and workspace info.
     *
     * @param workspace the absolute path of the current workspace
     * @param skills    the collection of loaded skills (maybe empty)
     */
    public static String build(String workspace, Collection<Skill> skills) {
        return build(workspace, skills, false);
    }

    /**
     * Builds the system prompt with loaded skills, workspace info, and search awareness.
     *
     * @param workspace     the absolute path of the current workspace
     * @param skills        the collection of loaded skills (maybe empty)
     * @param enableSearch  whether the LLM's built-in search is enabled (no web_search tool needed)
     */
    public static String build(String workspace, Collection<Skill> skills, boolean enableSearch) {
        String custom = loadCustomInstructions();
        return String.join("\n\n",
                workspaceSection(workspace),
                coreRulesSection(enableSearch, skills),
                skillsSection(skills),
                guidelinesSection(enableSearch),
                custom
        ).strip();
    }

    /**
     * Builds the system prompt with no skills (backward compatibility).
     */
    public static String build() {
        return build(System.getProperty("user.dir"), Collections.emptyList());
    }

    // -----------------------------------------------------------------
    //  Sections
    // -----------------------------------------------------------------

    private static String workspaceSection(String workspace) {
        return """
                === WORKSPACE CONTEXT (HIGHEST PRIORITY) ===
                Current workspace: %s
                Use this as the base for relative paths, git operations, and project context.
                You CAN read files anywhere on the filesystem (no sandbox). Confirm with the
                user before modifying files OUTSIDE the workspace.
                Always trust THIS path over any prior conversation — each session may differ.
                """.formatted(workspace);
    }

    private static String coreRulesSection(boolean enableSearch, Collection<Skill> skills) {
        String searchHint = enableSearch
                ? "use your built-in search or call fetch_url"
                : "call web_search, fetch_url, or the appropriate tool";

        boolean hasImageSkill = skills != null && skills.stream()
                .anyMatch(s -> (s.name() + " " + s.description()).toLowerCase().contains("image"));
        String imageRule = hasImageSkill
                ? "Image skills are installed — use `use_skill` to load the relevant image skill for ANY image request."
                : "Use the `generate_image` tool for text-to-image generation.";

        return """
                CRITICAL RULES:

                1. TOOL-FIRST: Always TRY calling tools before claiming failure. Never assume
                   a tool is unavailable — only report failure AFTER the tool returns an error.
                   Never fabricate content from URLs/files you haven't accessed via tools.
                   If a tool returns empty or garbled content, report that honestly.

                2. ACT FIRST: When the user needs real-time info (news, weather, prices, etc.),
                   %s immediately. Never present a menu of options — just do it and report results.

                3. GITHUB: For ANY GitHub request (repos, issues, PRs, profile, search, etc.),
                   call github_api_request IMMEDIATELY. It calls api.github.com with a Bearer
                   token and returns JSON. Never use fetch_url for github.com URLs.
                   Common URL-to-API conversions:
                   - github.com/{user}           → GET /users/{user}
                   - github.com/{user}/{repo}    → GET /repos/{user}/{repo}
                   - github.com/{user}/{repo}/issues → GET /repos/{user}/{repo}/issues
                   Pattern: github.com/{owner}/{repo}/{type} → /repos/{owner}/{repo}/{type}
                   Also: github_pinned_repos tool for pinned repos.

                4. IMAGE: %s
                   When outputting image URLs, ALWAYS use Markdown image syntax
                   ![description](image_url). Never output bare URLs.
                """.formatted(searchHint, imageRule);
    }

    private static String skillsSection(Collection<Skill> skills) {
        StringBuilder sb = new StringBuilder("SKILLS:");
        if (skills != null && !skills.isEmpty()) {
            for (Skill skill : skills) {
                sb.append("\n- ").append(skill.name()).append(": ").append(skill.description());
            }
            sb.append("\nCall use_skill(skill_name) to load full instructions before executing.");
        } else {
            sb.append(" No skills installed. Use list_skills to check, or add .md files to ~/.agents/skills/.");
        }
        return sb.toString();
    }

    private static String guidelinesSection(boolean enableSearch) {
        String searchGuideline = enableSearch
                ? "- You have built-in search — use it proactively"
                : "- Use web_search for finding information online";
        return """
                Guidelines:
                - Explain what you're doing before executing commands
                - Be cautious with destructive commands (rm, etc.)
                - Use shell for compiling, running tests, git status
                - Use fetch_url for web pages; http_request for APIs or as fallback
                %s
                - Ask for clarification if a task is unclear
                """.formatted(searchGuideline);
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
        try (InputStream is = CodingSystemPrompt.class.getResourceAsStream(
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
