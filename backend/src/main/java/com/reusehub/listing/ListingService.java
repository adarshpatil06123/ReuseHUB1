package com.reusehub.listing;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.dto.ListingEventResponse;
import com.reusehub.listing.dto.ListingResponse;
import com.reusehub.listing.dto.RelistingNotificationDto;
import com.reusehub.listing.dto.RelistingSuggestionResponse;
import com.reusehub.order.Order;
import com.reusehub.order.OrderRepository;
import com.reusehub.order.OrderStatus;
import com.reusehub.order.dto.OrderEventResponse;
import com.reusehub.order.dto.OrderResponse;
import com.reusehub.rental.RentalService;
import com.reusehub.need.NeedRequestService;
import com.reusehub.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class ListingService {
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final NeedRequestService needRequestService;
  private final RentalService rentalService;
  private final SimpMessagingTemplate messagingTemplate;

  @Value("${reusehub.listings.relisting-after:PT336H}")
  private Duration relistingAfter = Duration.ofDays(14);

  private final ZoneId rentalDateZone;

  public ListingService(
      ListingRepository listingRepository,
      UserRepository userRepository,
      OrderRepository orderRepository,
      NeedRequestService needRequestService,
      RentalService rentalService,
      SimpMessagingTemplate messagingTemplate,
      @Value("${reusehub.rentals.time-zone:Asia/Kolkata}") String rentalTimeZone
  ) {
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
    this.orderRepository = orderRepository;
    this.needRequestService = needRequestService;
    this.rentalService = rentalService;
    this.messagingTemplate = messagingTemplate;
    this.rentalDateZone = ZoneId.of(rentalTimeZone);
  }

  @Transactional
  public Listing create(UserPrincipal principal, Listing listing, List<String> imageUrls) {
    validateRentalListing(listing);
    var user = userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

    listing.setUser(user);
    listing.clearImages();
    if (imageUrls != null) {
      imageUrls.forEach(listing::addImage);
    }

    Listing saved = listingRepository.save(listing);
    needRequestService.matchListing(saved);
    publishEvent(saved, "CREATED");
    return saved;
  }

  @Transactional
  public Listing update(UserPrincipal principal, Long id, Listing updated, List<String> imageUrls) {
    Listing listing = getOwnedListing(principal, id);
    validateRentalListing(updated);
    rentalService.beforeListingUpdate(listing, updated);

    // AUTO-INVALIDATION ON CHANGE DETECTION
    List<String> changeDetails = new ArrayList<>();
    if (!Objects.equals(listing.getPrice(), updated.getPrice())) {
      changeDetails.add("Price changed from ₹" + listing.getPrice() + " to ₹" + updated.getPrice());
    }
    if (listing.getCondition() != updated.getCondition()) {
      changeDetails.add("Condition changed from " + listing.getCondition() + " to " + updated.getCondition());
    }
    if (listing.getListingType() != updated.getListingType()) {
      changeDetails.add("Listing type changed to " + updated.getListingType());
    }

    boolean termsChanged = !changeDetails.isEmpty();
    String changeSummary = String.join(", ", changeDetails);

    listing.setTitle(updated.getTitle());
    listing.setDescription(updated.getDescription());
    listing.setCategory(updated.getCategory());
    listing.setCondition(updated.getCondition());
    listing.setListingType(updated.getListingType());
    listing.setPrice(updated.getPrice());
    listing.setRentalPricingPeriod(updated.getRentalPricingPeriod());
    listing.setSecurityDeposit(updated.getSecurityDeposit());
    listing.setRentalAvailableFrom(updated.getRentalAvailableFrom());
    listing.setRentalAvailableUntil(updated.getRentalAvailableUntil());
    listing.setMinimumRentalDays(updated.getMinimumRentalDays());
    listing.setMaximumRentalDays(updated.getMaximumRentalDays());
    listing.setRentalInstructions(updated.getRentalInstructions());
    listing.setRelistingSuggestionSentAt(null);
    listing.setRelistingSuggestionDismissedAt(null);

    listing.clearImages();
    if (imageUrls != null) {
      imageUrls.forEach(listing::addImage);
    }

    Listing saved = listingRepository.save(listing);
    needRequestService.matchListing(saved);

    // AUTO-INVALIDATION EXECUTION: Invalidate open pending requests
    if (termsChanged) {
      // 1. Invalidate pending buyer requests on this listing
      List<Order> pendingOrders = orderRepository.findByListing_IdAndStatus(listing.getId(), OrderStatus.REQUESTED);
      for (Order order : pendingOrders) {
        order.setStatus(OrderStatus.INVALIDATED);
        order.setMessage("Auto-invalidated: Seller modified listing terms (" + changeSummary + "). Please review and resubmit.");
        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, "UPDATED");
      }

      // 2. Invalidate pending swap proposals where this item was offered
      List<Order> pendingSwapOffers = orderRepository.findByOfferedListingIdAndStatus(listing.getId(), OrderStatus.REQUESTED);
      for (Order order : pendingSwapOffers) {
        order.setStatus(OrderStatus.INVALIDATED);
        order.setMessage("Auto-invalidated: Offered swap item terms were modified by its owner (" + changeSummary + ").");
        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, "UPDATED");
      }
    }

    publishEvent(saved, "UPDATED");
    return saved;
  }

  public List<Listing> getForUser(UserPrincipal principal) {
    return listingRepository.findByUser_Id(principal.getUserId());
  }

  public Listing getById(Long id) {
    return listingRepository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
  }

  public List<RelistingSuggestionResponse> getRelistingSuggestions(UserPrincipal principal) {
    return listingRepository
        .findByUser_IdAndStatusAndRelistingSuggestionSentAtIsNotNullAndRelistingSuggestionDismissedAtIsNull(
            principal.getUserId(), ListingStatus.ACTIVE)
        .stream()
        .map(RelistingSuggestionResponse::from)
        .toList();
  }

  @Transactional
  public void dismissRelistingSuggestion(UserPrincipal principal, Long id) {
    Listing listing = getOwnedListing(principal, id);
    if (listing.getRelistingSuggestionSentAt() == null
        || listing.getRelistingSuggestionDismissedAt() != null) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Relisting suggestion not found");
    }
    listing.setRelistingSuggestionDismissedAt(Instant.now());
    listingRepository.save(listing);
  }

  @Transactional
  @Scheduled(
      initialDelayString = "${reusehub.listings.relisting-initial-delay-ms:60000}",
      fixedDelayString = "${reusehub.listings.relisting-check-interval-ms:86400000}"
  )
  public void generateRelistingSuggestions() {
    Instant cutoff = Instant.now().minus(relistingAfter);
    for (Listing listing : listingRepository
        .findByStatusAndListingTypeNotAndUpdatedAtBeforeAndRelistingSuggestionSentAtIsNull(
            ListingStatus.ACTIVE, ListingType.RENT, cutoff)) {
      listing.setRelistingSuggestionSentAt(Instant.now());
      Listing saved = listingRepository.save(listing);
      RelistingNotificationDto notification = new RelistingNotificationDto(
          "RELISTING",
          "relisting:" + saved.getId() + ":" + saved.getRelistingSuggestionSentAt().toEpochMilli(),
          saved.getId(),
          saved.getTitle(),
          "ReuseHub",
          "Your listing has been inactive for 14 days. " + RelistingSuggestionResponse.from(saved).advice(),
          saved.getRelistingSuggestionSentAt()
      );
      messagingTemplate.convertAndSend(
          "/topic/users/" + saved.getUser().getId() + "/notifications",
          notification
      );
    }
  }

  public List<Listing> search(
      ListingCategory category,
      ItemCondition condition,
      ListingType type,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String keyword
  ) {
    Specification<Listing> spec = Specification.where(ListingSpecifications.isActive())
        .and(ListingSpecifications.hasCategory(category))
        .and(ListingSpecifications.hasCondition(condition))
        .and(ListingSpecifications.hasType(type))
        .and(ListingSpecifications.priceBetween(minPrice, maxPrice))
        .and(ListingSpecifications.keywordLike(keyword));

    return listingRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Transactional
  public void delete(UserPrincipal principal, Long id) {
    Listing listing = getOwnedListing(principal, id);
    rentalService.ensureCanDeleteListing(id);
    listingRepository.delete(listing);
    publishDelete(listing.getUser().getId(), listing.getId());
  }

  @Transactional
  public Listing updateStatus(UserPrincipal principal, Long id, ListingStatus status) {
    Listing listing = getOwnedListing(principal, id);
    rentalService.ensureCanChangeListingStatus(listing, status);
    listing.setStatus(status);
    Listing saved = listingRepository.save(listing);
    if (saved.getStatus() == ListingStatus.ACTIVE) {
      needRequestService.matchListing(saved);
    }
    publishEvent(saved, "UPDATED");
    return saved;
  }

  private Listing getOwnedListing(UserPrincipal principal, Long id) {
    Listing listing = listingRepository.findByIdForUpdate(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    if (!listing.getUser().getId().equals(principal.getUserId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Not listing owner");
    }
    return listing;
  }

  private void validateRentalListing(Listing listing) {
    if (listing.getListingType() != ListingType.RENT) {
      return;
    }
    if (listing.getPrice() == null || listing.getPrice().signum() <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rental price must be greater than zero");
    }
    if (listing.getPrice().stripTrailingZeros().scale() > 2
        || listing.getPrice().compareTo(new BigDecimal("9999999999.99")) > 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rental price supports up to two decimal places and ten whole-number digits");
    }
    if (listing.getRentalPricingPeriod() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a rental pricing period");
    }
    if (listing.getSecurityDeposit() != null && listing.getSecurityDeposit().signum() < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Security deposit cannot be negative");
    }
    if (listing.getSecurityDeposit() != null
        && (listing.getSecurityDeposit().stripTrailingZeros().scale() > 2
            || listing.getSecurityDeposit().compareTo(new BigDecimal("9999999999.99")) > 0)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Security deposit supports up to two decimal places and ten whole-number digits");
    }
    if (listing.getRentalAvailableFrom() != null && listing.getRentalAvailableUntil() != null
        && listing.getRentalAvailableUntil().isBefore(listing.getRentalAvailableFrom())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rental availability end date cannot be before its start date");
    }
    if (listing.getRentalAvailableFrom() != null
        && listing.getRentalAvailableFrom().isBefore(LocalDate.now(rentalDateZone))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rental availability cannot start in the past");
    }
    if (listing.getMinimumRentalDays() != null && listing.getMinimumRentalDays() < 1) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum rental duration must be at least one day");
    }
    if (listing.getMaximumRentalDays() != null && listing.getMaximumRentalDays() < 1) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum rental duration must be at least one day");
    }
    if (listing.getMinimumRentalDays() != null && listing.getMaximumRentalDays() != null
        && listing.getMinimumRentalDays() > listing.getMaximumRentalDays()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum rental duration cannot exceed maximum duration");
    }
  }

  private void publishEvent(Listing listing, String action) {
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

  private void publishOrderEvent(Order order, String action) {
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

  private void publishDelete(Long userId, Long listingId) {
    ListingEventResponse payload = new ListingEventResponse(
        "DELETED",
        listingId,
        null
    );
    messagingTemplate.convertAndSend(
        "/topic/listings/" + userId,
        payload
    );
  }
}
