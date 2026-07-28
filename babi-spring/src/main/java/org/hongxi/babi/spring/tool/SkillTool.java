package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.skill.SkillLoader;
import org.hongxi.babi.common.skill.SkillLoader.Skill;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;
import java.util.Map;

/**
 * Tool for discovering and activating Skills via Spring AI @Tool annotation.
 *
 * <p>Skills are Markdown-based instruction sets loaded from:
 * <ul>
 *   <li>{@code ~/.agents/skills/} — global shared skills</li>
 *   <li>{@code ~/.babi/skills/}   — Babi-specific skills (higher priority)</li>
 *   <li>{@code {workspace}/.qoder/skills/} — project-level skills (highest priority)</li>
 * </ul>
 */
public class SkillTool {

    private final Map<String, Skill> skills;

    public SkillTool(Path workspacePath) {
        this.skills = SkillLoader.loadAll(workspacePath);
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    @Tool(description = "List all available skills. Returns skill names and descriptions. Call this before use_skill to discover what skills are available.")
    public String list_skills() {
        if (skills.isEmpty()) {
            return "No skills found. Create .md files in ~/.agents/skills/, ~/.babi/skills/, or .qoder/skills/ to add skills.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Available skills (").append(skills.size()).append("):\n\n");
        for (Skill skill : skills.values()) {
            sb.append("- **").append(skill.name()).append("**: ").append(skill.description()).append("\n");
        }
        sb.append("\nUse use_skill(skill_name) to activate a skill and get its instructions.");
        return sb.toString();
    }

    @Tool(description = "Activate a skill by name and get its full instructions. The instructions will guide you through the workflow. Call list_skills first to see available skills.")
    public String use_skill(
            @ToolParam(description = "The name of the skill to activate (from list_skills output)") String skill_name) {
        Skill skill = skills.get(skill_name);
        if (skill == null) {
            for (Map.Entry<String, Skill> entry : skills.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(skill_name)) {
                    skill = entry.getValue();
                    break;
                }
            }
        }
        if (skill == null) {
            return "Error: Skill '" + skill_name + "' not found. Available skills: "
                    + String.join(", ", skills.keySet())
                    + ". Use list_skills to see all available skills.";
        }
        String dirInfo = skill.directory() != null
                ? "\n\n**Skill directory**: `" + skill.directory() + "`\n"
                + "All relative paths in the instructions above are relative to this directory."
                : "";
        return "## Skill: " + skill.name() + "\n\n" + skill.body() + dirInfo;
    }
}
