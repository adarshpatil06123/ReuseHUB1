package com.reusehub.review.dto;

import com.reusehub.review.Review;
import java.time.Instant;

public record ReviewResponse(
    Long id,
    Long listingId,
    Long authorId,
    String authorName,
    Integer rating,
    String comment,
    Instant createdAt
) {
  public static ReviewResponse from(Review review) {
    return new ReviewResponse(
        review.getId(),
        review.getListing().getId(),
        review.getAuthor().getId(),
        review.getAuthor().getName(),
        review.getRating(),
        review.getComment(),
        review.getCreatedAt()
    );
  }
}
