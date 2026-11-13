package com.reusehub.listing.dto;

import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingCategory;
import com.reusehub.listing.ListingStatus;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.ItemCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ListingResponse(
    Long id,
    Long userId,
    String title,
    String description,
    ListingCategory category,
    ItemCondition condition,
    ListingType listingType,
    BigDecimal price,
    String location,
    ListingStatus status,
    List<String> imageUrls,
    Instant createdAt
) {
  public static ListingResponse from(Listing listing) {
    return new ListingResponse(
        listing.getId(),
        listing.getUser().getId(),
        listing.getTitle(),
        listing.getDescription(),
        listing.getCategory(),
        listing.getCondition(),
        listing.getListingType(),
        listing.getPrice(),
        listing.getLocation() != null ? listing.getLocation() : "North Campus",
        listing.getStatus(),
        listing.getImages().stream().map(img -> img.getImageUrl()).toList(),
        listing.getCreatedAt()
    );
  }
}
