package org.hongxi.babi.common.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared logic for reading file contents.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both LangGraph4j and Spring AI tool wrappers.
 */
public final class FileReadLogic {

    private FileReadLogic() {}

    /**
     * Reads the contents of a file at the given path.
     *
     * @param filePath absolute or relative path to the file
     * @return the file content, or an error message
     */
    public static String readFile(String filePath) {
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
