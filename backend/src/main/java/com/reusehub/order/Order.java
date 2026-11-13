package com.reusehub.order;

import com.reusehub.listing.Listing;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id")
  private Listing listing;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "buyer_id")
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seller_id")
  private User seller;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.REQUESTED;

  private BigDecimal offeredPrice;

  private BigDecimal finalPrice;

  @Column(name = "bundle_id")
  private UUID bundleId;

  private Integer bundlePosition;

  private Integer bundleSize;

  private BigDecimal collateralAmount;

  @Enumerated(EnumType.STRING)
  @Column
  private CollateralStatus collateralStatus = CollateralStatus.NOT_APPLICABLE;

  private Instant collateralLockedAt;

  private Instant collateralSettledAt;

  @Column(name = "offered_listing_id")
  private Long offeredListingId;

  @Column(length = 1000)
  private String message;

  private Instant expiresAt;

  private Instant completedAt;

  private Instant handoffDeadlineAt;

  private Instant buyerHandoffConfirmedAt;

  private Instant sellerHandoffConfirmedAt;

  @Column(name = "handoff_checkin_latitude")
  private Double handoffCheckInLatitude;

  @Column(name = "handoff_checkin_longitude")
  private Double handoffCheckInLongitude;

  @Column(name = "handoff_checkin_user_id")
  private Long handoffCheckInUserId;

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

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Listing getListing() {
    return listing;
  }

  public void setListing(Listing listing) {
    this.listing = listing;
  }

  public User getBuyer() {
    return buyer;
  }

  public void setBuyer(User buyer) {
    this.buyer = buyer;
  }

  public User getSeller() {
    return seller;
  }

  public void setSeller(User seller) {
    this.seller = seller;
  }

  public OrderType getType() {
    return type;
  }

  public void setType(OrderType type) {
    this.type = type;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public BigDecimal getOfferedPrice() {
    return offeredPrice;
  }

  public void setOfferedPrice(BigDecimal offeredPrice) {
    this.offeredPrice = offeredPrice;
  }

  public BigDecimal getFinalPrice() {
    return finalPrice;
  }

  public void setFinalPrice(BigDecimal finalPrice) {
    this.finalPrice = finalPrice;
  }

  public UUID getBundleId() {
    return bundleId;
  }

  public void setBundleId(UUID bundleId) {
    this.bundleId = bundleId;
  }

  public Integer getBundlePosition() {
    return bundlePosition;
  }

  public void setBundlePosition(Integer bundlePosition) {
    this.bundlePosition = bundlePosition;
  }

  public Integer getBundleSize() {
    return bundleSize;
  }

  public void setBundleSize(Integer bundleSize) {
    this.bundleSize = bundleSize;
  }

  public BigDecimal getCollateralAmount() {
    return collateralAmount;
  }

  public void setCollateralAmount(BigDecimal collateralAmount) {
    this.collateralAmount = collateralAmount;
  }

  public CollateralStatus getCollateralStatus() {
    return collateralStatus == null ? CollateralStatus.NOT_APPLICABLE : collateralStatus;
  }

  public void setCollateralStatus(CollateralStatus collateralStatus) {
    this.collateralStatus = collateralStatus;
  }

  public Instant getCollateralLockedAt() {
    return collateralLockedAt;
  }

  public void setCollateralLockedAt(Instant collateralLockedAt) {
    this.collateralLockedAt = collateralLockedAt;
  }

  public Instant getCollateralSettledAt() {
    return collateralSettledAt;
  }

  public void setCollateralSettledAt(Instant collateralSettledAt) {
    this.collateralSettledAt = collateralSettledAt;
  }

  public Long getOfferedListingId() {
    return offeredListingId;
  }

  public void setOfferedListingId(Long offeredListingId) {
    this.offeredListingId = offeredListingId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getHandoffDeadlineAt() {
    return handoffDeadlineAt;
  }

  public void setHandoffDeadlineAt(Instant handoffDeadlineAt) {
    this.handoffDeadlineAt = handoffDeadlineAt;
  }

  public Instant getBuyerHandoffConfirmedAt() {
    return buyerHandoffConfirmedAt;
  }

  public void setBuyerHandoffConfirmedAt(Instant buyerHandoffConfirmedAt) {
    this.buyerHandoffConfirmedAt = buyerHandoffConfirmedAt;
  }

  public Instant getSellerHandoffConfirmedAt() {
    return sellerHandoffConfirmedAt;
  }

  public void setSellerHandoffConfirmedAt(Instant sellerHandoffConfirmedAt) {
    this.sellerHandoffConfirmedAt = sellerHandoffConfirmedAt;
  }

  public Double getHandoffCheckInLatitude() {
    return handoffCheckInLatitude;
  }

  public void setHandoffCheckInLatitude(Double handoffCheckInLatitude) {
    this.handoffCheckInLatitude = handoffCheckInLatitude;
  }

  public Double getHandoffCheckInLongitude() {
    return handoffCheckInLongitude;
  }

  public void setHandoffCheckInLongitude(Double handoffCheckInLongitude) {
    this.handoffCheckInLongitude = handoffCheckInLongitude;
  }

  public Long getHandoffCheckInUserId() {
    return handoffCheckInUserId;
  }

  public void setHandoffCheckInUserId(Long handoffCheckInUserId) {
    this.handoffCheckInUserId = handoffCheckInUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
