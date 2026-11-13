package com.reusehub.listing;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.dto.ListingCreateRequest;
import com.reusehub.listing.dto.ListingResponse;
import com.reusehub.listing.dto.ListingUpdateRequest;
import com.reusehub.listing.dto.ListingStatusUpdateRequest;
import com.reusehub.listing.dto.RelistingSuggestionResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/listings")
public class ListingController {
  private final ListingService listingService;

  public ListingController(ListingService listingService) {
    this.listingService = listingService;
  }

  @PostMapping
  public ListingResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ListingCreateRequest request
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    Listing listing = request.toListing();
    return ListingResponse.from(
        listingService.create(principal, listing, request.imageUrls())
    );
  }

  @GetMapping
  public List<ListingResponse> search(
      @RequestParam(required = false) ListingCategory category,
      @RequestParam(required = false) ItemCondition condition,
      @RequestParam(required = false) ListingType type,
      @RequestParam(required = false, name = "price_min") BigDecimal priceMin,
      @RequestParam(required = false, name = "price_max") BigDecimal priceMax,
      @RequestParam(required = false) String keyword
  ) {
    return listingService.search(category, condition, type, priceMin, priceMax, keyword)
        .stream()
        .map(ListingResponse::from)
        .toList();
  }

  @GetMapping("/me")
  public List<ListingResponse> myListings(
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return listingService.getForUser(principal)
        .stream()
        .map(ListingResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public ListingResponse byId(@PathVariable Long id) {
    return ListingResponse.from(listingService.getById(id));
  }

  @PutMapping("/{id}")
  public ListingResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody ListingUpdateRequest request
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    Listing listing = request.toListing();
    return ListingResponse.from(
        listingService.update(principal, id, listing, request.imageUrls())
    );
  }

  @DeleteMapping("/{id}")
  public void delete(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long id
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    listingService.delete(principal, id);
  }

  @PatchMapping("/{id}/status")
  public ListingResponse updateStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long id,
      @Valid @RequestBody ListingStatusUpdateRequest request
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return ListingResponse.from(
        listingService.updateStatus(principal, id, request.status())
    );
  }

  @GetMapping("/me/relisting-suggestions")
  public List<RelistingSuggestionResponse> getRelistingSuggestions(
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return listingService.getRelistingSuggestions(principal);
  }

  @PostMapping("/{id}/relisting-suggestion/dismiss")
  public void dismissRelistingSuggestion(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long id
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    listingService.dismissRelistingSuggestion(principal, id);
  }
}
