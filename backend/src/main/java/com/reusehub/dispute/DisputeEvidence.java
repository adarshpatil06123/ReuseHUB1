package com.reusehub.dispute;

import com.reusehub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispute_evidence", uniqueConstraints = {
    @UniqueConstraint(name = "uk_evidence_dispute_sequence", columnNames = {"dispute_id", "sequence_number"})
})
public class DisputeEvidence {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dispute_id", nullable = false)
  private Dispute dispute;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by_user_id", nullable = false)
  private User uploadedBy;

  @Column(name = "sequence_number", nullable = false)
  private long sequenceNumber;

  @Column(nullable = false, length = 255)
  private String originalFilename;

  @Column(nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false)
  private long sizeBytes;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Column(nullable = false, length = 64)
  private String previousRecordHash;

  @Column(nullable = false, length = 64)
  private String recordHash;

  @Column(nullable = false, length = 80, unique = true)
  private String storageKey;

  @Column(nullable = false)
  private Instant uploadedAt;

  @PrePersist
  public void prePersist() {
    if (uploadedAt == null) {
      uploadedAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public Dispute getDispute() {
    return dispute;
  }

  public void setDispute(Dispute dispute) {
    this.dispute = dispute;
  }

  public User getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(User uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public String getPreviousRecordHash() {
    return previousRecordHash;
  }

  public void setPreviousRecordHash(String previousRecordHash) {
    this.previousRecordHash = previousRecordHash;
  }

  public String getRecordHash() {
    return recordHash;
  }

  public void setRecordHash(String recordHash) {
    this.recordHash = recordHash;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
