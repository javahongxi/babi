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
 * <p><b>Note:</b> This tool is not registered by default. The framework's built-in
 * {@code ShellExecuteTool} is used instead. If you prefer to use this tool for its
 * cleaner output format, you need to disable the built-in tool when building the agent:
 * <pre>{@code
 * HarnessAgent.builder()
 *     .disableShellTool()
 *     // register this custom tool
 *     .build();
 * }</pre>
 */
public class ShellCommandTool {

    /** Default timeout in seconds for command execution. */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final File workingDir;

    public ShellCommandTool() {
        this(null);
    }

    public ShellCommandTool(String workingDir) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
    }

    @Tool(
            name = "shell_command",
            description = "Execute a shell command on the local system and return its output. "
                    + "Use for build, test, git, and diagnostic commands.")
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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds: " + command;
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