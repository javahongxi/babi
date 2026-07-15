package org.hongxi.babi.codingagent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import org.hongxi.babi.codingagent.tool.FetchUrlTool;
import org.hongxi.babi.codingagent.tool.FileReadTool;
import org.hongxi.babi.codingagent.tool.GitHubApiTool;
import org.hongxi.babi.codingagent.tool.HttpRequestTool;
import org.hongxi.babi.codingagent.tool.ShellCommandTool;
import org.hongxi.babi.codingagent.tool.WebSearchTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.hongxi.babi.codingagent.AgentConstants.SYSTEM_PROMPT;

/**
 * CLI entry point for the Coding Agent.
 *
 * <p>Provides an interactive console for chatting with the coding agent.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DASHSCOPE_API_KEY=your_key
 *   mvn exec:java -pl babi-codingagent
 * </pre>
 */
public class CodingAgentCli {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: DASHSCOPE_API_KEY environment variable not set.");
            System.err.println("Get your API key from: https://dashscope.aliyun.com");
            System.err.println("Then set it with: export DASHSCOPE_API_KEY=your_api_key");
            System.exit(1);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Babi Coding Agent - Powered by AgentScope Java");
        System.out.println("=".repeat(60));
        System.out.println("An AI coding assistant with file reading and shell tools.");
        System.out.println("Type 'exit' to quit.\n");

        // Build agent using the core API (no Spring context needed for CLI)
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FileReadTool());
        toolkit.registerTool(new ShellCommandTool());
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new WebSearchTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());

        ReActAgent agent = ReActAgent.builder()
                .name("BabiCodingAgent")
                .sysPrompt(SYSTEM_PROMPT)
                .model("dashscope:qwen-plus")
                .toolkit(toolkit)
                .maxIters(20)
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

            System.out.print("\nBabiCodingAgent: ");
            agent.streamEvents(userMsg)
                    .doOnNext(event -> {
                        if (event instanceof TextBlockDeltaEvent e) {
                            System.out.print(e.getDelta());
                        }
                    })
                    .blockLast();
            System.out.println("\n");
        }
    }
}
