package com.reusehub.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "chat_threads", uniqueConstraints = {
    @UniqueConstraint(columnNames = "thread_key")
})
public class ChatThread {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "listing_id", nullable = false)
  private Long listingId;

  @Column(name = "participant_low_id", nullable = false)
  private Long participantLowId;

  @Column(name = "participant_high_id", nullable = false)
  private Long participantHighId;

  @Column(name = "thread_key", nullable = false, unique = true)
  private String threadKey;

  private Instant createdAt;

  private Instant updatedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getListingId() {
    return listingId;
  }

  public void setListingId(Long listingId) {
    this.listingId = listingId;
  }

  public Long getParticipantLowId() {
    return participantLowId;
  }

  public void setParticipantLowId(Long participantLowId) {
    this.participantLowId = participantLowId;
  }

  public Long getParticipantHighId() {
    return participantHighId;
  }

  public void setParticipantHighId(Long participantHighId) {
    this.participantHighId = participantHighId;
  }

  public String getThreadKey() {
    return threadKey;
  }

  public void setThreadKey(String threadKey) {
    this.threadKey = threadKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
