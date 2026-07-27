package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tool for executing shell commands.
 */
public class ShellCommandTool {

    private final File workingDir;
    private final ToolEventBus eventBus;

    public ShellCommandTool(ToolEventBus eventBus) {
        this(null, eventBus);
    }

    public ShellCommandTool(String workingDir, ToolEventBus eventBus) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
        this.eventBus = eventBus;
    }

    @Tool("Execute a shell command on the local system and return its output. Use for build, test, git, and diagnostic commands.")
    public String shellCommand(@P("The shell command to execute") String command) {
        emitEvent("shell_command", Map.of("command", command));
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

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) {
                eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
            }
        }
    }
}
