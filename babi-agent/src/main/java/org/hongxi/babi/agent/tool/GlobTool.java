package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.GlobLogic;

/**
 * Tool for finding files by name pattern using glob syntax.
 *
 * <p>Delegates to {@link GlobLogic} for the actual file search logic.
 */
public class GlobTool {

    @Tool(
            name = "glob_files",
            description = "Find files by name pattern (glob) under a directory. "
                    + "Returns matching file paths. Supports patterns like '**/*.java', '*.xml', 'src/**/*.py'. "
                    + "Use this to discover files before reading them.",
            readOnly = true)
    public String globFiles(
            @ToolParam(name = "pattern", description = "Glob pattern to match files, e.g. '**/*.java', '*.xml'") String pattern,
            @ToolParam(name = "directory", description = "Directory to search in (default: current directory)") String directory,
            @ToolParam(name = "max_results", description = "Maximum number of results to return (default: 100)") int maxResults) {
        return GlobLogic.glob(pattern, directory, maxResults);
    }
}
