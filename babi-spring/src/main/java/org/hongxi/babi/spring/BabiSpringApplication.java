package org.hongxi.babi.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot application entry point for the Babi Agent (Spring AI 2.0 edition).
 *
 * <p>Provides a web API for the BabiAgent via SSE streaming,
 * powered by Spring AI 2.0's ChatClient with ToolCallingAdvisor.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DEEPSEEK_API_KEY=your_key
 *   mvn spring-boot:run -pl babi-spring
 * </pre>
 */
@SpringBootApplication
public class BabiSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(BabiSpringApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String accessUrl = "http://localhost:" + port;
            System.out.println("\n========================================");
            System.out.println("Babi Agent (Spring AI 2.0) is ready!");
            System.out.println("Chat with your agent: " + accessUrl);
            System.out.println("========================================\n");
        };
    }
}
