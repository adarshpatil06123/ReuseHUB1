package com.reusehub.wishlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;

@Service
public class WishlistService {
  private final WishlistItemRepository wishlistItemRepository;
  private final UserRepository userRepository;
  private final ListingRepository listingRepository;

  public WishlistService(
      WishlistItemRepository wishlistItemRepository,
      UserRepository userRepository,
      ListingRepository listingRepository
  ) {
    this.wishlistItemRepository = wishlistItemRepository;
    this.userRepository = userRepository;
    this.listingRepository = listingRepository;
  }

  public List<WishlistItem> getForUser(UserPrincipal principal) {
    return wishlistItemRepository.findByUser_IdOrderByCreatedAtDesc(principal.getUserId());
  }

  @Transactional
  public WishlistItem add(UserPrincipal principal, Long listingId) {
    User user = userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    Listing listing = listingRepository.findById(listingId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));

    return wishlistItemRepository.findByUser_IdAndListing_Id(user.getId(), listing.getId())
        .orElseGet(() -> {
          WishlistItem item = new WishlistItem();
          item.setUser(user);
          item.setListing(listing);
          return wishlistItemRepository.save(item);
        });
  }

  @Transactional
  public void remove(UserPrincipal principal, Long listingId) {
    if (!wishlistItemRepository.existsByUser_IdAndListing_Id(principal.getUserId(), listingId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Wishlist item not found");
    }
    wishlistItemRepository.deleteByUser_IdAndListing_Id(principal.getUserId(), listingId);
  }

  @Transactional
  public void clear(UserPrincipal principal) {
    wishlistItemRepository.deleteByUser_Id(principal.getUserId());
  }
}
