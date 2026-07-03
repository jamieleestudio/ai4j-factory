package org.ai4j.factory.chat;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
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
    public ResponseEntity<Flux<ServerSentEvent<String>>> streamWithCredential(@PathVariable Long credentialId,
                                                                              @NotBlank @RequestParam String message,
                                                                              @RequestParam(value = "model", required = false) String modelName,
                                                                              @RequestParam(value = "sessionId", required = false) String sessionId) {
        String resolvedSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore().mustRevalidate().sMaxAge(0, TimeUnit.SECONDS))
                .header("X-Accel-Buffering", "no")
                .header("Connection", "keep-alive")
                .header("X-Session-Id", resolvedSessionId)
                .body(chatService.streamChat(credentialId, message, modelName, resolvedSessionId));
    }
}
