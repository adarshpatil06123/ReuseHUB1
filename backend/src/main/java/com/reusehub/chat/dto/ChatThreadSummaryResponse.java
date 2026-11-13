package com.reusehub.chat.dto;

import java.time.Instant;

public record ChatThreadSummaryResponse(
    String threadKey,
    Long listingId,
    Long otherUserId,
    String otherUserName,
    String otherUserAvatar,
    String lastMessage,
    Instant lastMessageAt
) {}
