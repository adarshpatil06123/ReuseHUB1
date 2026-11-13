package com.reusehub.dispute;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {
  List<DisputeEvidence> findByDispute_IdOrderBySequenceNumberAsc(UUID disputeId);

  Optional<DisputeEvidence> findByIdAndDispute_Id(UUID evidenceId, UUID disputeId);

  Optional<DisputeEvidence> findTopByDispute_IdOrderBySequenceNumberDesc(UUID disputeId);
}
