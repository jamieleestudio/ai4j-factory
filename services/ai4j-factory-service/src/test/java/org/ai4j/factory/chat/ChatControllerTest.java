package org.ai4j.factory.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void streamWithCredentialPassesRequestedModelToChatService() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.streamChat(anyLong(), any(), any(), any())).thenReturn(Flux.<ServerSentEvent<String>>empty());
        ChatController controller = new ChatController(chatService);

        controller.streamWithCredential(1L, "hello", "deepseek-chat", "test-session");

        verify(chatService).streamChat(eq(1L), eq("hello"), eq("deepseek-chat"), eq("test-session"));
    }

    @Test
    void streamWithCredentialAutoGeneratesSessionIdWhenNull() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.streamChat(anyLong(), any(), any(), any())).thenReturn(Flux.<ServerSentEvent<String>>empty());
        ChatController controller = new ChatController(chatService);

        ResponseEntity<Flux<ServerSentEvent<String>>> response = controller.streamWithCredential(1L, "hello", null, null);

        verify(chatService).streamChat(eq(1L), eq("hello"), eq(null), any());
        assertThat(response.getHeaders().get("X-Session-Id")).isNotNull();
        assertThat(response.getHeaders().get("X-Session-Id").get(0)).isNotEmpty();
    }
}
