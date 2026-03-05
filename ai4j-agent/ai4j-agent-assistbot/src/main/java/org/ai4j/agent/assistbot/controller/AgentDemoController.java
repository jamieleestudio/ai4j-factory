package org.ai4j.agent.assistbot.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/demo")
public class AgentDemoController {

    private final ChatClient chatClient;

    public AgentDemoController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/book-flight")
    public String bookFlight(@RequestParam(value = "request", defaultValue = "Book a flight from Beijing to Shanghai for tomorrow") String request) {
        return chatClient.prompt()
                .user(request)
                .tools("flightBookingService")
                .call()
                .content();
    }
}
