package com.reusehub.listing;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from Listing l where l.id = :id")
	Optional<Listing> findByIdForUpdate(@Param("id") Long id);

	List<Listing> findByUser_Id(Long userId);
	List<Listing> findByStatus(ListingStatus status);
	List<Listing> findByStatusAndUpdatedAtBeforeAndRelistingSuggestionSentAtIsNull(
			ListingStatus status, Instant cutoff);
	List<Listing> findByStatusAndListingTypeNotAndUpdatedAtBeforeAndRelistingSuggestionSentAtIsNull(
			ListingStatus status, ListingType excludedType, Instant cutoff);
	List<Listing> findByUser_IdAndStatusAndRelistingSuggestionSentAtIsNotNullAndRelistingSuggestionDismissedAtIsNull(
			Long userId, ListingStatus status);
}
