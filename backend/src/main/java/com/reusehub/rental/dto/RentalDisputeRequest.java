package com.reusehub.rental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RentalDisputeRequest(@NotBlank @Size(max = 1000) String reason) {}
