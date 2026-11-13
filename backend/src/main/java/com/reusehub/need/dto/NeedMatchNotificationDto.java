package com.reusehub.need.dto;

import java.time.Instant;

public record NeedMatchNotificationDto(
    String notificationType,
    String notificationId,
    Long listingId,
    String listingTitle,
    String actorName,
    String content,
    Instant createdAt
) {}
