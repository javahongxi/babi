package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.agent.skill.SkillLoader;
import org.hongxi.babi.agent.skill.SkillLoader.Skill;

import java.util.Map;

/**
 * Tool for discovering and activating Skills.
 *
 * <p>Skills are Markdown-based instruction sets loaded from:
 * <ul>
 *   <li>{@code ~/.agents/skills/} — global shared skills</li>
 *   <li>{@code ~/.babi/skills/}   — Babi-specific skills (higher priority)</li>
 *   <li>{@code .qoder/skills/}    — project-level skills (highest priority)</li>
 * </ul>
 *
 * <p>The agent calls {@code list_skills} to see what's available, then
 * {@code use_skill} to load the full instructions for a specific skill.
 */
public class SkillTool {

    private final Map<String, Skill> skills;

    public SkillTool() {
        this.skills = SkillLoader.loadAll();
    }

    /**
     * Returns the loaded skills map (unmodifiable).
     */
    public Map<String, Skill> getSkills() {
        return skills;
    }

    @Tool(name = "list_skills", description = "List all available skills. Returns skill names and descriptions. Call this before use_skill to discover what skills are available.", readOnly = true)
    public String listSkills() {
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

    @Tool(name = "use_skill", description = "Activate a skill by name and get its full instructions. The instructions will guide you through the workflow. Call list_skills first to see available skills.", readOnly = true)
    public String useSkill(
            @ToolParam(name = "skill_name", description = "The name of the skill to activate (from list_skills output)") String skillName) {

        Skill skill = skills.get(skillName);
        if (skill == null) {
            // Try case-insensitive match
            for (Map.Entry<String, Skill> entry : skills.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(skillName)) {
                    skill = entry.getValue();
                    break;
                }
            }
        }

        if (skill == null) {
            return "Error: Skill '" + skillName + "' not found. Available skills: "
                    + String.join(", ", skills.keySet())
                    + ". Use list_skills to see all available skills.";
        }

        String dirInfo = skill.directory() != null
                ? "\n\n**Skill directory**: `" + skill.directory() + "`\n"
                  + "All relative paths in the instructions above (e.g. `scripts/...`, `references/...`) "
                  + "are relative to this directory. Use absolute paths when executing commands."
                : "";

        return "## Skill: " + skill.name() + "\n\n" + skill.body() + dirInfo;
    }
}
