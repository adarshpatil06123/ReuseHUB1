package com.reusehub.need.dto;

import com.reusehub.listing.dto.ListingResponse;
import java.time.Instant;
import java.util.UUID;

public record NeedMatchResponse(
    UUID id,
    ListingResponse listing,
    Instant matchedAt
) {}
