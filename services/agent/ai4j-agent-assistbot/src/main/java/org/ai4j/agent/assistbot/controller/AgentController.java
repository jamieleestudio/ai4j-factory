package org.ai4j.agent.assistbot.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private final ChatClient chatClient;

    public AgentController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/agent/chat")
    public String chat(@RequestParam(value = "message", defaultValue = "Hello") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
