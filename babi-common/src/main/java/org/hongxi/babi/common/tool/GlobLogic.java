package org.hongxi.babi.common.tool;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared logic for finding files by name pattern using glob syntax.
 *
 * <p>Uses {@link java.nio.file.PathMatcher} with {@code glob:} syntax to match
 * file names against patterns like {@code **\/*.java}, {@code *.xml}, etc.
 *
 * <p>Automatically excludes common non-source directories such as {@code .git},
 * {@code node_modules}, {@code target}, etc.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * all three module-specific GlobTool wrappers.
 */
public final class GlobLogic {

    private GlobLogic() {}

    /** Default maximum number of results. */
    private static final int DEFAULT_MAX_RESULTS = 100;

    /** Directories to skip during traversal. */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode", "__pycache__"
    );

    /**
     * Finds files matching a glob pattern under a directory.
     *
     * @param pattern    glob pattern to match (e.g. "**&#47;*.java", "*.xml")
     * @param directory  root directory to search (defaults to "." if blank)
     * @param maxResults maximum number of results (defaults to 100 if &lt;= 0)
     * @return newline-separated list of matched file paths (relative to directory)
     */
    public static String glob(String pattern, String directory, int maxResults) {
        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern cannot be empty";
        }
        if (directory == null || directory.isBlank()) {
            directory = ".";
        }
        if (maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }

        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            return "Error: Not a directory: " + directory;
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
        List<String> matched = new ArrayList<>();
        final int limit = maxResults;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    if (EXCLUDED_DIRS.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = root.relativize(file);
                    if (matcher.matches(relative) || matcher.matches(file.getFileName())) {
                        matched.add(relative.toString());
                        if (matched.size() >= limit) return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching: " + e.getMessage();
        }

        if (matched.isEmpty()) {
            return "No files matched pattern: " + pattern;
        }

        String result = String.join("\n", matched);
        if (matched.size() >= limit) {
            result += "\n[Showing first " + matched.size()
                    + " results. Narrow your pattern or increase max_results for more.]";
        }
        return result;
    }
}
