package org.ai4j.factory.chat;

import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/stream/{credentialId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWithCredential(@PathVariable Long credentialId,
                                           @NotBlank @RequestParam String message,
                                           @RequestParam(value = "model", required = false) String modelName) {
        SseEmitter emitter = new SseEmitter(0L);

        Flux<ChatResponse> flux = chatService.streamChat(credentialId, message, "deepseek-v4-flash");
        flux.subscribe(
                r -> {
                    if (r.getResult() != null) {
                        if (r.getResult().getOutput().getText() != null) {
                            String chunk = r.getResult().getOutput().getText();
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        }
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }
}
