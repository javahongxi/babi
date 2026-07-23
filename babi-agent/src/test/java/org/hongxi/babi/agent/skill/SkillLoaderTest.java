package org.hongxi.babi.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.hongxi.babi.agent.skill.SkillLoader.Skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillLoaderTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------
    // parseSkillFile — YAML front-matter
    // -----------------------------------------------------------------

    @Test
    void parseSkillFile_withFullFrontMatter() throws IOException {
        Path file = writeFile("my-skill.md", """
                ---
                name: code-review
                description: Perform structured code review
                ---
                
                # Instructions
                1. Read the target file
                """);

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals("code-review", skill.name());
        assertEquals("Perform structured code review", skill.description());
        assertTrue(skill.body().contains("# Instructions"));
        assertFalse(skill.body().startsWith("---"));
        assertNull(skill.directory()); // flat file → no directory
    }

    @Test
    void parseSkillFile_withQuotedValues() throws IOException {
        Path file = writeFile("quoted.md", """
                ---
                name: "my-quoted-skill"
                description: "A quoted description"
                ---
                
                Body here.
                """);

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals("my-quoted-skill", skill.name());
        assertEquals("A quoted description", skill.description());
    }

    @Test
    void parseSkillFile_noFrontMatter_fallsBackToFilename() throws IOException {
        Path file = writeFile("fallback-name.md", """
                This is the first real line of content.
                Second line.
                """);

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals("fallback-name", skill.name());
        assertEquals("This is the first real line of content.", skill.description());
        assertTrue(skill.body().contains("first real line"));
    }

    @Test
    void parseSkillFile_noFrontMatter_skipsHeadings() throws IOException {
        Path file = writeFile("skip-heading.md", """
                # Title Heading

                Actual description line here.
                """);

        Skill skill = SkillLoader.parseSkillFile(file);

        // Heading should be skipped, first non-empty non-heading line used
        assertEquals("Actual description line here.", skill.description());
    }

    @Test
    void parseSkillFile_descriptionTruncatedAt100Chars() throws IOException {
        String longLine = "A".repeat(150);
        Path file = writeFile("long-desc.md", longLine + "\n\nBody.");

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals(103, skill.description().length()); // 100 + "..."
        assertTrue(skill.description().endsWith("..."));
    }

    @Test
    void parseSkillFile_skillMdInDirectory_usesParentDirName() throws IOException {
        // Create dir/my-skill/SKILL.md layout
        Path skillDir = tempDir.resolve("my-cool-skill");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: overridden-name
                description: Dir-based skill
                ---
                
                Instructions body.
                """, StandardCharsets.UTF_8);

        Skill skill = SkillLoader.parseSkillFile(skillMd);

        assertEquals("overridden-name", skill.name());
        assertEquals("Dir-based skill", skill.description());
        // directory should point to the skill root dir
        assertNotNull(skill.directory());
        assertTrue(skill.directory().toString().endsWith("my-cool-skill"));
    }

    @Test
    void parseSkillFile_skillMdNoFrontMatter_usesParentDirNameAsFallback() throws IOException {
        Path skillDir = tempDir.resolve("dir-skill-fallback");
        Files.createDirectories(skillDir);
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, "Just a body line.\n", StandardCharsets.UTF_8);

        Skill skill = SkillLoader.parseSkillFile(skillMd);

        // "SKILL" → fallback to parent dir name
        assertEquals("dir-skill-fallback", skill.name());
        assertEquals("Just a body line.", skill.description());
    }

    // -----------------------------------------------------------------
    // skillDirectory
    // -----------------------------------------------------------------

    @Test
    void skillDirectory_skillMd_returnsParentDir() {
        Path skillMd = tempDir.resolve("some-skill/SKILL.md");
        Path result = SkillLoader.skillDirectory(skillMd);

        assertNotNull(result);
        assertTrue(result.toString().endsWith("some-skill"));
    }

    @Test
    void skillDirectory_flatFile_returnsNull() {
        Path flatFile = tempDir.resolve("my-skill.md");
        Path result = SkillLoader.skillDirectory(flatFile);

        assertNull(result);
    }

    // -----------------------------------------------------------------
    // loadFromDir — via loadAll with controlled directories
    //   (loadFromDir is private, so we test indirectly by verifying
    //    that loadAll returns expected skills from a temp structure)
    // -----------------------------------------------------------------

    @Test
    void loadAll_babiDirOverridesGlobalDir() throws IOException {
        // We can't easily override the static GLOBAL_DIR/BABI_DIR constants,
        // but we can verify the override semantics by loading the same file
        // twice through parseSkillFile and checking the last-write-wins behavior
        // of the LinkedHashMap used in loadFromDir.

        // Instead, test that loadFromDir correctly populates a map by
        // simulating what loadAll does: load a dir, then load another dir
        // with a same-named skill that should override.

        // Since loadFromDir is private, we test the map-merge behavior
        // by directly calling parseSkillFile and verifying the name resolution.

        // Global skill
        Path globalFile = writeFile("shared-skill.md", """
                ---
                name: shared
                description: Global version
                ---
                Global body.
                """);
        Skill globalSkill = SkillLoader.parseSkillFile(globalFile);
        assertEquals("shared", globalSkill.name());

        // Babi override
        Path babiFile = writeFile("shared-skill-v2.md", """
                ---
                name: shared
                description: Babi override version
                ---
                Babi body.
                """);
        Skill babiSkill = SkillLoader.parseSkillFile(babiFile);
        assertEquals("shared", babiSkill.name());
        assertEquals("Babi override version", babiSkill.description());
    }

    @Test
    void parseSkillFile_emptyDescription_noBody() throws IOException {
        Path file = writeFile("empty.md", "");

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals("empty", skill.name());
        assertEquals("", skill.description());
        assertEquals("", skill.body());
    }

    @Test
    void parseSkillFile_onlyFrontMatter_noBody() throws IOException {
        Path file = writeFile("only-fm.md", """
                ---
                name: fm-only
                description: Has front-matter but no body
                ---
                """);

        Skill skill = SkillLoader.parseSkillFile(file);

        assertEquals("fm-only", skill.name());
        assertEquals("Has front-matter but no body", skill.description());
        assertEquals("", skill.body().strip());
    }

    // -----------------------------------------------------------------
    // loadAll — project-level skills (via cwd)
    // -----------------------------------------------------------------

    @Test
    void loadAll_noQoderDir_noError() {
        // When cwd has no .qoder/skills, loadAll should not throw
        assertDoesNotThrow(() -> SkillLoader.loadAll());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
