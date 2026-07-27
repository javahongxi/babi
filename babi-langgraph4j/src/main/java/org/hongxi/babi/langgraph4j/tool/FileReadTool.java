package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Tool for reading file contents.
 */
public class FileReadTool {

    private final ToolEventBus eventBus;

    public FileReadTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Tool("Read the contents of a file at the given path. Returns the file content as a string.")
    public String readFile(@P("Absolute or relative path to the file to read") String filePath) {
        emitEvent("read_file", Map.of("file_path", filePath));
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

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) {
                eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
            }
        }
    }
}
