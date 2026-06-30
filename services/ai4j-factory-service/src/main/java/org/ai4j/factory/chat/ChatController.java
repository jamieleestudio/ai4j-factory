package org.ai4j.factory.chat;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/stream/{credentialId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWithCredential(@PathVariable Long credentialId,
                                              @NotBlank @RequestParam String message,
                                              @RequestParam(value = "model", required = false) String modelName) {
        return chatService.streamChat(credentialId, message, modelName);
    }
}
