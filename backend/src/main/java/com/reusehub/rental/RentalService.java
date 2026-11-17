package com.reusehub.rental;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.RentalPricingPeriod;
import com.reusehub.rental.dto.RentalCreateRequest;
import com.reusehub.rental.dto.RentalNotificationDto;
import com.reusehub.rental.dto.RentalPageResponse;
import com.reusehub.rental.dto.RentalResponse;
import com.reusehub.rental.dto.RentalStatusUpdateRequest;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RentalService {
  private static final Logger log = LoggerFactory.getLogger(RentalService.class);
  private static final Set<RentalStatus> BOOKING_STATUSES = Set.of(
      RentalStatus.APPROVED,
      RentalStatus.READY_FOR_HANDOVER,
      RentalStatus.ACTIVE,
      RentalStatus.RETURN_REQUESTED,
      RentalStatus.DISPUTED
  );
  private static final Set<RentalStatus> LISTING_LOCK_STATUSES = Set.of(
      RentalStatus.PENDING,
      RentalStatus.APPROVED,
      RentalStatus.READY_FOR_HANDOVER,
      RentalStatus.ACTIVE,
      RentalStatus.RETURN_REQUESTED,
      RentalStatus.DISPUTED
  );

  private final RentalRepository rentalRepository;
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;
  private final SimpMessagingTemplate messagingTemplate;
  private final EntityManager entityManager;
  private final Duration requestExpiry;
  private final ZoneId dateZone;

  public RentalService(
      RentalRepository rentalRepository,
      ListingRepository listingRepository,
      UserRepository userRepository,
      SimpMessagingTemplate messagingTemplate,
      EntityManager entityManager,
      @Value("${reusehub.rentals.request-expiry:PT168H}") Duration requestExpiry,
      @Value("${reusehub.rentals.time-zone:Asia/Kolkata}") String timeZone
  ) {
    this.rentalRepository = rentalRepository;
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
    this.messagingTemplate = messagingTemplate;
    this.entityManager = entityManager;
    this.requestExpiry = requestExpiry;
    this.dateZone = ZoneId.of(timeZone);
  }

  private boolean isMoneyValid(BigDecimal amount) {
    return amount != null
        && amount.stripTrailingZeros().scale() <= 2
        && amount.compareTo(new BigDecimal("9999999999.99")) <= 0;
  }

  @Transactional
  public RentalResponse create(UserPrincipal principal, RentalCreateRequest request) {
    Listing listing = listingRepository.findByIdForUpdate(request.listingId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    validateRequest(listing, principal.getUserId(), request.startDate(), request.endDate());

    boolean duplicate = rentalRepository.findByListing_IdAndStatusIn(listing.getId(), LISTING_LOCK_STATUSES)
        .stream()
        .anyMatch(existing -> existing.getRenter().getId().equals(principal.getUserId())
            && overlaps(request.startDate(), request.endDate(), existing.getStartDate(), existing.getEndDate()));
    if (duplicate) {
      throw new ApiException(HttpStatus.CONFLICT, "You already have an active rental request for overlapping dates");
    }

    User renter = userRepository.findById(principal.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    long rentalDays = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    BigDecimal rate = listing.getPrice();
    BigDecimal deposit = listing.getSecurityDeposit() == null ? BigDecimal.ZERO : listing.getSecurityDeposit();
    BigDecimal rentalAmount = rate.multiply(BigDecimal.valueOf(rentalDays));
    if (!isMoneyValid(rentalAmount)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "The estimated rental amount exceeds the supported range");
    }

    RentalRequest rental = new RentalRequest();
    rental.setListing(listing);
    rental.setRenter(renter);
    rental.setOwner(listing.getUser());
    rental.setStartDate(request.startDate());
    rental.setEndDate(request.endDate());
    rental.setDailyRate(rate);
    rental.setPricingPeriod(listing.getRentalPricingPeriod());
    rental.setRentalAmount(rentalAmount);
    rental.setSecurityDeposit(deposit);
    rental.setMessage(normalizeMessage(request.message()));
    rental.setExpiresAt(Instant.now().plus(requestExpiry));
    rental.addStatusHistory(null, RentalStatus.PENDING, renter.getId(), "Rental request submitted");

    RentalRequest saved = rentalRepository.save(rental);
    notifyUser(saved.getOwner().getId(), saved, renter.getName(), "A student requested to rent your item.", "PENDING");
    return RentalResponse.from(saved, renter.getId());
  }

  @Transactional(readOnly = true)
  public RentalPageResponse listForUser(UserPrincipal principal, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Rental page must be non-negative and page size must be between 1 and 100");
    }
    Page<RentalRequest> rentals = rentalRepository.findByRenter_IdOrOwner_IdOrderByCreatedAtDesc(
        principal.getUserId(), principal.getUserId(), PageRequest.of(page, size));
    return new RentalPageResponse(
        rentals.getContent().stream()
            .map(rental -> RentalResponse.from(rental, principal.getUserId()))
            .toList(),
        rentals.getNumber(),
        rentals.getSize(),
        rentals.getTotalElements(),
        rentals.getTotalPages()
    );
  }

  @Transactional(readOnly = true)
  public RentalResponse get(UserPrincipal principal, UUID rentalId) {
    RentalRequest rental = findRental(rentalId);
    requireParticipant(rental, principal.getUserId());
    return RentalResponse.from(rental, principal.getUserId());
  }

  @Transactional
  public RentalResponse updateStatus(
      UserPrincipal principal,
      UUID rentalId,
      RentalStatusUpdateRequest request
  ) {
    RentalRequest rental = findRental(rentalId);
    requireParticipant(rental, principal.getUserId());
    lockListing(rental);
    rental = lockRental(rental.getId());
    requireParticipant(rental, principal.getUserId());
    RentalStatus previous = rental.getStatus();
    RentalStatus target = request.status();
    String reason = normalizeMessage(request.reason());
    boolean owner = rental.getOwner().getId().equals(principal.getUserId());

    if (target == RentalStatus.APPROVED || target == RentalStatus.REJECTED) {
      if (!owner) {
        throw new ApiException(HttpStatus.FORBIDDEN, "Only the listing owner can approve or reject a rental request");
      }
      if (previous != RentalStatus.PENDING) {
        throw new ApiException(HttpStatus.CONFLICT, "Only pending rental requests can be approved or rejected");
      }
      if (!Instant.now().isBefore(rental.getExpiresAt())) {
        transition(rental, RentalStatus.EXPIRED, null, "Request expired before owner response");
        RentalRequest expired = rentalRepository.save(rental);
        notifyBoth(expired, "ReuseHub", "The rental request expired without an owner response.",
            RentalStatus.EXPIRED.name());
        return RentalResponse.from(expired, principal.getUserId());
      }
      if (target == RentalStatus.APPROVED) {
        Listing listing = rental.getListing();
        if (listing.getStatus() == null || listing.getListingType() != ListingType.RENT
            || listing.getStatus() != com.reusehub.listing.ListingStatus.ACTIVE) {
          throw new ApiException(HttpStatus.CONFLICT, "This rental listing is no longer available");
        }
        ensureNoBookingOverlap(rental);
      }
      transition(rental, target, principal.getUserId(), reason);
      if (target == RentalStatus.APPROVED) {
        notifyUser(rental.getRenter().getId(), rental, rental.getOwner().getName(),
            "Your rental request was approved.", target.name());
      } else {
        notifyUser(rental.getRenter().getId(), rental, rental.getOwner().getName(),
            reason == null ? "Your rental request was rejected." : "Your rental request was rejected: " + reason,
            target.name());
      }
    } else if (target == RentalStatus.CANCELLED) {
      if (previous != RentalStatus.PENDING && previous != RentalStatus.APPROVED
          && previous != RentalStatus.READY_FOR_HANDOVER) {
        throw new ApiException(HttpStatus.CONFLICT, "This rental can no longer be cancelled");
      }
      if (previous != RentalStatus.PENDING && !LocalDate.now(dateZone).isBefore(rental.getStartDate())) {
        throw new ApiException(HttpStatus.CONFLICT, "An approved rental cannot be cancelled on or after its start date");
      }
      transition(rental, target, principal.getUserId(), reason);
      Long otherPartyId = owner ? rental.getRenter().getId() : rental.getOwner().getId();
      notifyUser(otherPartyId, rental, owner ? rental.getOwner().getName() : rental.getRenter().getName(),
          "The rental was cancelled.", target.name());
    } else if (target == RentalStatus.COMPLETED) {
      if (!owner) {
        throw new ApiException(HttpStatus.FORBIDDEN, "Only the listing owner can confirm a returned item");
      }
      if (previous != RentalStatus.RETURN_REQUESTED) {
        throw new ApiException(HttpStatus.CONFLICT, "The renter must request a return before it can be completed");
      }
      rental.setReturnedAt(Instant.now());
      rental.setCompletedAt(rental.getReturnedAt());
      transition(rental, target, principal.getUserId(), reason);
      notifyUser(rental.getRenter().getId(), rental, rental.getOwner().getName(),
          "The owner confirmed the return. The rental is complete; no payment or deposit was processed by ReuseHub.",
          target.name());
    } else {
      throw new ApiException(HttpStatus.BAD_REQUEST, "This status cannot be set with this operation");
    }

    RentalRequest saved = rentalRepository.save(rental);
    return RentalResponse.from(saved, principal.getUserId());
  }

  @Transactional
  public RentalResponse confirmHandover(UserPrincipal principal, UUID rentalId) {
    RentalRequest rental = findRental(rentalId);
    requireParticipant(rental, principal.getUserId());
    lockListing(rental);
    rental = lockRental(rental.getId());
    requireParticipant(rental, principal.getUserId());
    if (rental.getStatus() != RentalStatus.APPROVED && rental.getStatus() != RentalStatus.READY_FOR_HANDOVER) {
      throw new ApiException(HttpStatus.CONFLICT, "Only an approved rental can be handed over");
    }
    boolean owner = rental.getOwner().getId().equals(principal.getUserId());
    Instant now = Instant.now();
    if (owner) {
      if (rental.getOwnerHandoverConfirmedAt() != null) {
        return RentalResponse.from(rental, principal.getUserId());
      }
      rental.setOwnerHandoverConfirmedAt(now);
    } else {
      if (rental.getRenterHandoverConfirmedAt() != null) {
        return RentalResponse.from(rental, principal.getUserId());
      }
      rental.setRenterHandoverConfirmedAt(now);
    }

    RentalStatus target = RentalStatus.READY_FOR_HANDOVER;
    if (rental.getOwnerHandoverConfirmedAt() != null && rental.getRenterHandoverConfirmedAt() != null
        && !LocalDate.now(dateZone).isBefore(rental.getStartDate())) {
      target = RentalStatus.ACTIVE;
    }
    if (target != rental.getStatus()) {
      transition(rental, target, principal.getUserId(), owner ? "Owner confirmed handover" : "Renter acknowledged receipt");
    }
    RentalRequest saved = rentalRepository.save(rental);
    Long otherPartyId = owner ? rental.getRenter().getId() : rental.getOwner().getId();
    notifyUser(otherPartyId, saved, owner ? rental.getOwner().getName() : rental.getRenter().getName(),
        target == RentalStatus.ACTIVE ? "Both parties confirmed handover. The rental is now active."
            : "A participant confirmed the rental handover.",
        target.name());
    return RentalResponse.from(saved, principal.getUserId());
  }

  @Transactional
  public RentalResponse requestReturn(UserPrincipal principal, UUID rentalId) {
    RentalRequest rental = findRental(rentalId);
    requireParticipant(rental, principal.getUserId());
    if (!rental.getRenter().getId().equals(principal.getUserId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only the renter can request an item return");
    }
    lockListing(rental);
    rental = lockRental(rental.getId());
    requireParticipant(rental, principal.getUserId());
    if (!rental.getRenter().getId().equals(principal.getUserId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only the renter can request an item return");
    }
    if (rental.getStatus() != RentalStatus.ACTIVE) {
      throw new ApiException(HttpStatus.CONFLICT, "Only an active rental can be returned");
    }
    rental.setReturnRequestedAt(Instant.now());
    transition(rental, RentalStatus.RETURN_REQUESTED, principal.getUserId(), "Renter requested return");
    RentalRequest saved = rentalRepository.save(rental);
    notifyUser(saved.getOwner().getId(), saved, saved.getRenter().getName(),
        "The renter has requested to return your item.", RentalStatus.RETURN_REQUESTED.name());
    return RentalResponse.from(saved, principal.getUserId());
  }

  @Transactional
  public RentalResponse raiseDispute(UserPrincipal principal, UUID rentalId, String reason) {
    RentalRequest rental = findRental(rentalId);
    requireParticipant(rental, principal.getUserId());
    lockListing(rental);
    rental = lockRental(rental.getId());
    requireParticipant(rental, principal.getUserId());
    String normalizedReason = normalizeMessage(reason);
    if (normalizedReason == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required to report a return issue");
    }
    if (rental.getStatus() != RentalStatus.ACTIVE && rental.getStatus() != RentalStatus.RETURN_REQUESTED) {
      throw new ApiException(HttpStatus.CONFLICT, "A return issue can only be reported during an active rental or return");
    }
    transition(rental, RentalStatus.DISPUTED, principal.getUserId(), normalizedReason);
    RentalRequest saved = rentalRepository.save(rental);
    Long otherPartyId = saved.getOwner().getId().equals(principal.getUserId())
        ? saved.getRenter().getId() : saved.getOwner().getId();
    notifyUser(otherPartyId, saved, participantName(saved, principal.getUserId()),
        "A return issue was reported: " + normalizedReason, RentalStatus.DISPUTED.name());
    return RentalResponse.from(saved, principal.getUserId());
  }

  @Transactional
  public void beforeListingUpdate(Listing current, Listing updated) {
    List<RentalRequest> requests = rentalRepository.findByListing_IdAndStatusIn(
        current.getId(), LISTING_LOCK_STATUSES);
    boolean typeChanged = current.getListingType() != updated.getListingType();
    boolean rentalTermsChanged = typeChanged
        || !java.util.Objects.equals(current.getPrice(), updated.getPrice())
        || current.getRentalPricingPeriod() != updated.getRentalPricingPeriod()
        || !java.util.Objects.equals(current.getSecurityDeposit(), updated.getSecurityDeposit())
        || !java.util.Objects.equals(current.getRentalAvailableFrom(), updated.getRentalAvailableFrom())
        || !java.util.Objects.equals(current.getRentalAvailableUntil(), updated.getRentalAvailableUntil())
        || !java.util.Objects.equals(current.getMinimumRentalDays(), updated.getMinimumRentalDays())
        || !java.util.Objects.equals(current.getMaximumRentalDays(), updated.getMaximumRentalDays())
        || !java.util.Objects.equals(current.getRentalInstructions(), updated.getRentalInstructions());

    for (RentalRequest rental : requests) {
      if (BOOKING_STATUSES.contains(rental.getStatus())) {
        if (typeChanged && updated.getListingType() != ListingType.RENT) {
          throw new ApiException(HttpStatus.CONFLICT,
              "The listing cannot be changed from Rent while an approved or active rental exists");
        }
        if (updated.getListingType() == ListingType.RENT
            && ((updated.getRentalAvailableFrom() != null
                    && rental.getStartDate().isBefore(updated.getRentalAvailableFrom()))
                || (updated.getRentalAvailableUntil() != null
                    && rental.getEndDate().isAfter(updated.getRentalAvailableUntil())))) {
          throw new ApiException(HttpStatus.CONFLICT,
              "Availability cannot be changed to exclude an approved or active rental");
        }
      }
    }

    if (rentalTermsChanged) {
      rejectPendingRequests(requests, current.getUser().getId(),
          "Listing rental terms changed. Please review the updated terms and submit a new request.");
    }
  }

  @Transactional
  public void ensureCanDeleteListing(Long listingId) {
    if (rentalRepository.existsByListing_Id(listingId)) {
      throw new ApiException(HttpStatus.CONFLICT,
          "This listing has rental requests or history and cannot be deleted. Deactivate it after resolving open rentals.");
    }
  }

  @Transactional
  public void ensureCanChangeListingStatus(Listing listing, com.reusehub.listing.ListingStatus status) {
    if (listing.getListingType() == ListingType.RENT && status != com.reusehub.listing.ListingStatus.ACTIVE) {
      if (status != com.reusehub.listing.ListingStatus.INACTIVE) {
        throw new ApiException(HttpStatus.CONFLICT,
            "Rental listings can only be active or inactive; manage availability with rental dates");
      }
      if (!rentalRepository.findByListing_IdAndStatusIn(listing.getId(), LISTING_LOCK_STATUSES).isEmpty()) {
        throw new ApiException(HttpStatus.CONFLICT,
            "Resolve pending or approved rentals before deactivating this listing");
      }
    }
  }

  @Transactional
  @Scheduled(
      initialDelayString = "${reusehub.rentals.maintenance-initial-delay-ms:60000}",
      fixedDelayString = "${reusehub.rentals.maintenance-interval-ms:60000}"
  )
  public void updateExpiredAndReadyRentals() {
    Instant now = Instant.now();
    for (RentalRequest candidate : rentalRepository.findByStatusAndExpiresAtBefore(RentalStatus.PENDING, now)) {
      expireIfStillPending(candidate.getId(), now);
    }
    LocalDate today = LocalDate.now(dateZone);
    for (RentalRequest candidate : rentalRepository
        .findByStatusAndStartDateLessThanEqualAndOwnerHandoverConfirmedAtIsNotNullAndRenterHandoverConfirmedAtIsNotNull(
            RentalStatus.READY_FOR_HANDOVER, today)) {
      activateIfReady(candidate.getId());
    }
  }

  private void expireIfStillPending(UUID id, Instant now) {
    RentalRequest rental = rentalRepository.findById(id).orElse(null);
    if (rental == null) return;
    lockListing(rental);
    rental = lockRental(rental.getId());
    if (rental.getStatus() == RentalStatus.PENDING && rental.getExpiresAt().isBefore(now)) {
      transition(rental, RentalStatus.EXPIRED, null, "Request expired before owner response");
      RentalRequest saved = rentalRepository.save(rental);
      notifyUser(saved.getRenter().getId(), saved, saved.getOwner().getName(),
          "Your rental request expired without an owner response.", RentalStatus.EXPIRED.name());
      notifyUser(saved.getOwner().getId(), saved, "ReuseHub",
          "A rental request expired without your response.", RentalStatus.EXPIRED.name());
    }
  }

  private void activateIfReady(UUID id) {
    RentalRequest rental = rentalRepository.findById(id).orElse(null);
    if (rental == null) return;
    lockListing(rental);
    rental = lockRental(rental.getId());
    if (rental.getStatus() == RentalStatus.READY_FOR_HANDOVER
        && rental.getOwnerHandoverConfirmedAt() != null
        && rental.getRenterHandoverConfirmedAt() != null
        && !LocalDate.now(dateZone).isBefore(rental.getStartDate())) {
      transition(rental, RentalStatus.ACTIVE, null, "Rental start date reached after both handover confirmations");
      RentalRequest saved = rentalRepository.save(rental);
      notifyBoth(saved, "ReuseHub", "The rental is now active.", RentalStatus.ACTIVE.name());
    }
  }

  private void validateRequest(Listing listing, Long renterId, LocalDate startDate, LocalDate endDate) {
    if (listing.getListingType() != ListingType.RENT) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "This listing is not available for rent");
    }
    if (listing.getStatus() != com.reusehub.listing.ListingStatus.ACTIVE) {
      throw new ApiException(HttpStatus.CONFLICT, "This listing is not currently available");
    }
    if (listing.getUser().getId().equals(renterId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot rent your own listing");
    }
    if (startDate == null || endDate == null || startDate.isBefore(LocalDate.now(dateZone))
        || endDate.isBefore(startDate)) {
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "Choose a valid rental period with a start date today or later and an end date on or after it");
    }
    if (listing.getPrice() == null || listing.getPrice().signum() <= 0 || !isMoneyValid(listing.getPrice())) {
      throw new ApiException(HttpStatus.CONFLICT, "This listing has an invalid rental rate");
    }
    if (listing.getRentalPricingPeriod() == null) {
      throw new ApiException(HttpStatus.CONFLICT, "This listing has no rental pricing period");
    }
    if (listing.getRentalAvailableFrom() != null && startDate.isBefore(listing.getRentalAvailableFrom())) {
      throw new ApiException(HttpStatus.CONFLICT, "The requested start date is outside the listing's availability");
    }
    if (listing.getRentalAvailableUntil() != null && endDate.isAfter(listing.getRentalAvailableUntil())) {
      throw new ApiException(HttpStatus.CONFLICT, "The requested end date is outside the listing's availability");
    }
    long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    if (listing.getMinimumRentalDays() != null && days < listing.getMinimumRentalDays()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "The rental period is shorter than the listing minimum");
    }
    if (listing.getMaximumRentalDays() != null && days > listing.getMaximumRentalDays()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "The rental period exceeds the listing maximum");
    }
    if (listing.getSecurityDeposit() != null
        && (listing.getSecurityDeposit().signum() < 0 || !isMoneyValid(listing.getSecurityDeposit()))) {
      throw new ApiException(HttpStatus.CONFLICT, "This listing has an invalid security deposit");
    }
  }

  private void ensureNoBookingOverlap(RentalRequest requested) {
    boolean conflict = rentalRepository
        .findByListing_IdAndStatusIn(requested.getListing().getId(), BOOKING_STATUSES)
        .stream()
        .anyMatch(existing -> !existing.getId().equals(requested.getId())
            && overlaps(requested.getStartDate(), requested.getEndDate(),
                existing.getStartDate(), existing.getEndDate()));
    if (conflict) {
      throw new ApiException(HttpStatus.CONFLICT,
          "These dates overlap with another approved or active rental");
    }
  }

  private boolean overlaps(LocalDate firstStart, LocalDate firstEnd, LocalDate secondStart, LocalDate secondEnd) {
    return !firstStart.isAfter(secondEnd) && !firstEnd.isBefore(secondStart);
  }

  private void rejectPendingRequests(List<RentalRequest> requests, Long actorId, String reason) {
    for (RentalRequest request : requests) {
      if (request.getStatus() == RentalStatus.PENDING) {
        transition(request, RentalStatus.REJECTED, actorId, reason);
        rentalRepository.save(request);
        notifyUser(request.getRenter().getId(), request, request.getOwner().getName(), reason,
            RentalStatus.REJECTED.name());
      }
    }
  }

  private void transition(RentalRequest rental, RentalStatus target, Long actorId, String reason) {
    RentalStatus previous = rental.getStatus();
    if (previous == target) {
      throw new ApiException(HttpStatus.CONFLICT, "Rental is already in this status");
    }
    rental.setStatus(target);
    rental.setStatusReason(reason);
    rental.addStatusHistory(previous, target, actorId, reason);
  }

  private RentalRequest findRental(UUID rentalId) {
    return rentalRepository.findById(rentalId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rental request not found"));
  }

  private RentalRequest lockRental(UUID rentalId) {
    RentalRequest rental = rentalRepository.findByIdForUpdate(rentalId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rental request not found"));
    entityManager.refresh(rental, LockModeType.PESSIMISTIC_WRITE);
    return rental;
  }

  private void lockListing(RentalRequest rental) {
    listingRepository.findByIdForUpdate(rental.getListing().getId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rental listing not found"));
  }

  private void requireParticipant(RentalRequest rental, Long userId) {
    if (!rental.getOwner().getId().equals(userId) && !rental.getRenter().getId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Only the rental participants can access this request");
    }
  }

  private String participantName(RentalRequest rental, Long userId) {
    return rental.getOwner().getId().equals(userId) ? rental.getOwner().getName() : rental.getRenter().getName();
  }

  private String normalizeMessage(String message) {
    if (message == null || message.isBlank()) return null;
    return message.trim();
  }

  private void notifyBoth(RentalRequest rental, String actorName, String content, String status) {
    notifyUser(rental.getOwner().getId(), rental, actorName, content, status);
    notifyUser(rental.getRenter().getId(), rental, actorName, content, status);
  }

  private void notifyUser(Long userId, RentalRequest rental, String actorName, String content, String status) {
    RentalNotificationDto notification = new RentalNotificationDto(
        "RENTAL",
        "rental:" + rental.getId() + ":" + status + ":" + Instant.now().toEpochMilli(),
        rental.getId(),
        rental.getListing().getId(),
        rental.getListing().getTitle(),
        actorName,
        content,
        status,
        Instant.now()
    );
    Runnable sendNotification = () -> {
      try {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", notification);
      } catch (RuntimeException exception) {
        log.warn("Could not publish rental notification for rental {} to user {}", rental.getId(), userId, exception);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendNotification.run();
        }
      });
    } else {
      sendNotification.run();
    }
  }
}
