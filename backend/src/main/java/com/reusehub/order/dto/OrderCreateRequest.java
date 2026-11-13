package com.reusehub.order.dto;

import com.reusehub.order.OrderType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreateRequest(
    @NotNull Long listingId,
    @NotNull OrderType type,
    BigDecimal offeredPrice,
    BigDecimal finalPrice,
    Long offeredListingId,
    String message,
    Instant expiresAt
) {}
