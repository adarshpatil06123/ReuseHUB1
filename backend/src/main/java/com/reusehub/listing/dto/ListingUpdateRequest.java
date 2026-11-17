package com.reusehub.listing.dto;

import com.reusehub.listing.ItemCondition;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingCategory;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.RentalPricingPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

public record ListingUpdateRequest(
    @NotBlank String title,
    @NotBlank @Size(max = 2000) String description,
    @NotNull ListingCategory category,
    @NotNull ItemCondition condition,
    @NotNull ListingType listingType,
    BigDecimal price,
    @Size(max = 5) List<String> imageUrls,
    RentalPricingPeriod rentalPricingPeriod,
    BigDecimal securityDeposit,
    LocalDate rentalAvailableFrom,
    LocalDate rentalAvailableUntil,
    Integer minimumRentalDays,
    Integer maximumRentalDays,
    @Size(max = 1000) String rentalInstructions
) {
  public Listing toListing() {
    Listing listing = new Listing();
    listing.setTitle(title);
    listing.setDescription(description);
    listing.setCategory(category);
    listing.setCondition(condition);
    listing.setListingType(listingType);
    listing.setPrice(price);
    listing.setRentalPricingPeriod(rentalPricingPeriod);
    listing.setSecurityDeposit(securityDeposit);
    listing.setRentalAvailableFrom(rentalAvailableFrom);
    listing.setRentalAvailableUntil(rentalAvailableUntil);
    listing.setMinimumRentalDays(minimumRentalDays);
    listing.setMaximumRentalDays(maximumRentalDays);
    listing.setRentalInstructions(rentalInstructions);
    return listing;
  }
}
