"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { apiRequest, appRequest } from "@/lib/api";
import { CATEGORY_OPTIONS } from "@/lib/categories";
import { useToast } from "@/context/ToastContext";

type ListingResponse = {
  id: number;
  userId: number;
  title: string;
  description: string;
  category: string;
  condition: string;
  listingType: string;
  price: number | null;
  status: string;
  imageUrls: string[];
  createdAt: string;
};

type WishlistItemResponse = {
  id: number;
  listing: ListingResponse;
  savedAt: string;
};

type WishlistCard = {
  id: number;
  title: string;
  priceLabel: string;
  categoryLabel: string;
  savedLabel: string;
  mediaClass: string;
  imageUrl?: string;
  badge?: string;
};

type NeedMatch = {
  id: string;
  listing: ListingResponse;
  matchedAt: string;
};

type NeedRequest = {
  id: string;
  keywords: string;
  category: string | null;
  listingType: string | null;
  maxPrice: number | null;
  active: boolean;
  createdAt: string;
  matches: NeedMatch[];
};

const mediaPalette = [
  "media-sand",
  "media-clay",
  "media-brass",
  "media-linen",
  "media-slate"
];

const needRequestCategories = [
  { value: "BOOKS", label: "Books" },
  { value: "ELECTRONICS", label: "Electronics" },
  { value: "FURNITURE", label: "Furniture" },
  { value: "ACCESSORIES", label: "Accessories" },
  { value: "OTHER", label: "Other" }
];

const formatSavedLabel = (savedAt: string) => {
  const savedDate = new Date(savedAt);
  const today = new Date();
  const diff = Math.floor((today.getTime() - savedDate.getTime()) / 86400000);
  if (diff <= 0) return "Today";
  if (diff === 1) return "Yesterday";
  return savedDate.toLocaleDateString(undefined, { month: "short", day: "numeric" });
};

const formatCategoryLabel = (value: string) => {
  return value
    .toLowerCase()
    .split(/[_\s]+/)
    .map((part) => part.slice(0, 1).toUpperCase() + part.slice(1))
    .join(" ");
};

