package com.reusehub.wishlist.dto;

import com.reusehub.listing.dto.ListingResponse;
import com.reusehub.wishlist.WishlistItem;
import java.time.Instant;

public record WishlistItemResponse(
    Long id,
    ListingResponse listing,
    Instant savedAt
) {
  public static WishlistItemResponse from(WishlistItem item) {
    return new WishlistItemResponse(
        item.getId(),
        ListingResponse.from(item.getListing()),
        item.getCreatedAt()
    );
  }
}
