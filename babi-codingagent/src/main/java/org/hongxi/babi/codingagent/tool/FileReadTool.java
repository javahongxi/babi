package org.hongxi.babi.codingagent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tool for reading file contents.
 *
 * <p>Allows the agent to read and analyze source code files,
 * configuration files, and other text-based files.
 */
public class FileReadTool {

    @Tool(name = "read_file", description = "Read the contents of a file at the given path. Returns the file content as a string.", readOnly = true)
    public String readFile(
            @ToolParam(name = "file_path", description = "Absolute or relative path to the file to read") String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "Error: File not found: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "Error: Not a regular file: " + filePath;
            }
            return Files.readString(path);
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
