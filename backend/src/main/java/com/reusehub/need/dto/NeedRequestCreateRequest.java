package com.reusehub.need.dto;

import com.reusehub.listing.ListingCategory;
import com.reusehub.listing.ItemCondition;
import com.reusehub.listing.ListingType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record NeedRequestCreateRequest(
    @NotBlank @Size(max = 120) String keywords,
    ListingCategory category,
    ListingType listingType,
    @DecimalMin(value = "0.01", message = "Maximum price must be greater than zero") BigDecimal maxPrice,
    @Size(max = 2000) String details,
    ItemCondition minimumCondition,
    @Size(max = 120) String preferredLocation,
    @Pattern(regexp = "TODAY|THIS_WEEK|ANYTIME", message = "Choose a valid need timeline") String neededBy,
    Boolean showOnHeatmap,
    Boolean acceptDonations,
    @Size(max = 500) String referenceImageUrl,
    Boolean active
) {}
