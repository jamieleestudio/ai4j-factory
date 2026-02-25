package org.ai4j.mcp.spec.tool;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class MarkdownSpecResources {

    @McpResource(
            uri = "spec://{name}",
            name = "AI4J MCP Markdown Spec Resource",
            description = "Read AI4J MCP specification markdown documents"
    )
    public ReadResourceResult readSpec(String name) throws IOException {
        // 简单防护，避免 path traversal
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return errorResource("spec://" + name, "INVALID_SPEC_NAME", "Invalid resource name");
        }

        String path = "spec/" + name + ".md";
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            return errorResource("spec://" + name, "SPEC_NOT_FOUND",
                    "No markdown spec found: " + path);
        }

        String markdown = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        return new ReadResourceResult(List.of(
                new TextResourceContents(
                        "spec://" + name,
                        "text/markdown",
                        markdown
                )
        ));
    }

    private ReadResourceResult errorResource(String uri, String code, String message) {
        String json = """
                {
                  "code": "%s",
                  "message": "%s"
                }
                """.formatted(code, message);

        return new ReadResourceResult(List.of(
                new TextResourceContents(uri, "application/json", json)
        ));
    }
}