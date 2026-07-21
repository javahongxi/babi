package org.hongxi.babi.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.SkillTool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * CLI entry point for the Babi Agent.
 *
 * <p>Uses {@link HarnessAgent} for workspace-based context management,
 * built-in filesystem/shell tools, and session persistence.
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
        Path workspacePath = Path.of(workspace);
        // Ensure workspace directory exists
        Files.createDirectories(workspacePath);

        // Initialize AGENTS.md in workspace if not present
        initAgentsMd(workspacePath);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Babi Agent - Powered by AgentScope Java (HarnessAgent)");
        System.out.println("=".repeat(60));
        System.out.println("Workspace: " + workspace);
        System.out.println("Built-in tools: read_file, write_file, edit_file, grep_files, execute");
        System.out.println("Custom tools: fetch_url, http_request, github_api_request, list_skills, use_skill");
        System.out.println("Type 'exit' to quit.\n");

        // Register babi-specific custom tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new FetchUrlTool());
        toolkit.registerTool(new HttpRequestTool());
        toolkit.registerTool(new GitHubApiTool());
        SkillTool skillTool = new SkillTool();
        toolkit.registerTool(skillTool);

        // Build system prompt with skills info
        String sysPrompt = SystemPromptBuilder.build(skillTool.getSkills().values());

        // Build HarnessAgent (auto-creates session store at ~/.agentscope/state/BabiAgent/)
        HarnessAgent agent = HarnessAgent.builder()
                .name(AgentConstants.AGENT_NAME)
                .sysPrompt(sysPrompt)
                .model(AgentConstants.createModel())
                .toolkit(toolkit)
                .workspace(workspacePath)
                .filesystem(new LocalFilesystemSpec().project(workspacePath))
                .maxIters(20)
                .enableTaskList()
                .disableDynamicSkills()
                .disableMemoryTools()
                .disableCompaction()           // We have our own ContextTruncateMiddleware
                .disableToolResultEviction()   // Not needed — keep tool results in context
                .middleware(new ContextTruncateMiddleware(30))
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("cli-default")
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
            agent.streamEvents(userMsg, ctx)
                    .doOnNext(event -> {
                        if (event instanceof TextBlockDeltaEvent e) {
                            System.out.print(e.getDelta());
                        }
                    })
                    .blockLast();
            System.out.printf("%n%n");
        }
    }

    /**
     * Initialize AGENTS.md in the workspace if it doesn't exist.
     */
    private static void initAgentsMd(Path workspacePath) {
        Path agentsMd = workspacePath.resolve("AGENTS.md");
        if (Files.exists(agentsMd)) {
            return;
        }
        try {
            // Try loading from classpath resource
            try (InputStream is = BabiAgentCli.class.getResourceAsStream("/workspace/AGENTS.md")) {
                if (is != null) {
                    Files.copy(is, agentsMd, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
            // Fallback: create a minimal AGENTS.md
            Files.writeString(agentsMd, """
                    # BabiAgent
                    
                    You are BabiAgent, an expert coding assistant powered by AgentScope Java.
                    
                    ## Rules
                    
                    - When the user provides a URL, ALWAYS call fetch_url FIRST
                    - For GitHub URLs, use github_api_request (NOT fetch_url)
                    - NEVER fabricate content from resources you have not accessed
                    - Be cautious with destructive commands
                    """);
        } catch (Exception e) {
            System.err.println("Warning: Failed to create AGENTS.md: " + e.getMessage());
        }
    }
}
