package org.hongxi.babi.graph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot application entry point for Babi Agent (Graph edition).
 *
 * <p>Provides a web API for the BabiAgent via BabiController,
 * supporting SSE streaming chat endpoints.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DEEPSEEK_API_KEY=your_key  # or DASHSCOPE_API_KEY, depending on application.yml
 *   mvn spring-boot:run -pl babi-graph
 * </pre>
 */
@SpringBootApplication
public class BabiGraphApplication {
    public static void main(String[] args) {
        // Enable DashScope native search if Tavily API key is not set
        if (System.getenv("TAVILY_API_KEY") == null) {
            System.setProperty("babi.dashscope.chat.enable-search", "true");
        }
        SpringApplication.run(BabiGraphApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String accessUrl = "http://localhost:" + port;
            System.out.println("\n========================================");
            System.out.println("Babi Agent (Graph) is ready!");
            System.out.println("Chat with your agent: " + accessUrl);
            System.out.println("========================================\n");
        };
    }
}
