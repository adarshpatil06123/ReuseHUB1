package com.reusehub.chat.dto;

import java.time.Instant;

public record ChatMessageDto(
    Long id,
    String threadKey,
    Long senderId,
    String content,
    Instant createdAt
) {}
