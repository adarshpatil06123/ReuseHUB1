package com.reusehub.need;

import com.reusehub.common.ApiException;
import com.reusehub.listing.Listing;
import com.reusehub.listing.ListingRepository;
import com.reusehub.listing.ListingStatus;
import com.reusehub.listing.ListingType;
import com.reusehub.listing.dto.ListingResponse;
import com.reusehub.need.dto.NeedMatchNotificationDto;
import com.reusehub.need.dto.NeedMatchResponse;
import com.reusehub.need.dto.NeedRequestCreateRequest;
import com.reusehub.need.dto.NeedRequestResponse;
import com.reusehub.user.User;
import com.reusehub.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NeedRequestService {
  private final NeedRequestRepository needRequestRepository;
  private final NeedMatchRepository needMatchRepository;
  private final ListingRepository listingRepository;
  private final UserRepository userRepository;
  private final SimpMessagingTemplate messagingTemplate;

  public NeedRequestService(
      NeedRequestRepository needRequestRepository,
      NeedMatchRepository needMatchRepository,
      ListingRepository listingRepository,
      UserRepository userRepository,
      SimpMessagingTemplate messagingTemplate
  ) {
    this.needRequestRepository = needRequestRepository;
    this.needMatchRepository = needMatchRepository;
    this.listingRepository = listingRepository;
    this.userRepository = userRepository;
    this.messagingTemplate = messagingTemplate;
  }

  @Transactional
  public List<NeedRequestResponse> listForUser(Long userId) {
    return needRequestRepository.findByUser_IdOrderByCreatedAtDesc(userId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public NeedRequestResponse create(Long userId, NeedRequestCreateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    NeedRequest needRequest = new NeedRequest();
    needRequest.setUser(user);
    needRequest.setKeywords(request.keywords().trim());
    needRequest.setCategory(request.category());
    needRequest.setListingType(request.listingType());
    needRequest.setMaxPrice(request.maxPrice());
    needRequest.setDetails(trimToNull(request.details()));
    needRequest.setMinimumCondition(request.minimumCondition());
    needRequest.setPreferredLocation(defaultIfBlank(request.preferredLocation(), "North Campus"));
    needRequest.setNeededBy(defaultIfBlank(request.neededBy(), "ANYTIME"));
    needRequest.setShowOnHeatmap(Boolean.TRUE.equals(request.showOnHeatmap()));
    needRequest.setAcceptDonations(request.acceptDonations() == null || request.acceptDonations());
    needRequest.setReferenceImageUrl(trimToNull(request.referenceImageUrl()));
    needRequest.setActive(request.active() == null || request.active());
    NeedRequest saved = needRequestRepository.save(needRequest);

    if (saved.isActive()) {
      listingRepository.findByStatus(ListingStatus.ACTIVE).stream()
          .filter(listing -> matches(saved, listing))
          .forEach(listing -> createMatch(saved, listing));
    }
    return toResponse(saved);
  }

  @Transactional
  public NeedRequestResponse publish(Long userId, UUID needRequestId) {
    NeedRequest needRequest = needRequestRepository.findByIdAndUser_Id(needRequestId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Need request not found"));
    if (!needRequest.isActive()) {
      needRequest.setActive(true);
      needRequestRepository.save(needRequest);
      listingRepository.findByStatus(ListingStatus.ACTIVE).stream()
          .filter(listing -> matches(needRequest, listing))
          .forEach(listing -> createMatch(needRequest, listing));
    }
    return toResponse(needRequest);
  }

  @Transactional
  public void delete(Long userId, UUID needRequestId) {
    NeedRequest needRequest = needRequestRepository.findByIdAndUser_Id(needRequestId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Need request not found"));
    needRequestRepository.delete(needRequest);
  }

  @Transactional
  public void matchListing(Listing listing) {
    if (listing.getStatus() != ListingStatus.ACTIVE) {
      return;
    }
    needRequestRepository.findByActiveTrue().stream()
        .filter(needRequest -> matches(needRequest, listing))
        .filter(needRequest -> !needMatchRepository.existsByNeedRequest_IdAndListing_Id(
            needRequest.getId(), listing.getId()))
        .forEach(needRequest -> createMatch(needRequest, listing));
  }

  private void createMatch(NeedRequest needRequest, Listing listing) {
    if (needMatchRepository.existsByNeedRequest_IdAndListing_Id(needRequest.getId(), listing.getId())) {
      return;
    }
    NeedMatch match = new NeedMatch();
    match.setNeedRequest(needRequest);
    match.setListing(listing);
    NeedMatch savedMatch = needMatchRepository.save(match);
    NeedMatchNotificationDto notification = new NeedMatchNotificationDto(
        "NEED_MATCH",
        savedMatch.getId().toString(),
        listing.getId(),
        listing.getTitle(),
        "ReuseHub",
        "A new listing matches your need request: " + needRequest.getKeywords(),
        savedMatch.getMatchedAt() == null ? Instant.now() : savedMatch.getMatchedAt()
    );
    messagingTemplate.convertAndSend(
        "/topic/users/" + needRequest.getUser().getId() + "/notifications",
        notification
    );
  }

  private boolean matches(NeedRequest needRequest, Listing listing) {
    if (!needRequest.isActive() || listing.getStatus() != ListingStatus.ACTIVE) {
      return false;
    }
    if (needRequest.getCategory() != null && needRequest.getCategory() != listing.getCategory()) {
      return false;
    }
    if (needRequest.getListingType() != null && needRequest.getListingType() != listing.getListingType()) {
      return false;
    }
    if (needRequest.getListingType() == null
        && listing.getListingType() == ListingType.DONATE
        && !needRequest.isAcceptDonations()) {
      return false;
    }
    BigDecimal price = listing.getPrice();
    if (needRequest.getMaxPrice() != null) {
      boolean acceptedFreeDonation = listing.getListingType() == ListingType.DONATE
          && (needRequest.getListingType() == ListingType.DONATE
              || needRequest.isAcceptDonations());
      if ((price == null && !acceptedFreeDonation)
          || (price != null && price.compareTo(needRequest.getMaxPrice()) > 0)) {
        return false;
      }
    }
    if (needRequest.getMinimumCondition() != null
        && (listing.getCondition() == null
            || listing.getCondition().compareTo(needRequest.getMinimumCondition()) > 0)) {
      return false;
    }

    String searchableText = (listing.getTitle() + " " + listing.getDescription() + " "
        + (listing.getCategory() == null ? "" : listing.getCategory().name()))
        .toLowerCase(Locale.ROOT);
    String[] terms = needRequest.getKeywords().toLowerCase(Locale.ROOT).trim().split("\\s+");
    return Arrays.stream(terms).allMatch(searchableText::contains);
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String defaultIfBlank(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private NeedRequestResponse toResponse(NeedRequest needRequest) {
    List<NeedMatchResponse> matches = needMatchRepository
        .findByNeedRequest_IdOrderByMatchedAtDesc(needRequest.getId())
        .stream()
        .map(match -> new NeedMatchResponse(
            match.getId(),
            ListingResponse.from(match.getListing()),
            match.getMatchedAt()
        ))
        .toList();
    return NeedRequestResponse.from(needRequest, matches);
  }
}
