package com.reusehub.order.dto;

import com.reusehub.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderStatusUpdateRequest(
    @NotNull OrderStatus status,
    BigDecimal finalPrice,
    Instant completedAt
) {}
