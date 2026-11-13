package com.reusehub.chat;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.chat.dto.ChatThreadSummaryResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/chats")
public class ChatRestController {
  private final ChatService chatService;

  public ChatRestController(ChatService chatService) {
    this.chatService = chatService;
  }

  @GetMapping
  public List<ChatThreadSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
    return chatService.getThreadSummaries(principal.getUserId());
  }

  @GetMapping("/{threadKey}/messages")
  public List<com.reusehub.chat.dto.ChatMessageDto> getMessages(
      @PathVariable String threadKey,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    return chatService.getMessagesByThreadKey(threadKey, principal.getUserId());
  }

  @DeleteMapping("/{threadKey}/messages")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearMessages(
      @PathVariable String threadKey,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    chatService.clearMessages(threadKey, principal.getUserId());
  }

  @DeleteMapping("/{threadKey}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteChat(
      @PathVariable String threadKey,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    chatService.deleteChat(threadKey, principal.getUserId());
  }

  @PostMapping("/{threadKey}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void blockParticipant(
      @PathVariable String threadKey,
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    chatService.blockParticipant(threadKey, principal.getUserId());
  }
}
