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
 *   export DEEPSEEK_API_KEY=your_key  # or DASHSCOPE_API_KEY, depending on application.yml
 *   mvn spring-boot:run -pl babi-langgraph4j
 * </pre>
 */
@SpringBootApplication
public class BabiLangGraphApplication {
    public static void main(String[] args) {
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
