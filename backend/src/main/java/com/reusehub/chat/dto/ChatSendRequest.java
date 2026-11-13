package com.reusehub.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
    @NotNull Long listingId,
    @NotNull Long userId,
    @NotNull Long otherUserId,
    @NotBlank @Size(max = 2000) String content
) {}
