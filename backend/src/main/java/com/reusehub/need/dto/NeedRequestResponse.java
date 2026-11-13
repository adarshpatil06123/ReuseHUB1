package com.reusehub.need.dto;

import com.reusehub.need.NeedRequest;
import com.reusehub.listing.ItemCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NeedRequestResponse(
    UUID id,
    String keywords,
    String category,
    String listingType,
    BigDecimal maxPrice,
    String details,
    ItemCondition minimumCondition,
    String preferredLocation,
    String neededBy,
    boolean showOnHeatmap,
    boolean acceptDonations,
    String referenceImageUrl,
    boolean active,
    Instant createdAt,
    List<NeedMatchResponse> matches
) {
  public static NeedRequestResponse from(NeedRequest needRequest, List<NeedMatchResponse> matches) {
    return new NeedRequestResponse(
        needRequest.getId(),
        needRequest.getKeywords(),
        needRequest.getCategory() == null ? null : needRequest.getCategory().name(),
        needRequest.getListingType() == null ? null : needRequest.getListingType().name(),
        needRequest.getMaxPrice(),
        needRequest.getDetails(),
        needRequest.getMinimumCondition(),
        needRequest.getPreferredLocation() == null || needRequest.getPreferredLocation().isBlank()
            ? "North Campus" : needRequest.getPreferredLocation(),
        needRequest.getNeededBy(),
        needRequest.isShowOnHeatmap(),
        needRequest.isAcceptDonations(),
        needRequest.getReferenceImageUrl(),
        needRequest.isActive(),
        needRequest.getCreatedAt(),
        matches
    );
  }
}
