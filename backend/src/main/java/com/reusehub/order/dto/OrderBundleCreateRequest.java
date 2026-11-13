package com.reusehub.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderBundleCreateRequest(
    @NotEmpty @Size(min = 2, max = 5) List<Long> listingIds,
    String message
) {}
