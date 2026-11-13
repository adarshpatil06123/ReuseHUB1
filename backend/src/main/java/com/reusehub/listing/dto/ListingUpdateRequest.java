package com.reusehub.listing.dto;

import com.reusehub.listing.ItemCondition;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingCategory;
import com.reusehub.listing.ListingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ListingUpdateRequest(
    @NotBlank String title,
    @NotBlank @Size(max = 2000) String description,
    @NotNull ListingCategory category,
    @NotNull ItemCondition condition,
    @NotNull ListingType listingType,
    BigDecimal price,
    @Size(max = 5) List<String> imageUrls
) {
  public Listing toListing() {
    Listing listing = new Listing();
    listing.setTitle(title);
    listing.setDescription(description);
    listing.setCategory(category);
    listing.setCondition(condition);
    listing.setListingType(listingType);
    listing.setPrice(price);
    return listing;
  }
}
