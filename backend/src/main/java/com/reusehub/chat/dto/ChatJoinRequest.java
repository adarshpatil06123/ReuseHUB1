package com.reusehub.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatJoinRequest(
    @NotNull Long listingId,
    @NotNull Long userId,
    @NotNull Long otherUserId
) {}
