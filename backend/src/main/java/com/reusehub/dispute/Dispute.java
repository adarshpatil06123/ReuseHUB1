package com.reusehub.dispute;

import com.reusehub.order.Order;
import com.reusehub.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes")
public class Dispute {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, unique = true)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "opened_by_user_id", nullable = false)
  private User openedBy;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DisputeStatus status = DisputeStatus.OPEN;

  @Column(nullable = false)
  private Instant openedAt;

  private Instant buyerCloseConfirmedAt;

  private Instant sellerCloseConfirmedAt;

  private Instant closedAt;

  @PrePersist
  public void prePersist() {
    openedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Order getOrder() {
    return order;
  }

  public void setOrder(Order order) {
    this.order = order;
  }

  public User getOpenedBy() {
    return openedBy;
  }

  public void setOpenedBy(User openedBy) {
    this.openedBy = openedBy;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public DisputeStatus getStatus() {
    return status;
  }

  public void setStatus(DisputeStatus status) {
    this.status = status;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public Instant getBuyerCloseConfirmedAt() {
    return buyerCloseConfirmedAt;
  }

  public void setBuyerCloseConfirmedAt(Instant buyerCloseConfirmedAt) {
    this.buyerCloseConfirmedAt = buyerCloseConfirmedAt;
  }

  public Instant getSellerCloseConfirmedAt() {
    return sellerCloseConfirmedAt;
  }

  public void setSellerCloseConfirmedAt(Instant sellerCloseConfirmedAt) {
    this.sellerCloseConfirmedAt = sellerCloseConfirmedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }
}
