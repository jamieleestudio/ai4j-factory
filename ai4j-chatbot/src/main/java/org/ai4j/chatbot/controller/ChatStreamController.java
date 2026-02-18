package org.ai4j.chatbot.controller;

import org.ai4j.chatbot.service.ChatService;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final ChatService chatService;

    public ChatStreamController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping(value = "/stream/{credentialId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWithCredential(@PathVariable Long credentialId,
                                           @NotBlank @RequestParam String message,
                                           @RequestParam(value = "model", required = false) String modelName) {
        SseEmitter emitter = new SseEmitter(0L);

        Flux<ChatResponse> flux = chatService.streamChat(credentialId, message, modelName);
        flux.subscribe(
                r -> {
                    if (r.getResult() != null && r.getResult().getOutput() != null && r.getResult().getOutput().getText() != null) {
                        String chunk = r.getResult().getOutput().getText();
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter(0L);
        OpenAiApi api = OpenAiApi.builder()
        .baseUrl("https://api.deepseek.com")
        .apiKey("sk-86d534cc5380484290717613678d6b42")
        .build();

        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-chat")
                        .build())
                .build();

        Flux<ChatResponse> flux = model.stream(new Prompt(new UserMessage(message)));
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