package com.reusehub.listing.dto;

import com.reusehub.listing.ListingStatus;
import jakarta.validation.constraints.NotNull;

public record ListingStatusUpdateRequest(@NotNull ListingStatus status) {}
