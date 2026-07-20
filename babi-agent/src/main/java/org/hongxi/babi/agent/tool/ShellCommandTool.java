package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Tool for executing shell commands.
 *
 * <p>Allows the agent to run shell commands for build, test,
 * and diagnostic tasks. Commands execute within the configured
 * working directory.
 */
public class ShellCommandTool {

    private final File workingDir;

    /** Creates a ShellCommandTool with no specific working directory (inherits user.dir). */
    public ShellCommandTool() {
        this.workingDir = null;
    }

    /**
     * Creates a ShellCommandTool with a specific working directory.
     *
     * @param workingDir the directory in which commands are executed
     */
    public ShellCommandTool(String workingDir) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
    }

    @Tool(name = "shell_command", description = "Execute a shell command on the local system and return its output. Use for build, test, git, and diagnostic commands.")
    public String shellCommand(
            @ToolParam(name = "command", description = "The shell command to execute") String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            if (workingDir != null && workingDir.isDirectory()) {
                pb.directory(workingDir);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after 30 seconds: " + command;
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
