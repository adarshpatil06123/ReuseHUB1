package com.reusehub.wishlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.wishlist.dto.WishlistItemResponse;

@RestController
@RequestMapping("/wishlist")
public class WishlistController {
  private final WishlistService wishlistService;

  public WishlistController(WishlistService wishlistService) {
    this.wishlistService = wishlistService;
  }

  @GetMapping
  public List<WishlistItemResponse> list(
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return wishlistService.getForUser(principal)
        .stream()
        .map(WishlistItemResponse::from)
        .toList();
  }

  @PostMapping("/{listingId}")
  public WishlistItemResponse add(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long listingId
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return WishlistItemResponse.from(wishlistService.add(principal, listingId));
  }

  @DeleteMapping("/{listingId}")
  public void remove(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long listingId
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    wishlistService.remove(principal, listingId);
  }

  @DeleteMapping
  public void clear(
      @AuthenticationPrincipal UserPrincipal principal
  ) {
    if (principal == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    wishlistService.clear(principal);
  }
}
