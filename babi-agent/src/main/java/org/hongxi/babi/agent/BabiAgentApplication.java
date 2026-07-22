package org.hongxi.babi.agent;

import org.hongxi.babi.agent.controller.BabiAgentController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot application entry point for the Babi Agent.
 *
 * <p>Provides a web API for the BabiAgent via {@link BabiAgentController},
 * supporting both SSE streaming and synchronous chat endpoints.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DASHSCOPE_API_KEY=your_key
 *   mvn spring-boot:run -pl babi-agent
 * </pre>
 */
@SpringBootApplication
public class BabiAgentApplication {
    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: DASHSCOPE_API_KEY environment variable not set.");
            System.err.println("Get your API key from: https://dashscope.aliyun.com");
            System.err.println("Then set it with: export DASHSCOPE_API_KEY=your_api_key");
            System.exit(1);
        }
        SpringApplication.run(BabiAgentApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            String accessUrl = "http://localhost:" + port + contextPath;
            System.out.println("\n========================================");
            System.out.println("Application is ready!");
            System.out.println("Chat with your agent: " + accessUrl);
            System.out.println("========================================\n");
        };
    }
}
