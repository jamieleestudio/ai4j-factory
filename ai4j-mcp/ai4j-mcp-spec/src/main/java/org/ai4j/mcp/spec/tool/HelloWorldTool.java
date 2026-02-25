package org.ai4j.mcp.spec.tool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.util.function.Function;

@Configuration
public class HelloWorldTool {

    public record HelloRequest(String name) {}

    @Bean
    @Description("A simple hello world tool that greets the user")
    public Function<HelloRequest, String> hello() {
        return request -> "Hello, " + (request.name() != null ? request.name() : "World") + "!";
    }
}
