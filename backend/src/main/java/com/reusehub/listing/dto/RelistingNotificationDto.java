package com.reusehub.listing.dto;

import java.time.Instant;

public record RelistingNotificationDto(
    String notificationType,
    String notificationId,
    Long listingId,
    String listingTitle,
    String actorName,
    String content,
    Instant createdAt
) {}
