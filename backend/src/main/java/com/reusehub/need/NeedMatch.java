package com.reusehub.need;

import com.reusehub.listing.Listing;
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
@Table(name = "need_matches", uniqueConstraints = {
    @UniqueConstraint(name = "uk_need_match_request_listing", columnNames = {"need_request_id", "listing_id"})
})
public class NeedMatch {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "need_request_id", nullable = false)
  private NeedRequest needRequest;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private Listing listing;

  private Instant matchedAt;

  @PrePersist
  public void prePersist() {
    if (matchedAt == null) {
      matchedAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public NeedRequest getNeedRequest() {
    return needRequest;
  }

  public void setNeedRequest(NeedRequest needRequest) {
    this.needRequest = needRequest;
  }

  public Listing getListing() {
    return listing;
  }

  public void setListing(Listing listing) {
    this.listing = listing;
  }

  public Instant getMatchedAt() {
    return matchedAt;
  }
}
