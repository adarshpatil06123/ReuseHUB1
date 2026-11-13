package com.reusehub.order.dto;

import com.reusehub.listing.Listing;
import com.reusehub.order.Order;
import com.reusehub.order.OrderStatus;
import com.reusehub.order.OrderType;
import com.reusehub.order.CollateralStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    Long listingId,
    String listingTitle,
    String listingCategory,
    String listingImageUrl,
    OrderType type,
    OrderStatus status,
    BigDecimal offeredPrice,
    BigDecimal finalPrice,
    Long offeredListingId,
    String offeredListingTitle,
    String offeredListingImageUrl,
    String message,
    String role,
    String counterpartyName,
    Instant createdAt,
    Instant updatedAt,
    Instant handoffDeadlineAt,
    Instant buyerHandoffConfirmedAt,
    Instant sellerHandoffConfirmedAt,
    BigDecimal collateralAmount,
    CollateralStatus collateralStatus,
    Instant collateralLockedAt,
    Instant collateralSettledAt,
    UUID bundleId,
    Integer bundlePosition,
    Integer bundleSize
) {
  public static OrderResponse from(Order order, Long viewerId) {
    return from(order, viewerId, null);
  }

  public static OrderResponse from(Order order, Long viewerId, Listing offeredListing) {
    Listing listing = order.getListing();
    String listingImageUrl = (listing.getImages() != null && !listing.getImages().isEmpty())
        ? listing.getImages().get(0).getImageUrl()
        : null;
    boolean isBuyer = order.getBuyer().getId().equals(viewerId);
    String role = isBuyer ? "BUYER" : "SELLER";
    String counterpartyName = isBuyer
        ? order.getSeller().getName()
        : order.getBuyer().getName();

    String offeredTitle = offeredListing != null ? offeredListing.getTitle() : null;
    String offeredImageUrl = null;
    if (offeredListing != null && offeredListing.getImages() != null && !offeredListing.getImages().isEmpty()) {
      offeredImageUrl = offeredListing.getImages().get(0).getImageUrl();
    }

    return new OrderResponse(
        order.getId(),
        listing.getId(),
        listing.getTitle(),
        listing.getCategory() != null ? listing.getCategory().name() : "",
        listingImageUrl,
        order.getType(),
        order.getStatus(),
        order.getOfferedPrice(),
        order.getFinalPrice(),
        order.getOfferedListingId(),
        offeredTitle,
        offeredImageUrl,
        order.getMessage(),
        role,
        counterpartyName,
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getHandoffDeadlineAt(),
        order.getBuyerHandoffConfirmedAt(),
        order.getSellerHandoffConfirmedAt(),
        order.getCollateralAmount(),
        order.getCollateralStatus(),
        order.getCollateralLockedAt(),
        order.getCollateralSettledAt(),
        order.getBundleId(),
        order.getBundlePosition(),
        order.getBundleSize()
    );
  }
}
