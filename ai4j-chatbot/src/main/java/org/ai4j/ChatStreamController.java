package org.example;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final ChatModel chatModel;

    public ChatStreamController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter(0L);
        Flux<ChatResponse> flux = chatModel.stream(new Prompt(new UserMessage(message)));
        flux.subscribe(
                r -> {
                    String chunk = r.getResult().getOutput().getText();
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }
}