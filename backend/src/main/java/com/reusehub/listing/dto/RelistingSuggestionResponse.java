package com.reusehub.listing.dto;

import com.reusehub.listing.Listing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record RelistingSuggestionResponse(
    Long listingId,
    String listingTitle,
    BigDecimal currentPrice,
    BigDecimal suggestedPrice,
    Instant inactiveSince,
    String advice
) {
  public static RelistingSuggestionResponse from(Listing listing) {
    BigDecimal suggestedPrice = listing.getPrice() == null || listing.getPrice().signum() <= 0
        ? null
        : listing.getPrice().multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP);
    String advice = suggestedPrice == null
        ? "Refresh the listing title and photos to attract new interest."
        : "Consider a 10% price reduction or refresh the listing to attract new interest.";
    return new RelistingSuggestionResponse(
        listing.getId(),
        listing.getTitle(),
        listing.getPrice(),
        suggestedPrice,
        listing.getUpdatedAt(),
        advice
    );
  }
}
