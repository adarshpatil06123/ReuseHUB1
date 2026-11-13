package com.reusehub.demand;

import com.reusehub.listing.ListingStatus;
import com.reusehub.order.OrderRepository;
import com.reusehub.order.OrderStatus;
import com.reusehub.need.NeedRequestRepository;
import com.reusehub.wishlist.WishlistItemRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemandHeatmapService {
  private final WishlistItemRepository wishlistItemRepository;
  private final OrderRepository orderRepository;
  private final NeedRequestRepository needRequestRepository;

  public DemandHeatmapService(
      WishlistItemRepository wishlistItemRepository,
      OrderRepository orderRepository,
      NeedRequestRepository needRequestRepository
  ) {
    this.wishlistItemRepository = wishlistItemRepository;
    this.orderRepository = orderRepository;
    this.needRequestRepository = needRequestRepository;
  }

  @Transactional(readOnly = true)
  public List<PublicNeedRequestResponse> getPublicNeedRequests() {
    return needRequestRepository.findByActiveTrueAndShowOnHeatmapTrueOrderByCreatedAtDesc().stream()
        .map(request -> new PublicNeedRequestResponse(
            request.getId(),
            request.getUser().getId(),
            request.getUser().getName(),
            request.getKeywords(),
            request.getPreferredLocation(),
            request.getMaxPrice(),
            request.getCategory() == null ? null : request.getCategory().name(),
            request.getCreatedAt()
        ))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DemandHeatmapZoneResponse> getZones() {
    Map<String, DemandSignals> demandByLocation = new HashMap<>();
    wishlistItemRepository.countActiveWishlistSavesByLocation(ListingStatus.ACTIVE)
        .forEach(result -> demandByLocation
            .computeIfAbsent(normalizeLocation(result.location()), ignored -> new DemandSignals())
            .wishlistSaves += result.count());
    orderRepository.countOpenRequestsByLocation(ListingStatus.ACTIVE, OrderStatus.REQUESTED)
        .forEach(result -> demandByLocation
            .computeIfAbsent(normalizeLocation(result.location()), ignored -> new DemandSignals())
            .openRequests += result.count());
    needRequestRepository.countPublicNeedRequestsByLocation()
        .forEach(result -> demandByLocation
            .computeIfAbsent(normalizeLocation(result.getLocation()), ignored -> new DemandSignals())
            .needRequests += result.getCount());

    List<DemandHeatmapZoneResponse> zones = new ArrayList<>();
    demandByLocation.forEach((location, signals) -> {
      long wishlistSaves = signals.wishlistSaves;
      long openRequests = signals.openRequests;
      long needRequests = signals.needRequests;
      zones.add(new DemandHeatmapZoneResponse(
          location,
          wishlistSaves,
          openRequests,
          needRequests,
          wishlistSaves + openRequests + needRequests
      ));
    });
    zones.sort(Comparator.comparingLong(DemandHeatmapZoneResponse::demandScore)
        .reversed()
        .thenComparing(DemandHeatmapZoneResponse::location, String.CASE_INSENSITIVE_ORDER));
    return List.copyOf(zones);
  }

  private String normalizeLocation(String location) {
    return location == null || location.isBlank() ? "North Campus" : location.trim();
  }

  private static final class DemandSignals {
    private long wishlistSaves;
    private long openRequests;
    private long needRequests;
  }
}
