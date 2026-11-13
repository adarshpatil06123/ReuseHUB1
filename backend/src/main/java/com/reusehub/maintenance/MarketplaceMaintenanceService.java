package com.reusehub.maintenance;

import com.reusehub.chat.ChatMessageRepository;
import com.reusehub.chat.ChatThreadRepository;
import com.reusehub.listing.ListingImageRepository;
import com.reusehub.listing.ListingRepository;
import com.reusehub.order.OrderRepository;
import com.reusehub.wishlist.WishlistItemRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceMaintenanceService {
  private final ChatMessageRepository chatMessageRepository;
  private final ChatThreadRepository chatThreadRepository;
  private final WishlistItemRepository wishlistItemRepository;
  private final OrderRepository orderRepository;
  private final ListingImageRepository listingImageRepository;
  private final ListingRepository listingRepository;

  public MarketplaceMaintenanceService(
      ChatMessageRepository chatMessageRepository,
      ChatThreadRepository chatThreadRepository,
      WishlistItemRepository wishlistItemRepository,
      OrderRepository orderRepository,
      ListingImageRepository listingImageRepository,
      ListingRepository listingRepository
  ) {
    this.chatMessageRepository = chatMessageRepository;
    this.chatThreadRepository = chatThreadRepository;
    this.wishlistItemRepository = wishlistItemRepository;
    this.orderRepository = orderRepository;
    this.listingImageRepository = listingImageRepository;
    this.listingRepository = listingRepository;
  }

  @Transactional
  public Map<String, Long> purgeMarketplaceListings() {
    long chatMessages = chatMessageRepository.count();
    long chatThreads = chatThreadRepository.count();
    long wishlistItems = wishlistItemRepository.count();
    long orders = orderRepository.count();
    long listingImages = listingImageRepository.count();
    long listings = listingRepository.count();

    chatMessageRepository.deleteAllInBatch();
    chatThreadRepository.deleteAllInBatch();
    wishlistItemRepository.deleteAllInBatch();
    orderRepository.deleteAllInBatch();
    listingImageRepository.deleteAllInBatch();
    listingRepository.deleteAllInBatch();

    return Map.of(
        "chatMessages", chatMessages,
        "chatThreads", chatThreads,
        "wishlistItems", wishlistItems,
        "orders", orders,
        "listingImages", listingImages,
        "listings", listings
    );
  }
}