package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.FileReadLogic;

import java.util.Map;

/**
 * Tool for reading file contents.
 *
 * <p>Delegates to {@link FileReadLogic} for the actual file reading.
 */
public class FileReadTool extends AbstractNotifyingTool {

    public FileReadTool(ToolEventBus eventBus) {
        super(eventBus);
    }

    @Tool(name = "read_file", value = "Read the contents of a file at the given path. Returns the file content as a string.")
    public String readFile(@P("Absolute or relative path to the file to read") String filePath) {
        emitEvent("read_file", Map.of("file_path", filePath));
        return FileReadLogic.readFile(filePath);
    }
}
