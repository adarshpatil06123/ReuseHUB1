package com.reusehub.order;

import com.reusehub.demand.LocationDemandCount;
import com.reusehub.listing.ListingStatus;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  @Query("""
      select new com.reusehub.demand.LocationDemandCount(
        coalesce(o.listing.location, 'North Campus'), count(o.id))
      from Order o
      where o.listing.status = :listingStatus
        and o.status = :orderStatus
      group by coalesce(o.listing.location, 'North Campus')
      """)
  List<LocationDemandCount> countOpenRequestsByLocation(
      @Param("listingStatus") ListingStatus listingStatus,
      @Param("orderStatus") OrderStatus orderStatus);

  List<Order> findByBuyer_IdOrSeller_Id(Long buyerId, Long sellerId);

  List<Order> findByBundleIdOrderByBundlePositionAsc(UUID bundleId);

  List<Order> findByBundleIdAndStatus(UUID bundleId, OrderStatus status);

  List<Order> findByListing_IdAndStatus(Long listingId, OrderStatus status);

  List<Order> findByListing_IdAndBuyer_IdAndStatus(Long listingId, Long buyerId, OrderStatus status);

  List<Order> findByOfferedListingIdAndStatus(Long offeredListingId, OrderStatus status);

  List<Order> findByStatusAndHandoffDeadlineAtBefore(OrderStatus status, Instant deadline);

  List<Order> findByStatusAndHandoffDeadlineAtIsNull(OrderStatus status);
}
