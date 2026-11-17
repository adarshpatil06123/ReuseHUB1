package com.reusehub.order;

import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.listing.ListingStatus;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.dto.ListingEventResponse;
import com.reusehub.listing.dto.ListingResponse;
import com.reusehub.order.dto.OrderCreateRequest;
import com.reusehub.order.dto.OrderEventResponse;
import com.reusehub.order.dto.OrderNotificationDto;
import com.reusehub.order.dto.OrderResponse;
import com.reusehub.order.dto.OrderBundleCreateRequest;
import com.reusehub.order.dto.OrderStatusUpdateRequest;
import com.reusehub.order.dto.HandoffConfirmationRequest;
import com.reusehub.order.CollateralStatus;
import com.reusehub.user.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private static final double HANDOFF_RADIUS_METERS = 100.0;

  private final OrderRepository orderRepository;
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;
  private final SimpMessagingTemplate messagingTemplate;
  private final EntityManager entityManager;

  @Value("${reusehub.orders.handoff-window:PT24H}")
  private Duration handoffWindow = Duration.ofHours(24);

  public OrderService(
      OrderRepository orderRepository,
      ListingRepository listingRepository,
      UserRepository userRepository,
      SimpMessagingTemplate messagingTemplate,
      EntityManager entityManager
  ) {
    this.orderRepository = orderRepository;
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
    this.messagingTemplate = messagingTemplate;
    this.entityManager = entityManager;
  }

  public List<OrderResponse> listForUser(Long userId) {
    return orderRepository.findByBuyer_IdOrSeller_Id(userId, userId)
        .stream()
        .map(order -> {
          Listing offered = null;
          if (order.getOfferedListingId() != null) {
            offered = listingRepository.findById(order.getOfferedListingId()).orElse(null);
          }

          return OrderResponse.from(order, userId, offered);
        })
        .toList();
  }

  @Transactional
  public OrderResponse create(Long userId, OrderCreateRequest request) {
    Listing listing = listingRepository.findByIdForUpdate(request.listingId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));

    if (listing.getUser().getId().equals(userId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot purchase or swap your own listing");
    }
    if (listing.getListingType() == ListingType.RENT) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Use the rental request workflow for rental listings");
    }

    // Conflict Prevention: Listing must be ACTIVE
    if (listing.getStatus() != ListingStatus.ACTIVE) {
      throw new ApiException(HttpStatus.CONFLICT, "Listing is currently " + listing.getStatus() + " and not accepting new requests");
    }

    // Conflict Prevention: User cannot have duplicate open pending requests on the same listing
    boolean alreadyRequested = !orderRepository
        .findByListing_IdAndBuyer_IdAndStatus(listing.getId(), userId, OrderStatus.REQUESTED)
        .isEmpty();
    if (alreadyRequested) {
      throw new ApiException(HttpStatus.CONFLICT, "You already have an active pending request for this listing");
    }

    Listing offeredListing = null;
    if (request.type() == OrderType.SWAP && request.offeredListingId() != null) {
      if (request.offeredListingId().equals(listing.getId())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot offer the same listing in a swap");
      }
      offeredListing = listingRepository.findById(request.offeredListingId())
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offered swap listing not found"));

      if (!offeredListing.getUser().getId().equals(userId)) {
        throw new ApiException(HttpStatus.FORBIDDEN, "Offered swap listing must belong to you");
      }

      // Conflict Prevention: Offered item must be active
      if (offeredListing.getStatus() != ListingStatus.ACTIVE) {
        throw new ApiException(HttpStatus.CONFLICT, "Offered swap item '" + offeredListing.getTitle() + "' is currently " + offeredListing.getStatus());
      }
    }

    var buyer = userRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

    var seller = listing.getUser();

    Order order = new Order();
    order.setListing(listing);
    order.setBuyer(buyer);
    order.setSeller(seller);
    order.setType(request.type());
    order.setOfferedPrice(request.offeredPrice());
    order.setFinalPrice(request.finalPrice());
    order.setOfferedListingId(request.offeredListingId());
    order.setMessage(request.message());
    order.setExpiresAt(request.expiresAt());

    Order saved = orderRepository.save(order);
    OrderResponse response = OrderResponse.from(saved, userId, offeredListing);
    publishEvent(saved, "CREATED");
    publishOrderNotification(
        saved.getSeller().getId(),
        saved,
        saved.getBuyer().getId(),
        saved.getBuyer().getName() + " sent you a new " + saved.getType().name().toLowerCase() + " request."
    );
    return response;
  }

  @Transactional
  public List<OrderResponse> createBundle(Long userId, OrderBundleCreateRequest request) {
    List<Long> listingIds = request.listingIds();
    if (listingIds.size() < 2 || listingIds.size() > 5
        || new LinkedHashSet<>(listingIds).size() != listingIds.size()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose between 2 and 5 different listings for a bundle");
    }

    List<Long> lockIds = new ArrayList<>(listingIds);
    lockIds.sort(Long::compareTo);
    for (Long listingId : lockIds) {
      listingRepository.findByIdForUpdate(listingId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing " + listingId + " not found"));
    }

    List<Listing> listings = listingIds.stream()
        .map(id -> listingRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing " + id + " not found")))
        .toList();
    Long sellerId = listings.get(0).getUser().getId();
    if (listings.stream().anyMatch(listing -> !listing.getUser().getId().equals(sellerId))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "All bundle items must belong to the same seller");
    }

    for (Listing listing : listings) {
      if (listing.getListingType() != ListingType.SELL) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Bundles can only contain listings available for sale");
      }
      if (listing.getUser().getId().equals(userId)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot purchase your own listing");
      }
      if (listing.getStatus() != ListingStatus.ACTIVE) {
        throw new ApiException(HttpStatus.CONFLICT, "Listing '" + listing.getTitle() + "' is not available");
      }
      if (listing.getPrice() == null || listing.getPrice().signum() <= 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Every bundle listing must have a price greater than zero");
      }
      if (!orderRepository.findByListing_IdAndBuyer_IdAndStatus(
          listing.getId(), userId, OrderStatus.REQUESTED).isEmpty()) {
        throw new ApiException(HttpStatus.CONFLICT, "You already have a pending request for '" + listing.getTitle() + "'");
      }
    }

    var buyer = userRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    var seller = listings.get(0).getUser();
    UUID bundleId = UUID.randomUUID();
    int bundleSize = listings.size();
    List<OrderResponse> responses = new ArrayList<>();
    for (int index = 0; index < listings.size(); index++) {
      Listing listing = listings.get(index);
      Order order = new Order();
      order.setListing(listing);
      order.setBuyer(buyer);
      order.setSeller(seller);
      order.setType(OrderType.BUY);
      order.setStatus(OrderStatus.REQUESTED);
      order.setFinalPrice(listing.getPrice());
      order.setMessage(request.message());
      order.setBundleId(bundleId);
      order.setBundlePosition(index + 1);
      order.setBundleSize(bundleSize);
      Order saved = orderRepository.save(order);
      responses.add(OrderResponse.from(saved, userId));
      publishEvent(saved, "CREATED");
    }

    Order first = orderRepository.findByBundleIdOrderByBundlePositionAsc(bundleId).get(0);
    publishOrderNotification(
        seller.getId(),
        first,
        buyer.getId(),
        buyer.getName() + " requested a bundle of " + bundleSize + " items."
    );
    return List.copyOf(responses);
  }

  @Transactional
  public OrderResponse updateStatus(Long userId, UUID orderId, OrderStatusUpdateRequest request) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));

    if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Not allowed to modify this order");
    }
    if (order.getBundleId() != null) {
      return updateBundleStatus(userId, order, request);
    }

    OrderStatus previousStatus = order.getStatus();
    OrderStatus targetStatus = request.status();
    Listing primaryListing = order.getListing();

    // Only seller can accept an incoming request
    if (targetStatus == OrderStatus.ACCEPTED && !order.getSeller().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only the seller can accept this request");
    }

    // 1. DUAL-RESOURCE SWAP LOCK & CONFLICT PREVENTION ON ACCEPT
    if (targetStatus == OrderStatus.ACCEPTED) {
      primaryListing = listingRepository.findByIdForUpdate(primaryListing.getId())
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
      entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
      previousStatus = order.getStatus();
      if (previousStatus != OrderStatus.REQUESTED) {
        throw new ApiException(HttpStatus.CONFLICT, "Only pending (REQUESTED) orders can be accepted");
      }

      if (primaryListing.getListingType() == ListingType.RENT) {
        throw new ApiException(HttpStatus.CONFLICT, "Rental listings must be managed through the rental workflow");
      }

      // Validate primary listing availability
      if (primaryListing.getStatus() != ListingStatus.ACTIVE) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "Conflict Prevention: Primary listing is already " + primaryListing.getStatus()
        );
      }

      BigDecimal acceptedPrice = null;
      if (order.getType() == OrderType.BUY) {
        acceptedPrice = request.finalPrice() != null
            ? request.finalPrice()
            : order.getFinalPrice() != null
                ? order.getFinalPrice()
                : order.getOfferedPrice() != null ? order.getOfferedPrice() : primaryListing.getPrice();
        if (acceptedPrice == null || acceptedPrice.signum() <= 0) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "A positive agreed price is required to lock collateral");
        }
      }

      if (request.finalPrice() != null) {
        order.setFinalPrice(request.finalPrice());
      } else if (acceptedPrice != null) {
        order.setFinalPrice(acceptedPrice);
      }
      if (acceptedPrice != null) {
        lockCollateral(order, acceptedPrice);
      }

      if (order.getType() == OrderType.SWAP && order.getOfferedListingId() != null) {
        Listing offeredListing = listingRepository.findById(order.getOfferedListingId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offered swap listing not found"));

        // Validate offered listing availability
        if (offeredListing.getStatus() != ListingStatus.ACTIVE) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "Conflict Prevention: Offered swap item '" + offeredListing.getTitle() + "' is currently " + offeredListing.getStatus()
          );
        }
        if (offeredListing.getListingType() == ListingType.RENT) {
          throw new ApiException(HttpStatus.CONFLICT, "Rental listings cannot be offered in a swap");
        }

        // ATOMIC DUAL-RESOURCE LOCK: Both items transition to RESERVED
        primaryListing.setStatus(ListingStatus.RESERVED);
        offeredListing.setStatus(ListingStatus.RESERVED);
        listingRepository.save(primaryListing);
        listingRepository.save(offeredListing);
        publishListingEvent(primaryListing, "UPDATED");
        publishListingEvent(offeredListing, "UPDATED");

        // CONFLICT PREVENTION: Auto-decline competing requests for offered listing
        cancelCompetingRequests(offeredListing.getId(), order.getId(),
            "System: Request declined by Conflict Prevention — offered item committed to another confirmed swap.");
        cancelCompetingSwapOffers(offeredListing.getId(), order.getId(),
            "System: Swap offer declined by Conflict Prevention — offered item committed to another confirmed swap.");
      } else {
        // Single resource lock for BUY / DONATE
        primaryListing.setStatus(ListingStatus.RESERVED);
        listingRepository.save(primaryListing);
        publishListingEvent(primaryListing, "UPDATED");
      }

      // CONFLICT PREVENTION: Auto-decline all other competing requests for primary listing
      cancelCompetingRequests(primaryListing.getId(), order.getId(),
          "System: Request declined by Conflict Prevention — listing was reserved by another confirmed transaction.");

      order.setHandoffDeadlineAt(Instant.now().plus(handoffWindow));
    }

    // Completion must go through the two-party geofenced handoff endpoint.
    if (targetStatus == OrderStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Orders can only be completed after both participants confirm the geofenced handoff"
      );
    }

    if (targetStatus == OrderStatus.NO_SHOW) {
      throw new ApiException(HttpStatus.CONFLICT, "No-show status is managed automatically");
    }

    // 3. RESOURCE UNLOCK (CANCELLED / REJECTED)
    if ((targetStatus == OrderStatus.CANCELLED || targetStatus == OrderStatus.REJECTED)
        && previousStatus == OrderStatus.ACCEPTED) {
      releaseReservedListings(order);
      clearHandoffCheckIn(order);
      refundCollateral(order);
    }

    order.setStatus(targetStatus);
    if (request.finalPrice() != null && targetStatus != OrderStatus.ACCEPTED) {
      order.setFinalPrice(request.finalPrice());
    }
    if (request.completedAt() != null) {
      order.setCompletedAt(request.completedAt());
    }

    Order saved = orderRepository.save(order);
    publishEvent(saved, "UPDATED");
    if (targetStatus == OrderStatus.ACCEPTED) {
      publishOrderNotification(
          saved.getBuyer().getId(),
          saved,
          userId,
          "Your request was accepted. Coordinate a handoff and confirm your locations in My Orders."
      );
    } else if (targetStatus == OrderStatus.REJECTED) {
      publishOrderNotification(
          saved.getBuyer().getId(),
          saved,
          userId,
          "Your request was declined by the seller."
      );
    }

    Listing offered = null;
    if (saved.getOfferedListingId() != null) {
      offered = listingRepository.findById(saved.getOfferedListingId()).orElse(null);
    }
    return OrderResponse.from(saved, userId, offered);
  }

  private OrderResponse updateBundleStatus(
      Long userId,
      Order requestedOrder,
      OrderStatusUpdateRequest request
  ) {
    List<Order> bundle = orderRepository.findByBundleIdOrderByBundlePositionAsc(requestedOrder.getBundleId());
    if (request.status() == OrderStatus.ACCEPTED) {
      List<Long> listingIds = bundle.stream().map(item -> item.getListing().getId()).sorted().toList();
      for (Long listingId : listingIds) {
        listingRepository.findByIdForUpdate(listingId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
      }
      for (Order item : bundle) {
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
      }
    }
    if (bundle.size() != requestedOrder.getBundleSize()
        || bundle.stream().anyMatch(item -> item.getStatus() != requestedOrder.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "The bundle is incomplete or items have different statuses");
    }

    OrderStatus previousStatus = requestedOrder.getStatus();
    OrderStatus targetStatus = request.status();
    if (targetStatus == OrderStatus.ACCEPTED) {
      if (!requestedOrder.getSeller().getId().equals(userId)) {
        throw new ApiException(HttpStatus.FORBIDDEN, "Only the seller can accept this bundle");
      }
      if (previousStatus != OrderStatus.REQUESTED) {
        throw new ApiException(HttpStatus.CONFLICT, "Only a pending bundle can be accepted");
      }
      if (request.finalPrice() != null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Bundle items use their individual listing prices");
      }
      List<BigDecimal> itemPrices = new ArrayList<>();
      for (Order item : bundle) {
        Listing listing = item.getListing();
        if (listing.getListingType() != ListingType.SELL) {
          throw new ApiException(HttpStatus.CONFLICT, "Bundle items must remain available for sale");
        }
        if (listing.getStatus() != ListingStatus.ACTIVE) {
          throw new ApiException(HttpStatus.CONFLICT, "Bundle item '" + listing.getTitle() + "' is no longer available");
        }
        BigDecimal price = item.getFinalPrice() != null ? item.getFinalPrice() : listing.getPrice();
        if (price == null || price.signum() <= 0) {
          throw new ApiException(HttpStatus.BAD_REQUEST, "Every bundle item must have a price greater than zero");
        }
        itemPrices.add(price);
      }
      for (int index = 0; index < bundle.size(); index++) {
        Order item = bundle.get(index);
        BigDecimal price = itemPrices.get(index);
        item.setFinalPrice(price);
        lockCollateral(item, price);
      }
      Instant deadline = Instant.now().plus(handoffWindow);
      Set<UUID> bundleOrderIds = bundle.stream().map(Order::getId)
          .collect(java.util.stream.Collectors.toSet());
      for (Order item : bundle) {
        Listing listing = item.getListing();
        listing.setStatus(ListingStatus.RESERVED);
        listingRepository.save(listing);
        publishListingEvent(listing, "UPDATED");
        cancelCompetingRequests(listing.getId(), bundleOrderIds,
            "System: Request declined because the listing was reserved by an accepted bundle.");
        item.setHandoffDeadlineAt(deadline);
        item.setStatus(OrderStatus.ACCEPTED);
      }
    } else if (targetStatus == OrderStatus.COMPLETED || targetStatus == OrderStatus.NO_SHOW) {
      throw new ApiException(HttpStatus.CONFLICT, "Bundle completion and no-show status are managed by the handoff workflow");
    } else if (targetStatus != OrderStatus.REJECTED && targetStatus != OrderStatus.CANCELLED) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "This status is not supported for a bundle");
    } else {
      if (previousStatus != OrderStatus.REQUESTED && previousStatus != OrderStatus.ACCEPTED) {
        throw new ApiException(HttpStatus.CONFLICT, "This bundle can no longer be changed");
      }
      if (previousStatus == OrderStatus.ACCEPTED) {
        for (Order item : bundle) {
          releaseReservedListings(item);
          refundCollateral(item);
          clearHandoffCheckIn(item);
        }
      }
      bundle.forEach(item -> item.setStatus(targetStatus));
    }

    for (Order item : bundle) {
      Order saved = orderRepository.save(item);
      publishEvent(saved, "UPDATED");
    }
    Order first = bundle.get(0);
    if (targetStatus == OrderStatus.ACCEPTED) {
      publishOrderNotification(first.getBuyer().getId(), first, userId,
          "The seller accepted all " + bundle.size() + " items in your bundle.");
    } else if (targetStatus == OrderStatus.REJECTED) {
      publishOrderNotification(first.getBuyer().getId(), first, userId, "The seller declined your bundle.");
    } else if (targetStatus == OrderStatus.CANCELLED) {
      Long otherParticipantId = first.getBuyer().getId().equals(userId)
          ? first.getSeller().getId() : first.getBuyer().getId();
      publishOrderNotification(otherParticipantId, first, userId, "The bundle was cancelled.");
    }
    return responseFor(requestedOrder, userId);
  }

  @Transactional
  public OrderResponse confirmHandoff(
      Long userId,
      UUID orderId,
      HandoffConfirmationRequest request
  ) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    ensureParticipant(order, userId);
    if (order.getBundleId() != null) {
      return confirmBundleHandoff(userId, order, request);
    }

    if (order.getStatus() != OrderStatus.ACCEPTED) {
      throw new ApiException(HttpStatus.CONFLICT, "Only accepted orders can confirm a handoff");
    }

    Instant now = Instant.now();
    if (order.getHandoffDeadlineAt() == null) {
      order.setHandoffDeadlineAt(now.plus(handoffWindow));
    } else if (!now.isBefore(order.getHandoffDeadlineAt())) {
      markNoShow(order);
      Order saved = orderRepository.save(order);
      publishEvent(saved, "UPDATED");
      publishNoShowNotifications(saved);
      return responseFor(saved, userId);
    }

    boolean isBuyer = order.getBuyer().getId().equals(userId);
    Instant existingConfirmation = isBuyer
        ? order.getBuyerHandoffConfirmedAt()
        : order.getSellerHandoffConfirmedAt();
    if (existingConfirmation != null) {
      throw new ApiException(HttpStatus.CONFLICT, "You have already confirmed this handoff");
    }

    if (order.getHandoffCheckInUserId() == null) {
      order.setHandoffCheckInUserId(userId);
      order.setHandoffCheckInLatitude(request.latitude());
      order.setHandoffCheckInLongitude(request.longitude());
      setHandoffConfirmedAt(order, isBuyer, now);
      Long otherParticipantId = isBuyer ? order.getSeller().getId() : order.getBuyer().getId();
      publishOrderNotification(
          otherParticipantId,
          order,
          userId,
          (isBuyer ? "The buyer" : "The seller")
              + " checked in at the handoff location. Confirm your location nearby to complete the exchange."
      );
    } else {
      if (order.getHandoffCheckInUserId().equals(userId)) {
        throw new ApiException(HttpStatus.CONFLICT, "The other participant must confirm from their own account");
      }

      double distanceMeters = distanceInMeters(
          order.getHandoffCheckInLatitude(),
          order.getHandoffCheckInLongitude(),
          request.latitude(),
          request.longitude()
      );
      if (distanceMeters > HANDOFF_RADIUS_METERS) {
        throw new ApiException(
            HttpStatus.CONFLICT,
            "You are outside the 100-meter handoff zone. Move closer to the other participant and try again."
        );
      }

      setHandoffConfirmedAt(order, isBuyer, now);
      completeHandoff(order, now);
      Long otherParticipantId = isBuyer ? order.getSeller().getId() : order.getBuyer().getId();
      publishOrderNotification(
          otherParticipantId,
          order,
          userId,
          "Both participants confirmed nearby. The handoff is complete."
      );
    }

    Order saved = orderRepository.save(order);
    publishEvent(saved, "UPDATED");
    return responseFor(saved, userId);
  }

  private OrderResponse confirmBundleHandoff(
      Long userId,
      Order requestedOrder,
      HandoffConfirmationRequest request
  ) {
    List<Order> bundle = orderRepository.findByBundleIdOrderByBundlePositionAsc(requestedOrder.getBundleId());
    if (bundle.size() != requestedOrder.getBundleSize()
        || bundle.stream().anyMatch(item -> item.getStatus() != OrderStatus.ACCEPTED)) {
      throw new ApiException(HttpStatus.CONFLICT, "All bundle items must be accepted before handoff");
    }

    Order first = bundle.get(0);
    Instant now = Instant.now();
    if (first.getHandoffDeadlineAt() == null) {
      Instant deadline = now.plus(handoffWindow);
      bundle.forEach(item -> item.setHandoffDeadlineAt(deadline));
    } else if (!now.isBefore(first.getHandoffDeadlineAt())) {
      for (Order item : bundle) {
        markNoShow(item);
        Order saved = orderRepository.save(item);
        publishEvent(saved, "UPDATED");
      }
      publishNoShowNotifications(first);
      return responseFor(requestedOrder, userId);
    }

    boolean isBuyer = requestedOrder.getBuyer().getId().equals(userId);
    Instant existingConfirmation = isBuyer
        ? first.getBuyerHandoffConfirmedAt() : first.getSellerHandoffConfirmedAt();
    if (existingConfirmation != null) {
      throw new ApiException(HttpStatus.CONFLICT, "You have already confirmed this bundle handoff");
    }

    if (first.getHandoffCheckInUserId() == null) {
      for (Order item : bundle) {
        item.setHandoffCheckInUserId(userId);
        item.setHandoffCheckInLatitude(request.latitude());
        item.setHandoffCheckInLongitude(request.longitude());
        setHandoffConfirmedAt(item, isBuyer, now);
      }
      Long otherParticipantId = isBuyer ? first.getSeller().getId() : first.getBuyer().getId();
      publishOrderNotification(otherParticipantId, first, userId,
          (isBuyer ? "The buyer" : "The seller")
              + " checked in. Confirm nearby to complete all items in the bundle.");
    } else {
      if (first.getHandoffCheckInUserId().equals(userId)) {
        throw new ApiException(HttpStatus.CONFLICT, "The other participant must confirm from their own account");
      }
      double distanceMeters = distanceInMeters(
          first.getHandoffCheckInLatitude(),
          first.getHandoffCheckInLongitude(),
          request.latitude(),
          request.longitude()
      );
      if (distanceMeters > HANDOFF_RADIUS_METERS) {
        throw new ApiException(HttpStatus.CONFLICT, "You are outside the 100-meter handoff zone");
      }
      for (Order item : bundle) {
        setHandoffConfirmedAt(item, isBuyer, now);
        completeHandoff(item, now);
      }
      Long otherParticipantId = isBuyer ? first.getSeller().getId() : first.getBuyer().getId();
      publishOrderNotification(otherParticipantId, first, userId, "All items in the bundle are complete.");
    }

    for (Order item : bundle) {
      Order saved = orderRepository.save(item);
      publishEvent(saved, "UPDATED");
    }
    return responseFor(requestedOrder, userId);
  }

  @Transactional
  @Scheduled(fixedDelayString = "${reusehub.orders.no-show-check-interval-ms:60000}")
  public void requeueExpiredHandoffs() {
    Instant now = Instant.now();
    Set<UUID> processedBundles = new LinkedHashSet<>();

    for (Order legacyOrder : orderRepository.findByStatusAndHandoffDeadlineAtIsNull(OrderStatus.ACCEPTED)) {
      legacyOrder.setHandoffDeadlineAt(now.plus(handoffWindow));
      orderRepository.save(legacyOrder);
      publishEvent(legacyOrder, "UPDATED");
    }

    for (Order order : orderRepository.findByStatusAndHandoffDeadlineAtBefore(OrderStatus.ACCEPTED, now)) {
      if (order.getBundleId() != null && !processedBundles.add(order.getBundleId())) {
        continue;
      }
      List<Order> expiredItems = order.getBundleId() == null
          ? List.of(order)
          : orderRepository.findByBundleIdOrderByBundlePositionAsc(order.getBundleId());
      for (Order expiredItem : expiredItems) {
        if (expiredItem.getStatus() != OrderStatus.ACCEPTED) {
          continue;
        }
        markNoShow(expiredItem);
        Order saved = orderRepository.save(expiredItem);
        publishEvent(saved, "UPDATED");
      }
      publishNoShowNotifications(order);
    }
  }

  private void publishNoShowNotifications(Order order) {
    String message = "The handoff window expired. The listing is active on the marketplace again.";
    publishOrderNotification(order.getBuyer().getId(), order, null, message);
    publishOrderNotification(order.getSeller().getId(), order, null, message);
  }

  private void publishOrderNotification(Long recipientId, Order order, Long actorId, String content) {
    String actorName = actorId == null
        ? "ReuseHub"
        : userRepository.findById(actorId).map(user -> user.getName()).orElse("ReuseHub");
    OrderNotificationDto notification = new OrderNotificationDto(
        "ORDER",
        order.getId() + ":" + order.getStatus() + ":" + Instant.now().toEpochMilli(),
        order.getId(),
        order.getListing().getId(),
        order.getListing().getTitle(),
        actorName,
        content,
        order.getStatus().name(),
        Instant.now()
    );
    messagingTemplate.convertAndSend(
        "/topic/users/" + recipientId + "/notifications",
        notification
    );
  }

  private void completeHandoff(Order order, Instant completedAt) {
    Listing primaryListing = order.getListing();
    primaryListing.setStatus(order.getType() == OrderType.SWAP
        ? ListingStatus.SWAPPED
        : order.getType() == OrderType.DONATE ? ListingStatus.DONATED : ListingStatus.SOLD);
    listingRepository.save(primaryListing);
    publishListingEvent(primaryListing, "UPDATED");

    if (order.getType() == OrderType.SWAP && order.getOfferedListingId() != null) {
      listingRepository.findById(order.getOfferedListingId()).ifPresent(offered -> {
        offered.setStatus(ListingStatus.SWAPPED);
        listingRepository.save(offered);
        publishListingEvent(offered, "UPDATED");
      });
    }

    order.setStatus(OrderStatus.COMPLETED);
    order.setCompletedAt(completedAt);
    releaseCollateral(order, completedAt);
    clearHandoffCheckIn(order);
  }

  private void markNoShow(Order order) {
    releaseReservedListings(order);
    refundCollateral(order);
    clearHandoffCheckIn(order);
    order.setStatus(OrderStatus.NO_SHOW);
    order.setMessage("No-show: the handoff window expired. Reserved listing(s) are active on the marketplace again.");
  }

  private void lockCollateral(Order order, BigDecimal amount) {
    if (order.getCollateralStatus() != CollateralStatus.NOT_APPLICABLE) {
      throw new ApiException(HttpStatus.CONFLICT, "Collateral has already been locked or settled");
    }
    order.setCollateralAmount(amount);
    order.setCollateralStatus(CollateralStatus.HELD);
    order.setCollateralLockedAt(Instant.now());
  }

  private void releaseCollateral(Order order, Instant settledAt) {
    if (order.getCollateralStatus() == CollateralStatus.HELD) {
      order.setCollateralStatus(CollateralStatus.RELEASED);
      order.setCollateralSettledAt(settledAt);
    }
  }

  private void refundCollateral(Order order) {
    if (order.getCollateralStatus() == CollateralStatus.HELD) {
      order.setCollateralStatus(CollateralStatus.REFUNDED);
      order.setCollateralSettledAt(Instant.now());
    }
  }

  private void releaseReservedListings(Order order) {
    Listing primaryListing = order.getListing();
    if (primaryListing.getStatus() == ListingStatus.RESERVED) {
      primaryListing.setStatus(ListingStatus.ACTIVE);
      listingRepository.save(primaryListing);
      publishListingEvent(primaryListing, "UPDATED");
    }

    if (order.getType() == OrderType.SWAP && order.getOfferedListingId() != null) {
      listingRepository.findById(order.getOfferedListingId()).ifPresent(offered -> {
        if (offered.getStatus() == ListingStatus.RESERVED) {
          offered.setStatus(ListingStatus.ACTIVE);
          listingRepository.save(offered);
          publishListingEvent(offered, "UPDATED");
        }
      });
    }
  }

  private void ensureParticipant(Order order, Long userId) {
    if (!order.getBuyer().getId().equals(userId) && !order.getSeller().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Not allowed to modify this order");
    }
  }

  private void setHandoffConfirmedAt(Order order, boolean isBuyer, Instant confirmedAt) {
    if (isBuyer) {
      order.setBuyerHandoffConfirmedAt(confirmedAt);
    } else {
      order.setSellerHandoffConfirmedAt(confirmedAt);
    }
  }

  private void clearHandoffCheckIn(Order order) {
    order.setHandoffCheckInLatitude(null);
    order.setHandoffCheckInLongitude(null);
    order.setHandoffCheckInUserId(null);
  }

  private double distanceInMeters(double latitude1, double longitude1, double latitude2, double longitude2) {
    double earthRadiusMeters = 6_371_000.0;
    double latitudeDelta = Math.toRadians(latitude2 - latitude1);
    double longitudeDelta = Math.toRadians(longitude2 - longitude1);
    double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
        + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
        * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
    a = Math.min(1.0, Math.max(0.0, a));
    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private OrderResponse responseFor(Order order, Long userId) {
    Listing offered = null;
    if (order.getOfferedListingId() != null) {
      offered = listingRepository.findById(order.getOfferedListingId()).orElse(null);
    }
    return OrderResponse.from(order, userId, offered);
  }

  private void cancelCompetingRequests(Long listingId, UUID currentOrderId, String declineReason) {
    cancelCompetingRequests(listingId, Set.of(currentOrderId), declineReason);
  }

  private void cancelCompetingRequests(Long listingId, Set<UUID> currentOrderIds, String declineReason) {
    List<Order> competing = orderRepository.findByListing_IdAndStatus(listingId, OrderStatus.REQUESTED);
    for (Order c : competing) {
      if (c.getId() != null && !currentOrderIds.contains(c.getId())) {
        c.setStatus(OrderStatus.REJECTED);
        c.setMessage(declineReason);
        Order saved = orderRepository.save(c);
        publishEvent(saved, "UPDATED");
      }
    }
  }

  private void cancelCompetingSwapOffers(Long offeredListingId, UUID currentOrderId, String declineReason) {
    List<Order> competing = orderRepository.findByOfferedListingIdAndStatus(offeredListingId, OrderStatus.REQUESTED);
    for (Order c : competing) {
      if (c.getId() != null && !c.getId().equals(currentOrderId)) {
        c.setStatus(OrderStatus.REJECTED);
        c.setMessage(declineReason);
        Order saved = orderRepository.save(c);
        publishEvent(saved, "UPDATED");
      }
    }
  }

  public void publishEvent(Order order, String action) {
    Listing offered = null;
    if (order.getOfferedListingId() != null) {
      offered = listingRepository.findById(order.getOfferedListingId()).orElse(null);
    }

    OrderEventResponse buyerEvent = new OrderEventResponse(
        action,
        OrderResponse.from(order, order.getBuyer().getId(), offered)
    );
    OrderEventResponse sellerEvent = new OrderEventResponse(
        action,
        OrderResponse.from(order, order.getSeller().getId(), offered)
    );
    messagingTemplate.convertAndSend(
        "/topic/orders/" + order.getBuyer().getId(),
        buyerEvent
    );
    messagingTemplate.convertAndSend(
        "/topic/orders/" + order.getSeller().getId(),
        sellerEvent
    );
  }

  public void publishListingEvent(Listing listing, String action) {
    ListingEventResponse payload = new ListingEventResponse(
        action,
        listing.getId(),
        ListingResponse.from(listing)
    );
    messagingTemplate.convertAndSend(
        "/topic/listings/" + listing.getUser().getId(),
        payload
    );
  }
}
