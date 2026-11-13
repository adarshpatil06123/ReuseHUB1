package com.reusehub.listing;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
	List<Listing> findByUser_Id(Long userId);
	List<Listing> findByStatus(ListingStatus status);
	List<Listing> findByStatusAndUpdatedAtBeforeAndRelistingSuggestionSentAtIsNull(
			ListingStatus status, Instant cutoff);
	List<Listing> findByUser_IdAndStatusAndRelistingSuggestionSentAtIsNotNullAndRelistingSuggestionDismissedAtIsNull(
			Long userId, ListingStatus status);
}
