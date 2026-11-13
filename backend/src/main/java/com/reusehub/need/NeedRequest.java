package com.reusehub.need;

import com.reusehub.listing.ListingCategory;
import com.reusehub.listing.ItemCondition;
import com.reusehub.listing.ListingType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "need_requests")
public class NeedRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 120)
  private String keywords;

  @Enumerated(EnumType.STRING)
  private ListingCategory category;

  @Enumerated(EnumType.STRING)
  private ListingType listingType;

  @Column(length = 2000)
  private String details;

  @Enumerated(EnumType.STRING)
  private ItemCondition minimumCondition;

  private BigDecimal maxPrice;

  @Column(nullable = false, length = 120)
  private String preferredLocation = "North Campus";

  @Column(length = 24)
  private String neededBy = "ANYTIME";

  @Column(nullable = false)
  private boolean showOnHeatmap;

  @Column(nullable = false)
  private boolean acceptDonations = true;

  @Column(length = 500)
  private String referenceImageUrl;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "needRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<NeedMatch> matches = new ArrayList<>();

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public ListingCategory getCategory() {
    return category;
  }

  public void setCategory(ListingCategory category) {
    this.category = category;
  }

  public ListingType getListingType() {
    return listingType;
  }

  public void setListingType(ListingType listingType) {
    this.listingType = listingType;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public ItemCondition getMinimumCondition() {
    return minimumCondition;
  }

  public void setMinimumCondition(ItemCondition minimumCondition) {
    this.minimumCondition = minimumCondition;
  }

  public BigDecimal getMaxPrice() {
    return maxPrice;
  }

  public void setMaxPrice(BigDecimal maxPrice) {
    this.maxPrice = maxPrice;
  }

  public String getPreferredLocation() {
    return preferredLocation;
  }

  public void setPreferredLocation(String preferredLocation) {
    this.preferredLocation = preferredLocation;
  }

  public String getNeededBy() {
    return neededBy;
  }

  public void setNeededBy(String neededBy) {
    this.neededBy = neededBy;
  }

  public boolean isShowOnHeatmap() {
    return showOnHeatmap;
  }

  public void setShowOnHeatmap(boolean showOnHeatmap) {
    this.showOnHeatmap = showOnHeatmap;
  }

  public boolean isAcceptDonations() {
    return acceptDonations;
  }

  public void setAcceptDonations(boolean acceptDonations) {
    this.acceptDonations = acceptDonations;
  }

  public String getReferenceImageUrl() {
    return referenceImageUrl;
  }

  public void setReferenceImageUrl(String referenceImageUrl) {
    this.referenceImageUrl = referenceImageUrl;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<NeedMatch> getMatches() {
    return matches;
  }
}
