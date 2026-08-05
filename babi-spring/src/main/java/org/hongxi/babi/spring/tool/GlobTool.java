package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.GlobLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for finding files by name pattern using glob syntax.
 *
 * <p>Delegates to {@link GlobLogic} for the actual file search logic.
 */
public class GlobTool {

    @Tool(name = "glob_files", description = "Find files by name pattern (glob) under a directory. "
            + "Returns matching file paths. Supports patterns like '**/*.java', '*.xml', 'src/**/*.py'. "
            + "Use this to discover files before reading them.")
    public String globFiles(
            @ToolParam(description = "Glob pattern to match files, e.g. '**/*.java', '*.xml'") String pattern,
            @ToolParam(description = "Directory to search in (default: current directory)") String directory,
            @ToolParam(description = "Maximum number of results to return (default: 100)") int maxResults) {
        return GlobLogic.glob(pattern, directory, maxResults);
    }
}
