package com.reusehub.rental;

import com.reusehub.listing.Listing;
import com.reusehub.listing.RentalPricingPeriod;
import com.reusehub.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rental_requests", indexes = {
    @Index(name = "idx_rental_listing_status_dates", columnList = "listing_id,status,start_date,end_date"),
    @Index(name = "idx_rental_renter_created", columnList = "renter_id,created_at"),
    @Index(name = "idx_rental_owner_created", columnList = "owner_id,created_at")
})
public class RentalRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private Listing listing;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "renter_id", nullable = false)
  private User renter;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal dailyRate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RentalPricingPeriod pricingPeriod;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal rentalAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal securityDeposit;

  @Column(length = 1000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RentalStatus status = RentalStatus.PENDING;

  @Column(length = 1000)
  private String statusReason;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant ownerHandoverConfirmedAt;

  private Instant renterHandoverConfirmedAt;

  private Instant returnRequestedAt;

  private Instant returnedAt;

  private Instant completedAt;

  private Instant createdAt;

  private Instant updatedAt;

  @OneToMany(mappedBy = "rental", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RentalStatusHistory> statusHistory = new ArrayList<>();

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

  public void addStatusHistory(RentalStatus previousStatus, RentalStatus newStatus, Long actorId, String reason) {
    statusHistory.add(new RentalStatusHistory(this, previousStatus, newStatus, actorId, reason));
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public Listing getListing() { return listing; }
  public void setListing(Listing listing) { this.listing = listing; }
  public User getRenter() { return renter; }
  public void setRenter(User renter) { this.renter = renter; }
  public User getOwner() { return owner; }
  public void setOwner(User owner) { this.owner = owner; }
  public LocalDate getStartDate() { return startDate; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
  public BigDecimal getDailyRate() { return dailyRate; }
  public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
  public RentalPricingPeriod getPricingPeriod() { return pricingPeriod; }
  public void setPricingPeriod(RentalPricingPeriod pricingPeriod) { this.pricingPeriod = pricingPeriod; }
  public BigDecimal getRentalAmount() { return rentalAmount; }
  public void setRentalAmount(BigDecimal rentalAmount) { this.rentalAmount = rentalAmount; }
  public BigDecimal getSecurityDeposit() { return securityDeposit; }
  public void setSecurityDeposit(BigDecimal securityDeposit) { this.securityDeposit = securityDeposit; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public RentalStatus getStatus() { return status; }
  public void setStatus(RentalStatus status) { this.status = status; }
  public String getStatusReason() { return statusReason; }
  public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getOwnerHandoverConfirmedAt() { return ownerHandoverConfirmedAt; }
  public void setOwnerHandoverConfirmedAt(Instant ownerHandoverConfirmedAt) { this.ownerHandoverConfirmedAt = ownerHandoverConfirmedAt; }
  public Instant getRenterHandoverConfirmedAt() { return renterHandoverConfirmedAt; }
  public void setRenterHandoverConfirmedAt(Instant renterHandoverConfirmedAt) { this.renterHandoverConfirmedAt = renterHandoverConfirmedAt; }
  public Instant getReturnRequestedAt() { return returnRequestedAt; }
  public void setReturnRequestedAt(Instant returnRequestedAt) { this.returnRequestedAt = returnRequestedAt; }
  public Instant getReturnedAt() { return returnedAt; }
  public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public List<RentalStatusHistory> getStatusHistory() { return statusHistory; }
}
