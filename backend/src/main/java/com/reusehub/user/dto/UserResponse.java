package com.reusehub.user.dto;

import com.reusehub.user.User;
import java.time.Instant;

public record UserResponse(
    Long id,
    String email,
    String name,
    String username,
    String profileImageUrl,
    String department,
    String college,
    String phoneNumber,
    String location,
    Double ratingAvg,
    Integer ratingCount,
    boolean isActive,
    boolean emailVerified,
    Instant createdAt
) {
  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getUsername(),
        user.getProfileImageUrl(),
        user.getDepartment(),
        user.getCollege(),
        user.getPhoneNumber(),
        user.getLocation(),
        user.getRatingAvg(),
        user.getRatingCount(),
        user.isActive(),
        user.isEmailVerified(),
        user.getCreatedAt()
    );
  }
}
