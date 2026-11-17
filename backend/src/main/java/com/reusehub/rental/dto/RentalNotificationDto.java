package com.reusehub.rental.dto;

import java.time.Instant;
import java.util.UUID;

public record RentalNotificationDto(
    String notificationType,
    String notificationId,
    UUID rentalId,
    Long listingId,
    String listingTitle,
    String actorName,
    String content,
    String status,
    Instant createdAt
) {}
