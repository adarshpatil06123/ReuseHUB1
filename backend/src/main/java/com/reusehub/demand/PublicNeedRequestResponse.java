package com.reusehub.demand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicNeedRequestResponse(
    UUID id,
    Long userId,
    String name,
    String keywords,
    String preferredLocation,
    BigDecimal maxPrice,
    String category,
    Instant createdAt
) {}
