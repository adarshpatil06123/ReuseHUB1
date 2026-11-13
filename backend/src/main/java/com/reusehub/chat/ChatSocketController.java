package com.reusehub.chat;

import com.reusehub.chat.dto.ChatHistoryResponse;
import com.reusehub.chat.dto.ChatJoinRequest;
import com.reusehub.chat.dto.ChatMessageDto;
import com.reusehub.chat.dto.ChatSendRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
public class ChatSocketController {
  private final ChatService chatService;
  private final SimpMessagingTemplate messagingTemplate;

  public ChatSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
    this.chatService = chatService;
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping("/chat.join")
  public void join(@Valid ChatJoinRequest request) {
    ChatThread thread = chatService.getOrCreateThread(
        request.listingId(),
        request.userId(),
        request.otherUserId()
    );
    ChatHistoryResponse response = new ChatHistoryResponse(
        thread.getThreadKey(),
        chatService.getMessages(thread)
    );
    messagingTemplate.convertAndSend(
        "/topic/chats/" + thread.getThreadKey() + "/history",
        response
    );
  }

  @MessageMapping("/chat.send")
  public void send(@Valid ChatSendRequest request) {
    ChatThread thread = chatService.getOrCreateThread(
        request.listingId(),
        request.userId(),
        request.otherUserId()
    );
    ChatMessageDto message = chatService.saveMessage(
        thread,
        request.userId(),
        request.content().trim()
    );
    // Broadcast message to thread subscribers
    messagingTemplate.convertAndSend(
        "/topic/chats/" + message.threadKey() + "/messages",
        message
    );

    // Broadcast toast / popup notification to recipient
    com.reusehub.chat.dto.ChatNotificationDto notification =
        chatService.buildNotification(thread, request.userId(), message);
    messagingTemplate.convertAndSend(
        "/topic/users/" + request.otherUserId() + "/notifications",
        notification
    );
  }
}
