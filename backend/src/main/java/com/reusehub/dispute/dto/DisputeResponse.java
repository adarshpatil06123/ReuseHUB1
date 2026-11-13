package com.reusehub.dispute.dto;

import com.reusehub.dispute.DisputeStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisputeResponse(
    UUID id,
    UUID orderId,
    String listingTitle,
    String reason,
    DisputeStatus status,
    String openedBy,
    Instant openedAt,
    Instant buyerCloseConfirmedAt,
    Instant sellerCloseConfirmedAt,
    Instant closedAt,
    List<DisputeEvidenceResponse> evidence
) {}
