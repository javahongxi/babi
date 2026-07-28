package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.FileReadLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for reading file contents via Spring AI @Tool annotation.
 *
 * <p>Delegates to {@link FileReadLogic} for the actual file reading.
 */
public class FileReadTool {

    @Tool(description = "Read the contents of a file at the given path. Returns the file content as a string.")
    public String readFile(
            @ToolParam(description = "Absolute or relative path to the file to read") String filePath) {
        return FileReadLogic.readFile(filePath);
    }
}
