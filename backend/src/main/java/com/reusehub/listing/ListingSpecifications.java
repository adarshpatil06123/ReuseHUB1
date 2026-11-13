package com.reusehub.listing;

import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

public final class ListingSpecifications {
  private ListingSpecifications() {}

  public static Specification<Listing> hasCategory(ListingCategory category) {
    return (root, query, cb) ->
        category == null ? null : cb.equal(root.get("category"), category);
  }

  public static Specification<Listing> hasCondition(ItemCondition condition) {
    return (root, query, cb) ->
        condition == null ? null : cb.equal(root.get("condition"), condition);
  }

  public static Specification<Listing> hasType(ListingType type) {
    return (root, query, cb) ->
        type == null ? null : cb.equal(root.get("listingType"), type);
  }

  public static Specification<Listing> priceBetween(BigDecimal min, BigDecimal max) {
    return (root, query, cb) -> {
      if (min == null && max == null) {
        return null;
      }
      if (min != null && max != null) {
        return cb.between(root.get("price"), min, max);
      }
      if (min != null) {
        return cb.greaterThanOrEqualTo(root.get("price"), min);
      }
      return cb.lessThanOrEqualTo(root.get("price"), max);
    };
  }

  public static Specification<Listing> keywordLike(String keyword) {
    return (root, query, cb) -> {
      if (keyword == null || keyword.isBlank()) {
        return null;
      }
      String like = "%" + keyword.toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("title")), like),
          cb.like(cb.lower(root.get("description")), like)
      );
    };
  }

  public static Specification<Listing> isActive() {
    return (root, query, cb) -> cb.equal(root.get("status"), ListingStatus.ACTIVE);
  }
}
