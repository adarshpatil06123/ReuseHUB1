package com.reusehub.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeCreateRequest(
    @NotBlank @Size(max = 1000) String reason
) {}
