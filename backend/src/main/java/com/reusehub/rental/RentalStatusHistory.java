package com.reusehub.rental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rental_status_history", indexes = {
    @Index(name = "idx_rental_history_rental_changed", columnList = "rental_id,changed_at")
})
public class RentalStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rental_id", nullable = false)
  private RentalRequest rental;

  @Enumerated(EnumType.STRING)
  private RentalStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RentalStatus newStatus;

  private Long changedByUserId;

  @Column(length = 1000)
  private String reason;

  @Column(nullable = false)
  private Instant changedAt;

  protected RentalStatusHistory() {}

  public RentalStatusHistory(RentalRequest rental, RentalStatus previousStatus, RentalStatus newStatus,
      Long changedByUserId, String reason) {
    this.rental = rental;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.changedByUserId = changedByUserId;
    this.reason = reason;
  }

  @PrePersist
  public void prePersist() {
    changedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public RentalStatus getPreviousStatus() { return previousStatus; }
  public RentalStatus getNewStatus() { return newStatus; }
  public Long getChangedByUserId() { return changedByUserId; }
  public String getReason() { return reason; }
  public Instant getChangedAt() { return changedAt; }
}
