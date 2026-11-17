package com.reusehub.rental.dto;

import com.reusehub.listing.RentalPricingPeriod;
import com.reusehub.rental.RentalRequest;
import com.reusehub.rental.RentalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RentalResponse(
    UUID id,
    Long listingId,
    String listingTitle,
    String listingImageUrl,
    String location,
    Long renterId,
    String renterName,
    Long ownerId,
    String ownerName,
    String role,
    LocalDate startDate,
    LocalDate endDate,
    long rentalDays,
    BigDecimal dailyRate,
    RentalPricingPeriod pricingPeriod,
    BigDecimal rentalAmount,
    BigDecimal securityDeposit,
    BigDecimal totalEstimate,
    String message,
    RentalStatus status,
    String statusReason,
    Instant expiresAt,
    Instant ownerHandoverConfirmedAt,
    Instant renterHandoverConfirmedAt,
    Instant returnRequestedAt,
    Instant returnedAt,
    Instant completedAt,
    Instant createdAt,
    List<RentalHistoryResponse> statusHistory
) {
  public static RentalResponse from(RentalRequest rental, Long viewerId) {
    var listing = rental.getListing();
    var imageUrl = listing.getImages().isEmpty() ? null : listing.getImages().get(0).getImageUrl();
    return new RentalResponse(
        rental.getId(),
        listing.getId(),
        listing.getTitle(),
        imageUrl,
        listing.getLocation(),
        rental.getRenter().getId(),
        rental.getRenter().getName(),
        rental.getOwner().getId(),
        rental.getOwner().getName(),
        rental.getRenter().getId().equals(viewerId) ? "RENTER" : "OWNER",
        rental.getStartDate(),
        rental.getEndDate(),
        java.time.temporal.ChronoUnit.DAYS.between(rental.getStartDate(), rental.getEndDate()) + 1,
        rental.getDailyRate(),
        rental.getPricingPeriod(),
        rental.getRentalAmount(),
        rental.getSecurityDeposit(),
        rental.getRentalAmount().add(rental.getSecurityDeposit()),
        rental.getMessage(),
        rental.getStatus(),
        rental.getStatusReason(),
        rental.getExpiresAt(),
        rental.getOwnerHandoverConfirmedAt(),
        rental.getRenterHandoverConfirmedAt(),
        rental.getReturnRequestedAt(),
        rental.getReturnedAt(),
        rental.getCompletedAt(),
        rental.getCreatedAt(),
        rental.getStatusHistory().stream().map(RentalHistoryResponse::from).toList()
    );
  }
}
