package com.reusehub.chat;

import com.reusehub.chat.dto.ChatMessageDto;
import com.reusehub.chat.dto.ChatThreadSummaryResponse;
import com.reusehub.common.ApiException;
import com.reusehub.listing.ListingRepository;
import com.reusehub.user.UserRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
  private final ChatThreadRepository chatThreadRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final BlockedUserRepository blockedUserRepository;
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;

  public ChatService(
      ChatThreadRepository chatThreadRepository,
      ChatMessageRepository chatMessageRepository,
      BlockedUserRepository blockedUserRepository,
      ListingRepository listingRepository,
      UserRepository userRepository
  ) {
    this.chatThreadRepository = chatThreadRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.blockedUserRepository = blockedUserRepository;
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public ChatThread getOrCreateThread(Long listingId, Long userId, Long otherUserId) {
    ensureNotBlocked(userId, otherUserId);
    if (listingId != 0 && !listingRepository.existsById(listingId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Listing not found");
    }

    long low = Math.min(userId, otherUserId);
    long high = Math.max(userId, otherUserId);
    String threadKey = listingId + "-" + low + "-" + high;

    return chatThreadRepository.findByThreadKey(threadKey)
        .orElseGet(() -> {
          ChatThread thread = new ChatThread();
          thread.setListingId(listingId);
          thread.setParticipantLowId(low);
          thread.setParticipantHighId(high);
          thread.setThreadKey(threadKey);
          return chatThreadRepository.save(thread);
        });
  }

  @Transactional
  public List<ChatMessageDto> getMessages(ChatThread thread) {
    String threadKey = thread.getThreadKey();
    return chatMessageRepository.findByThreadOrderByCreatedAtAsc(thread)
        .stream()
        .map(msg -> new ChatMessageDto(
            msg.getId(),
            threadKey,
            msg.getSenderId(),
            msg.getContent(),
            msg.getCreatedAt()
        ))
        .toList();
  }

  @Transactional
  public List<ChatMessageDto> getMessagesByThreadKey(String threadKey, Long userId) {
    var threadOpt = chatThreadRepository.findByThreadKey(threadKey);
    if (threadOpt.isEmpty()) {
      return List.of();
    }
    ChatThread thread = threadOpt.get();
    if (!thread.getParticipantLowId().equals(userId) && !thread.getParticipantHighId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
    }
    Long otherUserId = thread.getParticipantLowId().equals(userId)
        ? thread.getParticipantHighId()
        : thread.getParticipantLowId();
    ensureNotBlocked(userId, otherUserId);
    return getMessages(thread);
  }

  @Transactional
  public void clearMessages(String threadKey, Long userId) {
    ChatThread thread = requireParticipantThread(threadKey, userId);
    chatMessageRepository.deleteByThread(thread);
    thread.setUpdatedAt(Instant.now());
    chatThreadRepository.save(thread);
  }

  @Transactional
  public void deleteChat(String threadKey, Long userId) {
    ChatThread thread = requireParticipantThread(threadKey, userId);
    chatMessageRepository.deleteByThread(thread);
    chatThreadRepository.delete(thread);
  }

  @Transactional
  public void blockParticipant(String threadKey, Long userId) {
    ChatThread thread = requireParticipantThread(threadKey, userId);
    Long blockedUserId = thread.getParticipantLowId().equals(userId)
        ? thread.getParticipantHighId()
        : thread.getParticipantLowId();
    if (!blockedUserRepository.existsByBlockerIdAndBlockedUserId(userId, blockedUserId)) {
      BlockedUser blockedUser = new BlockedUser();
      blockedUser.setBlockerId(userId);
      blockedUser.setBlockedUserId(blockedUserId);
      blockedUserRepository.save(blockedUser);
    }
  }

  @Transactional
  public ChatMessageDto saveMessage(ChatThread thread, Long senderId, String content) {
    ChatMessage message = new ChatMessage();
    message.setThread(thread);
    message.setSenderId(senderId);
    message.setContent(content);
    ChatMessage saved = chatMessageRepository.save(message);
    thread.setUpdatedAt(Instant.now());
    chatThreadRepository.save(thread);
    return toDto(saved);
  }

  public List<ChatThreadSummaryResponse> getThreadSummaries(Long userId) {
    return chatThreadRepository.findByParticipantLowIdOrParticipantHighId(userId, userId)
        .stream()
        .filter(thread -> {
          Long otherUserId = thread.getParticipantLowId().equals(userId)
              ? thread.getParticipantHighId()
              : thread.getParticipantLowId();
          return !isBlocked(userId, otherUserId);
        })
        .map(thread -> {
          Long otherUserId = thread.getParticipantLowId().equals(userId)
              ? thread.getParticipantHighId()
              : thread.getParticipantLowId();
          var otherUser = userRepository.findById(otherUserId).orElse(null);
          var lastMessage = chatMessageRepository.findTopByThreadOrderByCreatedAtDesc(thread)
              .orElse(null);
          Instant lastAt = lastMessage != null ? lastMessage.getCreatedAt() : thread.getUpdatedAt();
          return new ChatThreadSummaryResponse(
              thread.getThreadKey(),
              thread.getListingId(),
              otherUserId,
              otherUser != null ? otherUser.getName() : "Unknown",
              otherUser != null ? otherUser.getProfileImageUrl() : null,
              lastMessage != null ? lastMessage.getContent() : "No messages yet",
              lastAt
          );
        })
        .sorted(Comparator.comparing(ChatThreadSummaryResponse::lastMessageAt,
            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .toList();
  }

  private ChatThread requireParticipantThread(String threadKey, Long userId) {
    ChatThread thread = chatThreadRepository.findByThreadKey(threadKey)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
    if (!thread.getParticipantLowId().equals(userId) && !thread.getParticipantHighId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
    }
    return thread;
  }

  private void ensureNotBlocked(Long userId, Long otherUserId) {
    if (isBlocked(userId, otherUserId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Messaging is unavailable for this conversation");
    }
  }

  private boolean isBlocked(Long userId, Long otherUserId) {
    return blockedUserRepository.existsByBlockerIdAndBlockedUserId(userId, otherUserId)
        || blockedUserRepository.existsByBlockerIdAndBlockedUserId(otherUserId, userId);
  }

  public com.reusehub.chat.dto.ChatNotificationDto buildNotification(ChatThread thread, Long senderId, ChatMessageDto message) {
    var sender = userRepository.findById(senderId).orElse(null);
    var listing = listingRepository.findById(thread.getListingId()).orElse(null);
    return new com.reusehub.chat.dto.ChatNotificationDto(
        message.id(),
        thread.getThreadKey(),
        thread.getListingId(),
        listing != null ? listing.getTitle() : "Direct message",
        senderId,
        sender != null ? sender.getName() : "Someone",
        sender != null ? sender.getProfileImageUrl() : null,
        message.content(),
        message.createdAt()
    );
  }

  private ChatMessageDto toDto(ChatMessage message) {
    return new ChatMessageDto(
        message.getId(),
        message.getThread().getThreadKey(),
        message.getSenderId(),
        message.getContent(),
        message.getCreatedAt()
    );
  }
}
