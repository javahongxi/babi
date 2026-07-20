package org.hongxi.babi.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.tool.CodeSearchTool;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.FileEditTool;
import org.hongxi.babi.agent.tool.FileReadTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.ShellCommandTool;
import org.hongxi.babi.agent.tool.SkillTool;
import org.hongxi.babi.agent.tool.TodoWriteTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point for the Babi Agent.
 *
 * <p>Provides an interactive console for chatting with the babi agent.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DASHSCOPE_API_KEY=your_key
 *   mvn exec:java -pl babi-agent
 *   mvn exec:java -pl babi-agent -Dexec.args="--workspace ~/my-project"
 * </pre>
 */
public class BabiAgentCli {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: DASHSCOPE_API_KEY environment variable not set.");
            System.err.println("Get your API key from: https://dashscope.aliyun.com");
            System.err.println("Then set it with: export DASHSCOPE_API_KEY=your_api_key");
            System.exit(1);
        }

        // Parse --workspace argument
        String workspace = AgentConstants.DEFAULT_WORKSPACE;
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i]) && i + 1 < args.length) {
                workspace = AgentConstants.resolveWorkspace(args[i + 1]);
                break;
            }
        }
        // Ensure workspace directory exists
        Files.createDirectories(Path.of(workspace));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Babi Agent - Powered by AgentScope Java");
        System.out.println("=".repeat(60));
        System.out.println("Workspace: " + workspace);
        System.out.println("An AI coding assistant with file reading and shell tools.");
        System.out.println("Type 'exit' to quit.\n");

        // Build agent using the core API (no Spring context needed for CLI)
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FileReadTool());
        toolkit.registerTool(new FileEditTool());
        toolkit.registerTool(new ShellCommandTool(workspace));
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        toolkit.registerTool(new CodeSearchTool());
        toolkit.registerTool(new TodoWriteTool());
        toolkit.registerTool(new SkillTool());

        ReActAgent agent = ReActAgent.builder()
                .name(AgentConstants.AGENT_NAME)
                .sysPrompt(AgentConstants.systemPrompt(workspace))
                .model(AgentConstants.createModel())
                .toolkit(toolkit)
                .maxIters(20)
                .middleware(new ContextTruncateMiddleware(30))
                .build();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("You: ");
            String input = reader.readLine();

            if (input == null || input.trim().equalsIgnoreCase("exit")) {
                System.out.println("\nGoodbye!");
                break;
            }
            if (input.isBlank()) {
                continue;
            }

            Msg userMsg = new UserMessage(input.trim());

            System.out.print("\nBabiAgent: ");
            agent.streamEvents(userMsg)
                    .doOnNext(event -> {
                        if (event instanceof TextBlockDeltaEvent e) {
                            System.out.print(e.getDelta());
                        }
                    })
                    .blockLast();
            System.out.printf("\n[context: %d messages]%n%n",
                    agent.getAgentState().getContext().size());
        }
    }
}
