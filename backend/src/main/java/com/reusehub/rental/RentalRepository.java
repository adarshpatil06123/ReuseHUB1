package com.reusehub.rental;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository extends JpaRepository<RentalRequest, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RentalRequest r where r.id = :id")
  Optional<RentalRequest> findByIdForUpdate(@Param("id") UUID id);

  Page<RentalRequest> findByRenter_IdOrOwner_IdOrderByCreatedAtDesc(Long renterId, Long ownerId, Pageable pageable);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<RentalRequest> findByListing_IdAndStatusIn(Long listingId, Collection<RentalStatus> statuses);
  List<RentalRequest> findByStatusAndExpiresAtBefore(RentalStatus status, Instant now);
  List<RentalRequest> findByStatusAndStartDateLessThanEqualAndOwnerHandoverConfirmedAtIsNotNullAndRenterHandoverConfirmedAtIsNotNull(
      RentalStatus status, LocalDate today);
  boolean existsByListing_Id(Long listingId);
}
