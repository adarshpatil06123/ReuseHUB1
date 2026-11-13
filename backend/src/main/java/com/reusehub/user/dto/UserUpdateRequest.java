package com.reusehub.user.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Size(max = 120) String name,
    @Size(max = 60) String username,
    @Size(max = 255) String profileImageUrl,
    @Size(max = 120) String department,
    @Size(max = 160) String college,
    @Size(max = 40) String phoneNumber,
    @Size(max = 120) String location
) {}
