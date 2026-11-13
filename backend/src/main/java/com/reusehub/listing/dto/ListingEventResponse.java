package com.reusehub.listing.dto;

public record ListingEventResponse(
    String action,
    Long listingId,
    ListingResponse listing
) {}
