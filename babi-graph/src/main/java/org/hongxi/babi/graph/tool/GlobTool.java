package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.GlobLogic;

/**
 * Tool for finding files by name pattern using glob syntax.
 *
 * <p>Delegates to {@link GlobLogic} for the actual file search logic.
 */
public class GlobTool {

    @Tool(name = "glob_files", value = "Find files by name pattern (glob) under a directory. "
            + "Returns matching file paths. Supports patterns like '**/*.java', '*.xml', 'src/**/*.py'. "
            + "Use this to discover files before reading them.")
    public String globFiles(
            @P("Glob pattern to match files, e.g. '**/*.java', '*.xml'") String pattern,
            @P("Directory to search in (default: current directory)") String directory,
            @P("Maximum number of results to return (default: 100)") int maxResults) {
        return GlobLogic.glob(pattern, directory, maxResults);
    }
}
