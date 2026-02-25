package org.ai4j.mcp.spec.tool;

import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;

@Service
public class TimeTool {

    @McpTool(name = "getCurrentTime", description = "Get the time of current server.")
    public String  getCityTimeMethod() {
        return "Current time of server is " + System.currentTimeMillis();
    }

    @McpResource(
            uri = "spec://index",
            name = "Spec Index",
            description = "List available specs"
    )
    public String index() {
        return """
               # Spec Index
               - spec://overview
               - spec://project-structure
               """;
    }

}
