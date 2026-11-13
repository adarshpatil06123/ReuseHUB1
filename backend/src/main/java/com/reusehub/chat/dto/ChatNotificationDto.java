package com.reusehub.chat.dto;

import java.time.Instant;

public record ChatNotificationDto(
    Long messageId,
    String threadKey,
    Long listingId,
    String listingTitle,
    Long senderId,
    String senderName,
    String senderAvatar,
    String content,
    Instant createdAt
) {}
