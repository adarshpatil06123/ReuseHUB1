package com.reusehub.rental.dto;

import com.reusehub.rental.RentalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RentalStatusUpdateRequest(
    @NotNull RentalStatus status,
    @Size(max = 1000) String reason
) {}
