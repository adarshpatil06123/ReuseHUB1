package com.reusehub.review;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.review.dto.ReviewCreateRequest;
import com.reusehub.review.dto.ReviewResponse;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/listings/{listingId}/reviews")
public class ReviewController {
  private final ReviewRepository reviewRepository;
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;

  public ReviewController(
      ReviewRepository reviewRepository,
      ListingRepository listingRepository,
      UserRepository userRepository
  ) {
    this.reviewRepository = reviewRepository;
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
  }

  @GetMapping
  public List<ReviewResponse> getReviews(@PathVariable Long listingId) {
    return reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId)
        .stream()
        .map(ReviewResponse::from)
        .toList();
  }

  @PostMapping
  public ReviewResponse createReview(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long listingId,
      @Valid @RequestBody ReviewCreateRequest request
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    Listing listing = listingRepository.findById(listingId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));

    User author = userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

    Review review = new Review();
    review.setListing(listing);
    review.setAuthor(author);
    review.setRating(request.rating());
    review.setComment(request.comment());

    Review saved = reviewRepository.save(review);
    return ReviewResponse.from(saved);
  }
}
