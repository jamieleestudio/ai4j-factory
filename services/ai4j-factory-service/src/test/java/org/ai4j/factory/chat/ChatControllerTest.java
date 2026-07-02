package org.ai4j.factory.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void streamWithCredentialPassesRequestedModelToChatService() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.streamChat(anyLong(), any(), any(), any())).thenReturn(Flux.<ServerSentEvent<String>>empty());
        ChatController controller = new ChatController(chatService);

        controller.streamWithCredential(1L, "hello", "deepseek-chat", null);

        verify(chatService).streamChat(1L, "hello", "deepseek-chat", null);
    }
}
