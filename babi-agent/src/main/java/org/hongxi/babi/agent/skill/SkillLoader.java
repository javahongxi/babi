package org.hongxi.babi.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads Skill definitions from two directories:
 * <ol>
 *   <li>{@code ~/.agents/skills/} — global shared skills (cross-project reuse)</li>
 *   <li>{@code ~/.babi/skills/}   — Babi-specific skills (higher priority)</li>
 * </ol>
 *
 * <p>When both directories contain a skill with the same name, the Babi-specific
 * one wins (project-level override).
 *
 * <p>Skill file format (Markdown with YAML front-matter):
 * <pre>
 * ---
 * name: code-review
 * description: Perform structured code review on source files
 * ---
 *
 * # Instructions
 * 1. Read the target file...
 * </pre>
 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private static final Path GLOBAL_DIR = Path.of(System.getProperty("user.home"), ".agents", "skills");
    private static final Path BABI_DIR   = Path.of(System.getProperty("user.home"), ".babi",  "skills");

    private SkillLoader() {}

    /**
     * Loads all skills from both directories. Babi-dir skills override global-dir
     * skills with the same name.
     *
     * @return unmodifiable map of skill-name → {@link Skill}
     */
    public static Map<String, Skill> loadAll() {
        Map<String, Skill> skills = new LinkedHashMap<>();

        // 1. Global skills first (lower priority)
        loadFromDir(GLOBAL_DIR, skills);

        // 2. Babi-specific skills (higher priority, overrides global)
        loadFromDir(BABI_DIR, skills);

        log.info("Loaded {} skill(s) from {} and {}", skills.size(), GLOBAL_DIR, BABI_DIR);
        return Collections.unmodifiableMap(skills);
    }

    /**
     * Scans a directory for skills. Supports two layouts:
     * <ul>
     *   <li>Flat files: {@code dir/my-skill.md}</li>
     *   <li>Directory format: {@code dir/my-skill/SKILL.md}</li>
     * </ul>
     */
    private static void loadFromDir(Path dir, Map<String, Skill> target) {
        if (!Files.isDirectory(dir)) {
            log.debug("Skill directory does not exist, skipping: {}", dir);
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".md"))
                  .forEach(p -> loadSingleFile(p, target));

            // Also scan subdirectories for SKILL.md (directory-based skill format)
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

    /**
     * Parses a single skill Markdown file.
     *
     * <p>Expected format:
     * <pre>
     * ---
     * name: my-skill
     * description: What this skill does
     * ---
     *
     * (instructions body)
     * </pre>
     *
     * <p>If front-matter is missing, the file name (without .md) is used as the
     * name, and the first non-empty line is used as the description.
     */
    static Skill parseSkillFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String fileName = file.getFileName().toString().replace(".md", "");

        // For SKILL.md inside a directory, use parent dir name as fallback
        String defaultName = "SKILL".equalsIgnoreCase(fileName)
                ? file.getParent().getFileName().toString()
                : fileName;
        String name = defaultName;
        String description = "";
        String body = content;

        // Parse YAML front-matter if present
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

        // Fallback: use first non-empty line as description
        if (description.isEmpty()) {
            for (String line : body.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    description = trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed;
                    break;
                }
            }
        }

        return new Skill(name, description, body);
    }

    /**
     * Represents a single loaded Skill.
     */
    public record Skill(String name, String description, String body) {}
}
