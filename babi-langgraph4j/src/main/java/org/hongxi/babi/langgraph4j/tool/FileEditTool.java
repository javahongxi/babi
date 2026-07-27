package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for editing files via exact string replacement.
 */
public class FileEditTool {

    private final ToolEventBus eventBus;

    public FileEditTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Tool("Edit a file by replacing an exact text match. The old_text must match the file content exactly (including whitespace). Only the first occurrence is replaced.")
    public String editFile(
            @P("Path to the file to edit") String filePath,
            @P("The exact text to find in the file (must match precisely)") String oldText,
            @P("The text to replace it with") String newText) {

        emitEvent("edit_file", Map.of("file_path", filePath));
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "Error: File not found: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "Error: Not a regular file: " + filePath;
            }

            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return "Error: old_text not found in file. Make sure the text matches exactly, including whitespace and indentation. Try reading the file first to get the exact content.";
            }

            String updated = content.replaceFirst(
                    Pattern.quote(oldText),
                    Matcher.quoteReplacement(newText));

            Files.writeString(path, updated);

            long remaining = content.split(Pattern.quote(oldText), -1).length - 1;
            if (remaining > 1) {
                return "Successfully replaced 1 occurrence. Note: " + (remaining - 1) + " more occurrence(s) of old_text remain in the file.";
            }
            return "Successfully edited " + filePath;
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
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
