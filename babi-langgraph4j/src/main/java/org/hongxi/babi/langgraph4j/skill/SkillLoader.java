package org.hongxi.babi.langgraph4j.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads Skill definitions from three directories (lowest to highest priority):
 * <ol>
 *   <li>{@code ~/.agents/skills/} — global shared skills</li>
 *   <li>{@code ~/.babi/skills/}   — Babi-specific skills</li>
 *   <li>{@code .qoder/skills/}    — project-level skills (highest priority)</li>
 * </ol>
 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private static final Path GLOBAL_DIR = Path.of(System.getProperty("user.home"), ".agents", "skills");
    private static final Path BABI_DIR   = Path.of(System.getProperty("user.home"), ".babi",  "skills");
    private static final String PROJECT_SKILLS_DIR = ".qoder/skills";

    private SkillLoader() {}

    /**
     * Loads all skills from global, Babi-specific, and project-level directories.
     *
     * @param workspacePath the absolute path of the current workspace
     * @return unmodifiable map of skill-name to {@link Skill}
     */
    public static Map<String, Skill> loadAll(Path workspacePath) {
        Map<String, Skill> skills = new LinkedHashMap<>();

        loadFromDir(GLOBAL_DIR, skills);
        loadFromDir(BABI_DIR, skills);

        Path projectSkillsDir = workspacePath.toAbsolutePath().normalize().resolve(PROJECT_SKILLS_DIR);
        loadFromDir(projectSkillsDir, skills);

        log.info("Loaded {} skill(s) from {}, {}, and {}", skills.size(), GLOBAL_DIR, BABI_DIR, projectSkillsDir);
        return Collections.unmodifiableMap(skills);
    }

    private static void loadFromDir(Path dir, Map<String, Skill> target) {
        if (!Files.isDirectory(dir)) {
            log.debug("Skill directory does not exist, skipping: {}", dir);
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".md"))
                  .forEach(p -> loadSingleFile(p, target));

            try (Stream<Path> subdirs = Files.list(dir)) {
                subdirs.filter(Files::isDirectory)
                       .forEach(subdir -> {
                           Path skillMd = subdir.resolve("SKILL.md");
                           if (Files.isRegularFile(skillMd)) {
                               loadSingleFile(skillMd, target);
                           }
                       });
            }
        } catch (IOException e) {
            log.warn("Failed to list skill directory {}: {}", dir, e.getMessage());
        }
    }

    private static void loadSingleFile(Path file, Map<String, Skill> target) {
        try {
            Skill skill = parseSkillFile(file);
            if (skill != null) {
                target.put(skill.name(), skill);
                log.debug("Loaded skill '{}' from {}", skill.name(), file);
            }
        } catch (Exception e) {
            log.warn("Failed to load skill from {}: {}", file, e.getMessage());
        }
    }

    static Path skillDirectory(Path skillFile) {
        String fileName = skillFile.getFileName().toString();
        if ("SKILL.md".equalsIgnoreCase(fileName)) {
            return skillFile.getParent().toAbsolutePath().normalize();
        }
        return null;
    }

    static Skill parseSkillFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String fileName = file.getFileName().toString().replace(".md", "");

        String defaultName = "SKILL".equalsIgnoreCase(fileName)
                ? file.getParent().getFileName().toString()
                : fileName;
        String name = defaultName;
        String description = "";
        String body = content;

        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontMatter = content.substring(3, endIdx).strip();
                body = content.substring(endIdx + 3).strip();

                for (String line : frontMatter.split("\n")) {
                    line = line.strip();
                    if (line.startsWith("name:")) {
                        name = line.substring(5).strip().replaceAll("^\"|\"$", "");
                    } else if (line.startsWith("description:")) {
                        description = line.substring(12).strip().replaceAll("^\"|\"$", "");
                    }
                }
            }
        }

        if (description.isEmpty()) {
            for (String line : body.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    description = trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed;
                    break;
                }
            }
        }

        Path dir = skillDirectory(file);
        return new Skill(name, description, body, dir);
    }

    /**
     * Represents a single loaded Skill.
     */
    public record Skill(String name, String description, String body, Path directory) {}
}
