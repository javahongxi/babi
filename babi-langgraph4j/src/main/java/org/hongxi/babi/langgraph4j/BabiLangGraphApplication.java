package org.hongxi.babi.langgraph4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot application entry point for Babi Agent (LangGraph4J edition).
 *
 * <p>Provides a web API for the BabiAgent via BabiController,
 * supporting SSE streaming chat endpoints.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DASHSCOPE_API_KEY=your_key
 *   mvn spring-boot:run -pl babi-langgraph4j
 * </pre>
 */
@SpringBootApplication
public class BabiLangGraphApplication {

    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: DASHSCOPE_API_KEY environment variable not set.");
            System.err.println("Get your API key from: https://dashscope.aliyun.com");
            System.err.println("Then set it with: export DASHSCOPE_API_KEY=your_api_key");
            System.exit(1);
        }
        SpringApplication.run(BabiLangGraphApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String accessUrl = "http://localhost:" + port;
            System.out.println("\n========================================");
            System.out.println("Babi Agent (LangGraph4J) is ready!");
            System.out.println("Chat with your agent: " + accessUrl);
            System.out.println("========================================\n");
        };
    }
}
