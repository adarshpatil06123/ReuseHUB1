package com.reusehub.wishlist;

import com.reusehub.demand.LocationDemandCount;
import com.reusehub.listing.ListingStatus;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
  @Query("""
      select new com.reusehub.demand.LocationDemandCount(
        coalesce(w.listing.location, 'North Campus'), count(w.id))
      from WishlistItem w
      where w.listing.status = :listingStatus
      group by coalesce(w.listing.location, 'North Campus')
      """)
  List<LocationDemandCount> countActiveWishlistSavesByLocation(
      @Param("listingStatus") ListingStatus listingStatus);

  List<WishlistItem> findByUser_IdOrderByCreatedAtDesc(Long userId);
  Optional<WishlistItem> findByUser_IdAndListing_Id(Long userId, Long listingId);
  boolean existsByUser_IdAndListing_Id(Long userId, Long listingId);
  void deleteByUser_IdAndListing_Id(Long userId, Long listingId);
  @Modifying
  @Transactional
  void deleteByUser_Id(Long userId);
}
