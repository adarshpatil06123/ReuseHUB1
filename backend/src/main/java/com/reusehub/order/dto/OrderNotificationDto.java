package com.reusehub.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderNotificationDto(
    String notificationType,
    String notificationId,
    UUID orderId,
    Long listingId,
    String listingTitle,
    String actorName,
    String content,
    String status,
    Instant createdAt
) {}
