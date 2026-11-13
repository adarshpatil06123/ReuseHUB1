package com.reusehub.need;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeedRequestRepository extends JpaRepository<NeedRequest, UUID> {
  List<NeedRequest> findByUser_IdOrderByCreatedAtDesc(Long userId);

  List<NeedRequest> findByActiveTrue();

  List<NeedRequest> findByActiveTrueAndShowOnHeatmapTrueOrderByCreatedAtDesc();

  Optional<NeedRequest> findByIdAndUser_Id(UUID id, Long userId);

  void deleteByIdAndUser_Id(UUID id, Long userId);

  @Query("""
      select nr.preferredLocation as location, count(nr) as count
      from NeedRequest nr
      where nr.active = true and nr.showOnHeatmap = true
      group by nr.preferredLocation
      """)
  List<PublicNeedDemand> countPublicNeedRequestsByLocation();

  interface PublicNeedDemand {
    String getLocation();

    long getCount();
  }
}
