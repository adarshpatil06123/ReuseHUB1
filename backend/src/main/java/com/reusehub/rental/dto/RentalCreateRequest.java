package com.reusehub.rental.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RentalCreateRequest(
    @NotNull Long listingId,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @Size(max = 1000) String message
) {}
