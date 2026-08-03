package org.hongxi.babi.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import org.hongxi.babi.agent.middleware.ContextTruncateMiddleware;
import org.hongxi.babi.common.prompt.CodingSystemPrompt;
import org.hongxi.babi.agent.tool.FetchUrlTool;
import org.hongxi.babi.agent.tool.GitHubApiTool;
import org.hongxi.babi.agent.tool.HttpRequestTool;
import org.hongxi.babi.agent.tool.SkillTool;
import org.hongxi.babi.common.util.AgentUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

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

        // Parse --workspace argument; default to current working directory
        String rawWorkspace = System.getProperty("user.dir");
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i]) && i + 1 < args.length) {
                rawWorkspace = args[i + 1];
                break;
            }
        }
        String workspace = AgentUtils.resolveWorkspace(rawWorkspace);
        Path workspacePath = Path.of(workspace);
        // Ensure workspace directory exists
        Files.createDirectories(workspacePath);

        // Initialize AGENTS.md in workspace if not present
        AgentUtils.initAgentsMd(workspacePath);

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
        SkillTool skillTool = new SkillTool(workspacePath);
        toolkit.registerTool(skillTool);

        // Build system prompt with workspace info and skills
        String sysPrompt = CodingSystemPrompt.build(workspace, skillTool.getSkills().values(), true);

        // Build HarnessAgent (auto-creates session store at ~/.agentscope/state/BabiAgent/)
        String modelName = System.getenv().getOrDefault("BABI_MODEL_NAME", "qwen-plus");
        String fallbackModel = System.getenv().getOrDefault("BABI_FALLBACK_MODEL", "qwen-turbo");
        HarnessAgent agent = HarnessAgent.builder()
                .name(AgentUtils.AGENT_NAME)
                .sysPrompt(sysPrompt)
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .stream(true)
                        .enableSearch(true)
                        .build())
                .toolkit(toolkit)
                .workspace(workspacePath)
                .filesystem(new LocalFilesystemSpec()
                        .project(workspacePath)
                        // Default ROOTED mode restricts file access to workspace only;
                        // UNRESTRICTED allows reading/writing any local file path
                        .mode(LocalFsMode.UNRESTRICTED))
                .maxIters(20)
                .maxRetries(2)              // Tool calls retry up to 2 times on failure
                .fallbackModel(fallbackModel)  // Auto-fallback when primary model is unavailable
                .enableTaskList()
                .enablePlanMode()            // Plan mode: investigate first, then execute
                .allowShellInPlanMode()      // Allow build/test commands in plan mode
                .disableDynamicSkills()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableCompaction()           // We have our own ContextTruncateMiddleware
                .disableToolResultEviction()   // Not needed — keep tool results in context
                .enableAgentTracingLog(false)  // Disable AgentTraceMiddleware for performance
                .middleware(new ContextTruncateMiddleware(30))
                .build();

        // Use workspace-based session ID so different workspaces have isolated conversation history
        String sessionId = "cli-" + Integer.toHexString(workspace.hashCode());
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
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
}
