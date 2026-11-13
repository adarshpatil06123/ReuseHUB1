package com.reusehub.dispute.dto;

import java.time.Instant;
import java.util.UUID;

public record DisputeEvidenceResponse(
    UUID id,
    long sequenceNumber,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String sha256,
    String previousRecordHash,
    String recordHash,
    boolean integrityVerified,
    String uploadedBy,
    Instant uploadedAt
) {}