export default function WishlistPage() {
  const { showToast } = useToast();
  const [items, setItems] = useState<WishlistItemResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("Saved Items");
  const [sortBy, setSortBy] = useState("Date Added");
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const isSavedItems = activeTab === "Saved Items";

  // Saved Searches state with browser persistence
  const [savedSearches, setSavedSearches] = useState<{ id: string; query: string; createdAt: string }[]>([]);
  const [newSearchQuery, setNewSearchQuery] = useState("");
  const [needRequests, setNeedRequests] = useState<NeedRequest[]>([]);
  const [needKeywords, setNeedKeywords] = useState("");
  const [needCategory, setNeedCategory] = useState("");
  const [needListingType, setNeedListingType] = useState("");
  const [needMaxPrice, setNeedMaxPrice] = useState("");
  const [needBusy, setNeedBusy] = useState(false);

  useEffect(() => {
    try {
      const stored = localStorage.getItem("reusehub_saved_searches");
      if (stored) {
        setSavedSearches(JSON.parse(stored));
      }
    } catch {
      // ignore
    }
  }, []);

  const addSavedSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSearchQuery.trim()) return;
    const next = [
      { id: Date.now().toString(), query: newSearchQuery.trim(), createdAt: new Date().toISOString() },
      ...savedSearches
    ];
    setSavedSearches(next);
    localStorage.setItem("reusehub_saved_searches", JSON.stringify(next));
    setNewSearchQuery("");
    showToast("Search saved to your alert list!", "success");
  };

  const removeSavedSearch = (id: string) => {
    const next = savedSearches.filter((s) => s.id !== id);
    setSavedSearches(next);
    localStorage.setItem("reusehub_saved_searches", JSON.stringify(next));
    showToast("Saved search removed", "success");
  };

  useEffect(() => {
    let mounted = true;
    appRequest<WishlistItemResponse[]>("/api/wishlist")
      .then((data) => {
        if (!mounted) return;
        setItems(data);
        setError(null);
      })
      .catch((err) => {
        if (!mounted) return;
        setItems([]);
        const message = err instanceof Error ? err.message : "Unable to load wishlist";
        setError(message);
        showToast(message, "error");
      });
    apiRequest<NeedRequest[]>("/needs")
      .then((data) => {
        if (mounted) setNeedRequests(data);
      })
      .catch((err) => {
        if (mounted) {
          showToast(err instanceof Error ? err.message : "Unable to load need requests", "error");
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  const createNeedRequest = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!needKeywords.trim()) return;
    setNeedBusy(true);
    try {
      const request = await apiRequest<NeedRequest>("/needs", {
        method: "POST",
        body: JSON.stringify({
          keywords: needKeywords.trim(),
          category: needCategory || null,
          listingType: needListingType || null,
          maxPrice: needMaxPrice ? Number(needMaxPrice) : null
        })
      });
      setNeedRequests((previous) => [request, ...previous]);
      setNeedKeywords("");
      setNeedCategory("");
      setNeedListingType("");
      setNeedMaxPrice("");
      showToast("Need request saved. Matching listings are shown below.", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to save need request.", "error");
    } finally {
      setNeedBusy(false);
    }
  };

  const deleteNeedRequest = async (id: string) => {
    try {
      await apiRequest<void>(`/needs/${id}`, { method: "DELETE" });
      setNeedRequests((previous) => previous.filter((request) => request.id !== id));
      showToast("Need request removed.", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to remove need request.", "error");
    }
  };

  const publishNeedRequest = async (id: string) => {
    try {
      const published = await apiRequest<NeedRequest>(`/needs/${id}/publish`, { method: "PATCH" });
      setNeedRequests((previous) => previous.map((request) => request.id === id ? published : request));
      showToast("Your need request is live and matching listings now.", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to publish need request.", "error");
    }
  };

  const categoryOptions = useMemo(() => {
    const wishlistCategories = new Set(items.map((item) => item.listing.category));
    return CATEGORY_OPTIONS.filter((option) => wishlistCategories.has(option.value));
  }, [items]);

  const filteredItems = useMemo(() => {
    const filtered = selectedCategories.length === 0
      ? items
      : items.filter((item) => selectedCategories.includes(item.listing.category));

    const sorted = [...filtered];
    if (sortBy === "Price: Low to High") {
      sorted.sort((a, b) => (a.listing.price ?? 0) - (b.listing.price ?? 0));
    } else if (sortBy === "Price: High to Low") {
      sorted.sort((a, b) => (b.listing.price ?? 0) - (a.listing.price ?? 0));
    } else if (sortBy === "Newest") {
      sorted.sort((a, b) => new Date(b.listing.createdAt).getTime() - new Date(a.listing.createdAt).getTime());
    } else {
      sorted.sort((a, b) => new Date(b.savedAt).getTime() - new Date(a.savedAt).getTime());
    }

    return sorted;
  }, [items, selectedCategories, sortBy]);

  const cards = useMemo<WishlistCard[]>(() => {
    return filteredItems.map((item, index) => {
      const listing = item.listing;
      const priceLabel = listing.price !== null
        ? `₹${listing.price.toLocaleString()}`
        : listing.listingType === "SWAP"
        ? "Trade"
        : "Free";
      const categoryLabel = formatCategoryLabel(listing.category);
      const savedLabel = formatSavedLabel(item.savedAt);
      const imageUrl = listing.imageUrls?.[0];
      const mediaClass = imageUrl
        ? "wishlist-media photo"
        : `wishlist-media ${mediaPalette[index % mediaPalette.length]}`;
      const badge = index === 0 ? "FEATURED" : undefined;
      return {
        id: listing.id,
        title: listing.title,
        priceLabel,
        categoryLabel,
        savedLabel,
        mediaClass,
        imageUrl,
        badge
      };
    });
  }, [filteredItems]);

  const toggleCategory = (value: string) => {
    setSelectedCategories((prev) =>
      prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
    );
  };

  const removeItem = async (listingId: number) => {
    try {
      await appRequest<void>(`/api/wishlist/${listingId}`, { method: "DELETE" });
      setItems((prev) => prev.filter((item) => item.listing.id !== listingId));
      showToast("Item removed from your wishlist.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to remove item";
      setError(message);
      showToast(message, "error");
    }
  };

  return (
    <div className="home wishlist-page">
      <main className="page">
        <div className="breadcrumb">Home / My Wishlist</div>
        <div className="wishlist-header">
          <div>
            <h1>Wishlist</h1>
            <p>Track saved items, spot new finds, and measure impact.</p>
          </div>
          <div className="wishlist-tabs" role="tablist">
            <button
              className={`wishlist-tab ${activeTab === "Saved Items" ? "active" : ""}`}
              type="button"
              onClick={() => setActiveTab("Saved Items")}
            >
              Saved Items ({items.length})
            </button>
            <button
              className={`wishlist-tab ${activeTab === "Saved Searches" ? "active" : ""}`}
              type="button"
              onClick={() => setActiveTab("Saved Searches")}
            >
              Saved Searches ({savedSearches.length})
            </button>
            <button
              className={`wishlist-tab ${activeTab === "Need Requests" ? "active" : ""}`}
              type="button"
              onClick={() => setActiveTab("Need Requests")}
            >
              Need Requests ({needRequests.length})
            </button>
          </div>
        </div>

        <div className="wishlist-layout">
          <aside className="wishlist-filters">
            <div className="filter-block">
              <h3>Filters</h3>
              <span className="filter-sub">Sort by</span>
              <select
                className="filter-select"
                value={sortBy}
                onChange={(event) => setSortBy(event.target.value)}
              >
                <option>Date Added</option>
                <option>Newest</option>
                <option>Price: Low to High</option>
                <option>Price: High to Low</option>
              </select>
            </div>

            <div className="filter-block">
              <span className="filter-sub">Categories</span>
              <label className="filter-option">
                <input
                  type="checkbox"
                  checked={selectedCategories.length === 0}
                  onChange={() => setSelectedCategories([])}
                />
                All
              </label>
              {categoryOptions.map((category) => (
                <label className="filter-option" key={category.value}>
                  <input
                    type="checkbox"
                    checked={selectedCategories.includes(category.value)}
                    onChange={() => toggleCategory(category.value)}
                  />
                  {category.label}
                </label>
              ))}
            </div>

            <div className="wishlist-impact">
              <div className="impact-head">
                <span className="impact-dot" />
                <strong>Wishlist Impact</strong>
              </div>
              <p>
                You have saved {filteredItems.length} item{filteredItems.length === 1 ? "" : "s"}.
              </p>
              <div className="impact-meter">
                <span />
              </div>
            </div>
          </aside>

          <section className="wishlist-grid" aria-label="Saved items">
            {error ? <div className="error">{error}</div> : null}
            {isSavedItems ? (
              cards.map((card) => (
                <article className="wishlist-card" key={card.id}>
                  <div
                    className={card.mediaClass}
                    style={card.imageUrl ? { backgroundImage: `url(${card.imageUrl})` } : undefined}
                  >
                    {card.badge ? <span className="wishlist-badge">{card.badge}</span> : null}
                    <button
                      className="wishlist-heart"
                      type="button"
                      aria-label="Remove from wishlist"
                      onClick={() => removeItem(card.id)}
                    >
                      <span aria-hidden="true">&#9825;</span>
                    </button>
                  </div>
                  <div className="wishlist-body">
                    <div className="wishlist-price">{card.priceLabel}</div>
                    <h3>{card.title}</h3>
                    <div className="wishlist-meta">
                      <span>{card.categoryLabel}</span>
                      <span>{card.savedLabel}</span>
                    </div>
                  </div>
                </article>
              ))
            ) : activeTab === "Saved Searches" ? (
              <div style={{ width: "100%", maxWidth: "600px" }}>
                <form onSubmit={addSavedSearch} style={{ display: "flex", gap: "8px", marginBottom: "24px" }}>
                  <input
                    type="text"
                    placeholder="e.g. Graphic Calculator, Dorm Mirror..."
                    value={newSearchQuery}
                    onChange={(e) => setNewSearchQuery(e.target.value)}
                    style={{ flex: 1, padding: "10px 14px", borderRadius: "8px", border: "1px solid var(--line)" }}
                  />
                  <button className="btn primary" type="submit">
                    + Save Search
                  </button>
                </form>

                {savedSearches.length === 0 ? (
                  <div className="wishlist-empty">
                    No saved searches yet. Add terms above to quickly run your favorite marketplace searches!
                  </div>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                    {savedSearches.map((search) => (
                      <div
                        key={search.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "space-between",
                          padding: "14px 18px",
                          background: "#ffffff",
                          border: "1px solid var(--line)",
                          borderRadius: "12px"
                        }}
                      >
                        <div>
                          <strong>"{search.query}"</strong>
                          <span style={{ display: "block", fontSize: "0.8rem", color: "#888", marginTop: "2px" }}>
                            Added {formatSavedLabel(search.createdAt)}
                          </span>
                        </div>
                        <div style={{ display: "flex", gap: "8px" }}>
                          <Link
                            className="btn ghost"
                            style={{ padding: "6px 12px", fontSize: "0.85rem" }}
                            href={`/marketplace`}
                            onClick={() => {
                              // Setting search context will filter items
                            }}
                          >
                            Explore
                          </Link>
                          <button
                            type="button"
                            className="btn ghost"
                            style={{ padding: "6px 10px", color: "#ef4444" }}
                            onClick={() => removeSavedSearch(search.id)}
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <div style={{ width: "100%", maxWidth: "760px" }}>
                <p style={{ marginTop: 0, color: "var(--muted)" }}>
                  Describe what you need. Matching listings are saved here and you receive a notification when new matches appear.
                </p>
                <form onSubmit={createNeedRequest} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "10px", marginBottom: "20px" }}>
                  <input
                    required
                    maxLength={120}
                    placeholder="What are you looking for?"
                    value={needKeywords}
                    onChange={(event) => setNeedKeywords(event.target.value)}
                    style={{ gridColumn: "1 / -1", padding: "10px 12px", border: "1px solid var(--line)", borderRadius: "8px" }}
                  />
                  <select value={needCategory} onChange={(event) => setNeedCategory(event.target.value)} className="filter-select">
                    <option value="">Any category</option>
                    {needRequestCategories.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}
                  </select>
                  <select value={needListingType} onChange={(event) => setNeedListingType(event.target.value)} className="filter-select">
                    <option value="">Any listing type</option>
                    <option value="SELL">For sale</option>
                    <option value="SWAP">Swap</option>
                    <option value="DONATE">Free</option>
                  </select>
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    placeholder="Max price"
                    value={needMaxPrice}
                    onChange={(event) => setNeedMaxPrice(event.target.value)}
                    style={{ padding: "10px 12px", border: "1px solid var(--line)", borderRadius: "8px" }}
                  />
                  <button className="btn primary" type="submit" disabled={needBusy}>
                    {needBusy ? "Saving..." : "Create need request"}
                  </button>
                </form>

                {needRequests.length === 0 ? (
                  <div className="wishlist-empty">No need requests yet. Create one to get notified when matching listings are posted.</div>
                ) : (
                  <div style={{ display: "grid", gap: "14px" }}>
                    {needRequests.map((request) => (
                      <article key={request.id} style={{ padding: "16px", border: "1px solid var(--line)", borderRadius: "12px", background: "#fff" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", gap: "10px", alignItems: "flex-start" }}>
                          <div>
                            <strong>{request.keywords}</strong>
                            <div style={{ marginTop: "4px", color: "var(--muted)", fontSize: "13px" }}>
                              {[request.category && formatCategoryLabel(request.category), request.listingType, request.maxPrice !== null && `Max price ${request.maxPrice}`].filter(Boolean).join(" · ") || "Any category, type, or price"}
                            </div>
                          </div>
                          <button className="btn ghost" type="button" onClick={() => deleteNeedRequest(request.id)}>Remove</button>
                        </div>
                        <div style={{ marginTop: "12px", fontWeight: 600, fontSize: "13px" }}>
                          {request.active ? `${request.matches.length} match${request.matches.length === 1 ? "" : "es"}` : "Draft · not visible to other students"}
                        </div>
                        {!request.active && (
                          <button className="btn primary" type="button" style={{ marginTop: "10px" }} onClick={() => void publishNeedRequest(request.id)}>
                            Publish request
                          </button>
                        )}
                        {request.matches.length > 0 ? (
                          <div style={{ display: "grid", gap: "8px", marginTop: "8px" }}>
                            {request.matches.map((match) => (
                              <Link key={match.id} href={`/marketplace/${match.listing.id}`} style={{ display: "flex", justifyContent: "space-between", gap: "12px", padding: "10px", borderRadius: "8px", background: "#f8fafc", color: "inherit", textDecoration: "none" }}>
                                <span>{match.listing.title} · {formatCategoryLabel(match.listing.category)}</span>
                                <span>{match.listing.price === null ? "Free / swap" : `₹${match.listing.price}`}</span>
                              </Link>
                            ))}
                          </div>
                        ) : (
                          <p style={{ marginBottom: 0, color: "var(--muted)", fontSize: "13px" }}>No matches yet. New matching listings will appear here.</p>
                        )}
                      </article>
                    ))}
                  </div>
                )}
              </div>
            )}
          </section>
        </div>
      </main>

      <footer className="footer" id="community">
        <div>
          <h3>ReuseHub</h3>
          <p>The global marketplace for pre-loved sustainable goods.</p>
        </div>
        <div>
          <h4>Sustain</h4>
          <a href="#">Carbon Tracking</a>
          <a href="#">Impact Report</a>
          <a href="#">Seller Ethics</a>
        </div>
        <div>
          <h4>Community</h4>
          <a href="#">Blog</a>
          <a href="#">Events</a>
          <a href="#">Success Stories</a>
        </div>
        <div>
          <h4>Help</h4>
          <a href="#">Center</a>
          <a href="#">Trust &amp; Safety</a>
          <a href="#">Policy</a>
        </div>
      </footer>
    </div>
  );
}
