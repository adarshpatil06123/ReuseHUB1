package com.reusehub.rental.dto;

import com.reusehub.rental.RentalStatus;
import com.reusehub.rental.RentalStatusHistory;
import java.time.Instant;

public record RentalHistoryResponse(
    RentalStatus previousStatus,
    RentalStatus newStatus,
    Long changedByUserId,
    String reason,
    Instant changedAt
) {
  public static RentalHistoryResponse from(RentalStatusHistory history) {
    return new RentalHistoryResponse(
        history.getPreviousStatus(),
        history.getNewStatus(),
        history.getChangedByUserId(),
        history.getReason(),
        history.getChangedAt()
    );
  }
}
