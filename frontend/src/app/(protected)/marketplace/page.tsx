"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { apiRequest, appRequest } from "@/lib/api";
import { CATEGORY_OPTIONS } from "@/lib/categories";
import { useToast } from "@/context/ToastContext";
import { useSearch } from "@/context/SearchContext";

type Listing = {
  id: number;
  userId?: number;
  title: string;
  description: string;
  category: string;
  condition: string;
  listingType: string;
  price: string | null;
  status: string;
  imageUrls: string[];
  location?: string;
  rentalPricingPeriod?: string | null;
  securityDeposit?: number | string | null;
  rentalAvailableFrom?: string | null;
  rentalAvailableUntil?: string | null;
};

type WishlistItemResponse = {
  id: number;
  listing: {
    id: number;
  };
  savedAt: string;
};


const conditionOptions = ["New", "Good", "Fair"] as const;

export default function MarketplacePage() {
  const { showToast } = useToast();
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialCategory = searchParams.get("category");
  const { searchQuery, setSearchQuery } = useSearch();
  const [listings, setListings] = useState<Listing[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selectedCategories, setSelectedCategories] = useState<string[]>(
    initialCategory ? [initialCategory.toUpperCase()] : []
  );
  const [selectedConditions, setSelectedConditions] = useState<string[]>([]);
  const [priceMax, setPriceMax] = useState(0);
  const [location, setLocation] = useState("All Locations");
  const [listingMode, setListingMode] = useState(searchParams.get("type") === "RENT" ? "RENT" : "ALL");
  const [availableFrom, setAvailableFrom] = useState("");
  const [availableUntil, setAvailableUntil] = useState("");
  const [sortBy, setSortBy] = useState("NEWEST");
  const [wishlistIds, setWishlistIds] = useState<Set<number>>(new Set());
  const [wishlistBusyIds, setWishlistBusyIds] = useState<Set<number>>(new Set());
  const [visibleCount, setVisibleCount] = useState<number>(8);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [selectedBundleIds, setSelectedBundleIds] = useState<number[]>([]);
  const [bundleBusy, setBundleBusy] = useState(false);

  const selectedBundleSellerId = listings.find(
    (listing) => selectedBundleIds.includes(listing.id)
  )?.userId;

  const toggleBundleItem = (listing: Listing) => {
    if (selectedBundleIds.includes(listing.id)) {
      setSelectedBundleIds((ids) => ids.filter((id) => id !== listing.id));
      return;
    }
    if (selectedBundleIds.length >= 5) {
      showToast("A bundle can contain at most 5 items.", "error");
      return;
    }
    if (selectedBundleSellerId !== undefined && listing.userId !== selectedBundleSellerId) {
      showToast("Choose items from the same seller for one bundle.", "error");
      return;
    }
    setSelectedBundleIds((ids) => [...ids, listing.id]);
  };

  const requestBundle = async () => {
    if (selectedBundleIds.length < 2) {
      showToast("Select at least 2 listings to request a bundle.", "error");
      return;
    }
    setBundleBusy(true);
    try {
      await apiRequest("/orders/bundle", {
        method: "POST",
        body: JSON.stringify({ listingIds: selectedBundleIds })
      });
      setSelectedBundleIds([]);
      showToast("Bundle request sent to the seller.", "success");
      router.push("/dashboard/orders");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to request this bundle.", "error");
    } finally {
      setBundleBusy(false);
    }
  };

  useEffect(() => {
    const cat = searchParams.get("category");
    if (cat) {
      setSelectedCategories([cat.toUpperCase()]);
    }
  }, [searchParams]);

  useEffect(() => {
    setListingMode(searchParams.get("type") === "RENT" ? "RENT" : "ALL");
  }, [searchParams]);

  useEffect(() => {
    let mounted = true;
    apiRequest<{ id: number }>("/users/me")
      .then((user) => {
        if (mounted) setCurrentUserId(user.id);
      })
      .catch(() => {
        if (mounted) setCurrentUserId(null);
      });
    apiRequest<Listing[]>("/listings")
      .then((data) => {
        if (!mounted) return;
        setListings(data);
        const maxPrice = data.reduce((acc, listing) => {
          const numericPrice = Number(String(listing.price || "0").replace(/[^0-9.]/g, ""));
          if (Number.isNaN(numericPrice)) return acc;
          return Math.max(acc, numericPrice);
        }, 0);
        setPriceMax(maxPrice);
        setError(null);
      })
      .catch((err) => {
        if (!mounted) return;
        const message = err instanceof Error ? err.message : "Failed to load listings";
        setError(message);
        showToast(message, "error");
        setListings([]);
        setPriceMax(0);
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    appRequest<WishlistItemResponse[]>("/api/wishlist")
      .then((data) => {
        if (!mounted) return;
        setWishlistIds(new Set(data.map((item) => item.listing.id)));
      })
      .catch((err) => {
        if (!mounted) return;
        const message = err instanceof Error ? err.message : "Unable to load wishlist";
        setError(message);
        showToast(message, "error");
        setWishlistIds(new Set());
      });
    return () => {
      mounted = false;
    };
  }, []);

  const [ratingsMap, setRatingsMap] = useState<Record<number, { avg: string; count: number }>>({});

  useEffect(() => {
    // Compute ratings for listings dynamically from stored reviews
    const map: Record<number, { avg: string; count: number }> = {};
    listings.forEach((item) => {
      try {
        const stored = localStorage.getItem(`reusehub_reviews_${item.id}`);
        if (stored) {
          const revs = JSON.parse(stored);
          if (Array.isArray(revs) && revs.length > 0) {
            const sum = revs.reduce((acc: number, r: { rating?: number }) => acc + (Number(r.rating) || 5), 0);
            map[item.id] = {
              avg: (sum / revs.length).toFixed(1),
              count: revs.length
            };
            return;
          }
        }
      } catch {
        // ignore
      }
      // Default baseline for items without custom reviews
      map[item.id] = { avg: "5.0", count: 1 };
    });
    setRatingsMap(map);
  }, [listings]);

  const filteredListings = useMemo(() => {
    const conditionMap: Record<string, string[]> = {
      New: ["NEW", "LIKE_NEW"],
      Good: ["GOOD"],
      Fair: ["FAIR"]
    };

    const filtered = listings.filter((listing) => {
      const matchesCategory =
        selectedCategories.length === 0 || selectedCategories.includes(listing.category);
      const matchesMode = listingMode === "ALL"
        || (listingMode === "RENT" ? listing.listingType === "RENT" : listing.listingType === "SELL");

      const matchesCondition =
        selectedConditions.length === 0 ||
        selectedConditions.some((condition) =>
          conditionMap[condition]?.includes(listing.condition)
        );

      const numericPrice = Number(String(listing.price || "0").replace(/[^0-9.]/g, ""));
      const priceValue = Number.isNaN(numericPrice) ? 0 : numericPrice;
      const matchesPrice = priceValue <= priceMax;
      const matchesLocation = location === "All Locations" || listing.location === location;
      const requestedStart = availableFrom || availableUntil;
      const requestedEnd = availableUntil || availableFrom;
      const matchesAvailability = (!requestedStart && !requestedEnd) || (
        listing.listingType === "RENT"
        && (!listing.rentalAvailableFrom || listing.rentalAvailableFrom <= requestedStart)
        && (!listing.rentalAvailableUntil || listing.rentalAvailableUntil >= requestedEnd)
      );

      const trimmedQuery = searchQuery.trim().toLowerCase();
      const matchesSearch =
        !trimmedQuery ||
        listing.title.toLowerCase().includes(trimmedQuery) ||
        listing.description.toLowerCase().includes(trimmedQuery) ||
        listing.category.toLowerCase().includes(trimmedQuery);

      return matchesMode && matchesCategory && matchesCondition && matchesPrice
        && matchesLocation && matchesAvailability && matchesSearch;
    });

    const toPrice = (listing: Listing) => {
      const numericPrice = Number(String(listing.price || "0").replace(/[^0-9.]/g, ""));
      return Number.isNaN(numericPrice) ? 0 : numericPrice;
    };

    const sorted = [...filtered];
    if (sortBy === "LOW_TO_HIGH") {
      sorted.sort((a, b) => toPrice(a) - toPrice(b));
    } else if (sortBy === "HIGH_TO_LOW") {
      sorted.sort((a, b) => toPrice(b) - toPrice(a));
    } else if (sortBy === "NEWEST") {
      sorted.sort((a, b) => b.id - a.id);
    }

    return sorted;
  }, [listings, listingMode, selectedCategories, selectedConditions, priceMax, location,
    availableFrom, availableUntil, sortBy, searchQuery]);

  const statsLabel = useMemo(() => {
    return `${filteredListings.length.toLocaleString()} pre-loved items looking for a new home.`;
  }, [filteredListings.length]);

  const toggleCategory = (value: string) => {
    setSelectedCategories((prev) =>
      prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
    );
  };

  const toggleCondition = (value: string) => {
    setSelectedConditions((prev) =>
      prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
    );
  };

  const formatPrice = (price: string | number | null, listingType: string) => {
    if (listingType === "SWAP") return "Trade";
    if (listingType === "DONATE") return "Free";
    const numericPrice = Number(String(price || "0").replace(/[^0-9.]/g, ""));
    if (Number.isNaN(numericPrice)) return "₹0";
    return `₹${numericPrice.toLocaleString()}${listingType === "RENT" ? " / day" : ""}`;
  };

  const toggleWishlist = async (listingId: number) => {
    if (wishlistBusyIds.has(listingId)) return;
    setWishlistBusyIds((prev) => new Set(prev).add(listingId));

    const isSaved = wishlistIds.has(listingId);

    // Instant optimistic update for immediate snappy feedback
    setWishlistIds((prev) => {
      const next = new Set(prev);
      if (isSaved) {
        next.delete(listingId);
      } else {
        next.add(listingId);
      }
      return next;
    });

    try {
      if (isSaved) {
        await appRequest<void>(`/api/wishlist/${listingId}`, { method: "DELETE" });
        showToast("Removed from wishlist.", "success");
      } else {
        await appRequest<WishlistItemResponse>(`/api/wishlist/${listingId}`, { method: "POST" });
        showToast("Added to wishlist.", "success");
      }
      setError(null);
    } catch (err) {
      // Revert optimistic change on network failure
      setWishlistIds((prev) => {
        const next = new Set(prev);
        if (isSaved) {
          next.add(listingId);
        } else {
          next.delete(listingId);
        }
        return next;
      });
      const message = err instanceof Error ? err.message : "Wishlist update failed";
      setError(message);
      showToast(message, "error");
    } finally {
      setWishlistBusyIds((prev) => {
        const next = new Set(prev);
        next.delete(listingId);
        return next;
      });
    }
  };

  const clearWishlist = async () => {
    try {
      await appRequest<void>("/api/wishlist", { method: "DELETE" });
      setWishlistIds(new Set());
      setError(null);
      showToast("Wishlist cleared.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Wishlist update failed";
      setError(message);
      showToast(message, "error");
    }
  };

  return (
    <div className="home">
      <main className="page marketplace">
        <aside className="filters">
          <h3>Listing type</h3>
          <div className="pill-row" role="group" aria-label="Filter by listing type">
            {[
              { value: "ALL", label: "All" },
              { value: "SELL", label: "Buy/Sell" },
              { value: "RENT", label: "Rent" }
            ].map((mode) => (
              <button
                key={mode.value}
                type="button"
                className={`pill-btn ${listingMode === mode.value ? "active" : ""}`}
                onClick={() => {
                  setListingMode(mode.value);
                  if (mode.value === "RENT") router.replace("/marketplace?type=RENT");
                  else if (searchParams.has("type")) router.replace("/marketplace");
                }}
              >
                {mode.label}
              </button>
            ))}
          </div>

          <h3>Category</h3>
          <label>
            <input
              type="checkbox"
              checked={selectedCategories.length === 0}
              onChange={() => setSelectedCategories([])}
            />
            All
          </label>
          {CATEGORY_OPTIONS.map((option) => (
            <label key={option.value}>
              <input
                type="checkbox"
                checked={selectedCategories.includes(option.value)}
                onChange={() => toggleCategory(option.value)}
              />
              {option.label}
            </label>
          ))}

          <h3>Price range</h3>
          <div className="range">
            <span>₹0</span>
            <input
              type="range"
              min="0"
              max="500"
              value={priceMax}
              onChange={(event) => setPriceMax(Number(event.target.value))}
            />
            <span>₹500+</span>
          </div>

          <h3>Condition</h3>
          <div className="pill-row">
            {conditionOptions.map((condition) => (
              <button
                key={condition}
                type="button"
                className={`pill-btn ${selectedConditions.includes(condition) ? "active" : ""}`}
                onClick={() => toggleCondition(condition)}
              >
                {condition}
              </button>
            ))}
          </div>

          <h3>Location</h3>
          <select value={location} onChange={(event) => setLocation(event.target.value)}>
            <option>All Locations</option>
            {[...new Set(listings.map((listing) => listing.location).filter((value): value is string => Boolean(value)))]
              .sort()
              .map((value) => <option key={value}>{value}</option>)}
          </select>

          <h3>Rental dates</h3>
          <label>
            Start
            <input type="date" value={availableFrom} min={new Date().toLocaleDateString("en-CA")}
              onChange={(event) => setAvailableFrom(event.target.value)} />
          </label>
          <label>
            End
            <input type="date" value={availableUntil} min={availableFrom || new Date().toLocaleDateString("en-CA")}
              onChange={(event) => setAvailableUntil(event.target.value)} />
          </label>

          <div className="impact-card">
            <h4>Impact Stats</h4>
            <p>
              Your campus has diverted{" "}
              <strong>{(listings.length * 0.15 + 1.2).toFixed(1)} tons</strong> of waste across{" "}
              <strong>{listings.length}</strong> active student listings.
            </p>
          </div>
        </aside>

        <section className="market-content">
          <div className="market-head">
            <div>
              <h1>Marketplace</h1>
              <p>{statsLabel}</p>
            </div>
            <div className="sort">
              <span>Sort by:</span>
              <select value={sortBy} onChange={(event) => setSortBy(event.target.value)}>
                <option value="NEWEST">Newest First</option>
                <option value="LOW_TO_HIGH">{listingMode === "RENT" ? "Rate: Low to High" : "Price: Low to High"}</option>
                <option value="HIGH_TO_LOW">{listingMode === "RENT" ? "Rate: High to Low" : "Price: High to Low"}</option>
              </select>
              <button
                className="btn ghost"
                type="button"
                onClick={clearWishlist}
                disabled={wishlistBusyIds.size > 0}
              >
                Clear wishlist
              </button>
            </div>
          </div>
          {selectedBundleIds.length > 0 ? (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "12px", flexWrap: "wrap", margin: "12px 0", padding: "12px 16px", border: "1px solid #bfdbfe", borderRadius: "10px", background: "#eff6ff" }}>
              <span>{selectedBundleIds.length} bundle item{selectedBundleIds.length === 1 ? "" : "s"} selected. Select 2–5 items from one seller.</span>
              <div style={{ display: "flex", gap: "8px" }}>
                <button className="btn ghost" type="button" onClick={() => setSelectedBundleIds([])}>
                  Clear
                </button>
                <button className="btn primary" type="button" onClick={requestBundle} disabled={bundleBusy || selectedBundleIds.length < 2}>
                  {bundleBusy ? "Sending..." : "Request bundle"}
                </button>
              </div>
            </div>
          ) : null}

          {error ? <div className="error">{error}</div> : null}

          {searchQuery.trim() && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", margin: "12px 0", padding: "10px 16px", background: "rgba(16, 185, 129, 0.08)", borderRadius: "8px", border: "1px solid rgba(16, 185, 129, 0.2)" }}>
              <span>Showing search results for: <strong>"{searchQuery}"</strong></span>
              <button
                type="button"
                className="btn ghost"
                style={{ padding: "4px 10px", fontSize: "0.85rem" }}
                onClick={() => setSearchQuery("")}
              >
                Clear Search ✕
              </button>
            </div>
          )}

          {filteredListings.length === 0 ? (
            <div style={{ textAlign: "center", padding: "48px 16px" }}>
              <p style={{ fontSize: "1.2rem", fontWeight: 600, color: "var(--foreground, #333)" }}>
                {listings.length === 0
                  ? "No listings yet."
                  : `No listings found matching "${searchQuery}"`}
              </p>
              <p style={{ color: "#666", marginTop: "8px" }}>
                {listings.length === 0
                  ? "Be the first to add one to the marketplace."
                  : "Try adjusting your search terms or clearing active filters."}
              </p>
              <button
                type="button"
                className="btn primary"
                style={{ marginTop: "16px" }}
                onClick={() => setSearchQuery("")}
              >
                Reset Search
              </button>
            </div>
          ) : (
            <div className="market-grid">
            {filteredListings.slice(0, visibleCount).map((listing) => {
              const coverImage = listing.imageUrls?.[0];
              return (
                <article className="market-card" key={listing.id}>
                  <div
                    className={`market-media ${coverImage ? "has-image" : ""}`}
                    style={coverImage ? { backgroundImage: `url(${coverImage})` } : undefined}
                  >
                    <span className="tag">
                      {listing.listingType === "DONATE" ? "FREE" : listing.listingType}
                    </span>
                    <button
                      type="button"
                      className={`wishlist-heart ${wishlistIds.has(listing.id) ? "active" : ""}`}
                      onClick={() => toggleWishlist(listing.id)}
                      disabled={wishlistBusyIds.has(listing.id)}
                      aria-pressed={wishlistIds.has(listing.id)}
                      aria-label={
                        wishlistIds.has(listing.id)
                          ? "Remove from wishlist"
                          : "Add to wishlist"
                      }
                    >
                      ♥
                    </button>
                  </div>
                  <div className="market-body">
                    <div>
                      <h3>{listing.title}</h3>
                      <p>{listing.category}</p>
                    </div>
                    <span className="price">
                      {formatPrice(listing.price, listing.listingType)}
                    </span>
                  </div>
                  <div className="market-footer">
                    <span className="rating">
                      ★ {ratingsMap[listing.id]?.avg ?? "5.0"} ({ratingsMap[listing.id]?.count ?? 1})
                    </span>
                    <div className="actions">
                      {listing.listingType === "SELL" &&
                        listing.status === "ACTIVE" &&
                        currentUserId !== null &&
                        Number.isInteger(listing.userId) &&
                        listing.userId !== currentUserId ? (
                          <button
                            className="btn ghost"
                            type="button"
                            onClick={() => toggleBundleItem(listing)}
                          >
                            {selectedBundleIds.includes(listing.id) ? "Remove bundle item" : "Add to bundle"}
                          </button>
                        ) : null}
                      <Link className="btn ghost" href={`/marketplace/${listing.id}`}>
                        Details
                      </Link>
                      <Link className="btn primary" href={`/marketplace/${listing.id}/chat`}>
                        Chat
                      </Link>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
          )}

          {filteredListings.length > visibleCount && (
            <div className="pagination" style={{ display: "flex", justifyContent: "center", margin: "32px 0" }}>
              <button
                className="btn primary"
                type="button"
                onClick={() => setVisibleCount((prev) => prev + 8)}
              >
                Show More Items ({filteredListings.length - visibleCount} remaining)
              </button>
            </div>
          )}
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Promoting circularity and campus sustainability through sharing.</p>
        </div>
        <div>
          <h4>Platform</h4>
          <Link href="/marketplace">Marketplace</Link>
          <Link href="/wishlist">Wishlist</Link>
          <Link href="/dashboard">Dashboard</Link>
        </div>
        <div>
          <h4>Community</h4>
          <Link href="/profile">My Profile</Link>
          <Link href="/dashboard/orders">My Orders</Link>
          <Link href="/dashboard/listings">My Listings</Link>
        </div>
        <div>
          <h4>Sustainability</h4>
          <p style={{ fontSize: "0.85rem", color: "#666" }}>
            Campus circular exchange network.
          </p>
        </div>
      </footer>
    </div>
  );
}
