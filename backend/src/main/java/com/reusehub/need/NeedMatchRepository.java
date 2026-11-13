package com.reusehub.need;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeedMatchRepository extends JpaRepository<NeedMatch, UUID> {
  List<NeedMatch> findByNeedRequest_IdOrderByMatchedAtDesc(UUID needRequestId);

  boolean existsByNeedRequest_IdAndListing_Id(UUID needRequestId, Long listingId);
}
