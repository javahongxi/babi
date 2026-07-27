package org.hongxi.babi.common.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Shared logic for executing shell commands.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both LangGraph4j and Spring AI tool wrappers.
 */
public final class ShellCommandLogic {

    private ShellCommandLogic() {}

    /** Default timeout in seconds for command execution. */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Executes a shell command and returns its output.
     *
     * @param command    the shell command to execute
     * @param workingDir optional working directory (may be {@code null})
     * @return command output, optionally with exit code appended
     */
    public static String shellCommand(String command, File workingDir) {
        return shellCommand(command, workingDir, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Executes a shell command with a custom timeout and returns its output.
     *
     * @param command        the shell command to execute
     * @param workingDir     optional working directory (may be {@code null})
     * @param timeoutSeconds maximum execution time in seconds
     * @return command output, optionally with exit code appended
     */
    public static String shellCommand(String command, File workingDir, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (workingDir != null && workingDir.isDirectory()) {
                pb.directory(workingDir);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + timeoutSeconds + " seconds: " + command;
            }

            int exitCode = process.exitValue();
            String result = output.toString();
            if (exitCode != 0) {
                result += "\n[Exit code: " + exitCode + "]";
            }
            return result;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
