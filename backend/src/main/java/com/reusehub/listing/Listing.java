package com.reusehub.listing;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listings")
public class Listing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 2000)
  private String description;

  @Enumerated(EnumType.STRING)
  private ListingCategory category;

  @Enumerated(EnumType.STRING)
  @jakarta.persistence.Column(name = "item_condition")
  private ItemCondition condition;

  @Enumerated(EnumType.STRING)
  private ListingType listingType;

  private BigDecimal price;

  private String location = "North Campus";

  @Enumerated(EnumType.STRING)
  private ListingStatus status = ListingStatus.ACTIVE;

  @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ListingImage> images = new ArrayList<>();

  private Instant createdAt;

  private Instant updatedAt;

  private Instant relistingSuggestionSentAt;

  private Instant relistingSuggestionDismissedAt;

  @jakarta.persistence.Version
  private Long version;

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

  public void addImage(String url) {
    ListingImage image = new ListingImage();
    image.setListing(this);
    image.setImageUrl(url);
    images.add(image);
  }

  public void clearImages() {
    images.clear();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ListingCategory getCategory() {
    return category;
  }

  public void setCategory(ListingCategory category) {
    this.category = category;
  }

  public ItemCondition getCondition() {
    return condition;
  }

  public void setCondition(ItemCondition condition) {
    this.condition = condition;
  }

  public ListingType getListingType() {
    return listingType;
  }

  public void setListingType(ListingType listingType) {
    this.listingType = listingType;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public ListingStatus getStatus() {
    return status;
  }

  public void setStatus(ListingStatus status) {
    this.status = status;
  }

  public List<ListingImage> getImages() {
    return images;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getRelistingSuggestionSentAt() {
    return relistingSuggestionSentAt;
  }

  public void setRelistingSuggestionSentAt(Instant relistingSuggestionSentAt) {
    this.relistingSuggestionSentAt = relistingSuggestionSentAt;
  }

  public Instant getRelistingSuggestionDismissedAt() {
    return relistingSuggestionDismissedAt;
  }

  public void setRelistingSuggestionDismissedAt(Instant relistingSuggestionDismissedAt) {
    this.relistingSuggestionDismissedAt = relistingSuggestionDismissedAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
