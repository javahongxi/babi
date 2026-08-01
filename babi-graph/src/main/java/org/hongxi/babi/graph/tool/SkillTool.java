package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.skill.SkillLoader;
import org.hongxi.babi.common.skill.SkillLoader.Skill;

import java.nio.file.Path;
import java.util.Map;

/**
 * Tool for discovering and activating Skills.
 */
public class SkillTool extends AbstractNotifyingTool {

    private final Map<String, Skill> skills;

    public SkillTool(Path workspacePath, ToolEventBus eventBus) {
        super(eventBus);
        this.skills = SkillLoader.loadAll(workspacePath);
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    @Tool(name = "list_skills", value = "List all available skills. Returns skill names and descriptions. Call this before use_skill to discover what skills are available.")
    public String listSkills() {
        emitEvent("list_skills", Map.of());
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

    @Tool(name = "use_skill", value = "Activate a skill by name and get its full instructions. The instructions will guide you through the workflow. Call list_skills first to see available skills.")
    public String useSkill(@P("The name of the skill to activate (from list_skills output)") String skillName) {
        emitEvent("use_skill", Map.of("skill_name", skillName));
        Skill skill = skills.get(skillName);
        if (skill == null) {
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
                  + "All relative paths in the instructions above are relative to this directory."
                : "";
        return "## Skill: " + skill.name() + "\n\n" + skill.body() + dirInfo;
    }
}
