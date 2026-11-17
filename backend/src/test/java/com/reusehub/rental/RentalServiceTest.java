package com.reusehub.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reusehub.auth.UserPrincipal;
import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.listing.ListingStatus;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.RentalPricingPeriod;
import com.reusehub.rental.dto.RentalCreateRequest;
import com.reusehub.rental.dto.RentalStatusUpdateRequest;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class RentalServiceTest {
  @Mock private RentalRepository rentalRepository;
  @Mock private ListingRepository listingRepository;
  @Mock private UserRepository userRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private EntityManager entityManager;

  private RentalService rentalService;
  private User owner;
  private User renter;
  private Listing listing;

  @BeforeEach
  void setUp() {
    rentalService = new RentalService(
        rentalRepository,
        listingRepository,
        userRepository,
        messagingTemplate,
        entityManager,
        Duration.ofDays(7),
        "Asia/Kolkata"
    );
    owner = new User();
    owner.setId(10L);
    owner.setName("Owner");
    renter = new User();
    renter.setId(20L);
    renter.setName("Renter");
    listing = new Listing();
    listing.setId(100L);
    listing.setUser(owner);
    listing.setListingType(ListingType.RENT);
    listing.setStatus(ListingStatus.ACTIVE);
    listing.setPrice(new BigDecimal("25.50"));
    listing.setSecurityDeposit(new BigDecimal("40.00"));
    listing.setRentalPricingPeriod(RentalPricingPeriod.PER_DAY);
  }

  @Test
  void createsRentalWithServerCalculatedInclusiveEstimate() {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
    LocalDate end = start.plusDays(2);
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    when(rentalRepository.findByListing_IdAndStatusIn(100L, Set.of(
        RentalStatus.PENDING, RentalStatus.APPROVED, RentalStatus.READY_FOR_HANDOVER,
        RentalStatus.ACTIVE, RentalStatus.RETURN_REQUESTED, RentalStatus.DISPUTED
    ))).thenReturn(List.of());
    when(userRepository.findById(20L)).thenReturn(Optional.of(renter));
    when(rentalRepository.save(any(RentalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    rentalService.create(new UserPrincipal(20L, "renter@example.edu"),
        new RentalCreateRequest(100L, start, end, "Please message me"));

    ArgumentCaptor<RentalRequest> saved = ArgumentCaptor.forClass(RentalRequest.class);
    verify(rentalRepository).save(saved.capture());
    assertEquals(new BigDecimal("76.50"), saved.getValue().getRentalAmount());
    assertEquals(new BigDecimal("40.00"), saved.getValue().getSecurityDeposit());
    assertEquals(RentalStatus.PENDING, saved.getValue().getStatus());
    assertEquals(start, saved.getValue().getStartDate());
    assertEquals(end, saved.getValue().getEndDate());
    assertEquals(RentalStatus.PENDING, saved.getValue().getStatusHistory().get(0).getNewStatus());
  }

  @Test
  void rejectsRequestsForOwnListing() {
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.create(new UserPrincipal(10L, "owner@example.edu"),
            new RentalCreateRequest(100L, start, start, null)));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
  }

  @Test
  void rejectsPastRentalDates() {
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    LocalDate past = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.create(new UserPrincipal(20L, "renter@example.edu"),
            new RentalCreateRequest(100L, past, past, null)));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
  }

  @Test
  void rejectsNonPositiveRentalRatesBeforeCreatingARequest() {
    listing.setPrice(BigDecimal.ZERO);
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.create(new UserPrincipal(20L, "renter@example.edu"),
            new RentalCreateRequest(100L, start, start, null)));

    assertEquals(HttpStatus.CONFLICT, exception.getStatus());
  }

  @Test
  void rejectsDuplicateOverlappingRequestsFromTheSameRenter() {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
    RentalRequest existing = rental(start, start.plusDays(1), RentalStatus.PENDING, UUID.randomUUID());
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    when(rentalRepository.findByListing_IdAndStatusIn(100L, Set.of(
        RentalStatus.PENDING, RentalStatus.APPROVED, RentalStatus.READY_FOR_HANDOVER,
        RentalStatus.ACTIVE, RentalStatus.RETURN_REQUESTED, RentalStatus.DISPUTED
    ))).thenReturn(List.of(existing));

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.create(new UserPrincipal(20L, "renter@example.edu"),
            new RentalCreateRequest(100L, start.plusDays(1), start.plusDays(1), null)));

    assertEquals(HttpStatus.CONFLICT, exception.getStatus());
  }

  @Test
  void rejectsApprovingAnOverlappingConfirmedRental() {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
    RentalRequest pending = rental(start, start.plusDays(2), RentalStatus.PENDING, UUID.randomUUID());
    RentalRequest approved = rental(start.plusDays(1), start.plusDays(3), RentalStatus.APPROVED, UUID.randomUUID());
    when(rentalRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(rentalRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    when(rentalRepository.findByListing_IdAndStatusIn(100L, Set.of(
        RentalStatus.APPROVED, RentalStatus.READY_FOR_HANDOVER, RentalStatus.ACTIVE,
        RentalStatus.RETURN_REQUESTED, RentalStatus.DISPUTED
    ))).thenReturn(List.of(approved));

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.updateStatus(new UserPrincipal(10L, "owner@example.edu"), pending.getId(),
            new RentalStatusUpdateRequest(RentalStatus.APPROVED, null)));

    assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    assertEquals(RentalStatus.PENDING, pending.getStatus());
  }

  @Test
  void expiresARequestInsteadOfApprovingItAfterItsDeadline() {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
    RentalRequest pending = rental(start, start.plusDays(1), RentalStatus.PENDING, UUID.randomUUID());
    pending.setExpiresAt(Instant.now().minusSeconds(1));
    when(rentalRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
    when(rentalRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
    when(listingRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(listing));
    when(rentalRepository.save(any(RentalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var response = rentalService.updateStatus(new UserPrincipal(10L, "owner@example.edu"), pending.getId(),
        new RentalStatusUpdateRequest(RentalStatus.APPROVED, null));

    assertEquals(RentalStatus.EXPIRED, response.status());
    assertEquals(RentalStatus.EXPIRED, pending.getStatus());
  }

  @Test
  void preventsUnrelatedUserFromReadingRental() {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
    RentalRequest rental = rental(start, start, RentalStatus.PENDING, UUID.randomUUID());
    when(rentalRepository.findById(rental.getId())).thenReturn(Optional.of(rental));

    ApiException exception = assertThrows(ApiException.class, () ->
        rentalService.get(new UserPrincipal(99L, "other@example.edu"), rental.getId()));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
  }

  private RentalRequest rental(LocalDate start, LocalDate end, RentalStatus status, UUID id) {
    RentalRequest rental = new RentalRequest();
    rental.setId(id);
    rental.setListing(listing);
    rental.setOwner(owner);
    rental.setRenter(renter);
    rental.setStartDate(start);
    rental.setEndDate(end);
    rental.setDailyRate(listing.getPrice());
    rental.setPricingPeriod(RentalPricingPeriod.PER_DAY);
    rental.setRentalAmount(listing.getPrice());
    rental.setSecurityDeposit(BigDecimal.ZERO);
    rental.setStatus(status);
    rental.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
    return rental;
  }
}
