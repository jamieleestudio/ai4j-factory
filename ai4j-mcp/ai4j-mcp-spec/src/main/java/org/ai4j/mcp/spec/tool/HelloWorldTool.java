package org.ai4j.mcp.spec.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldTool {

    @McpTool(name = "hello", description = "A simple hello world tool that greets the user")
    public String hello(String name) {
        return "Hello, " + (name != null ? name : "World") + "!";
    }
}