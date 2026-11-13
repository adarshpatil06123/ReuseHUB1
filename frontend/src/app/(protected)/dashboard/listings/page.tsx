"use client";

import Link from "next/link";
import DashboardSidebar from "@/components/DashboardSidebar";
import { useEffect, useMemo, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

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

type ListingEvent = {
  action: string;
  listingId: number;
  listing: ListingResponse | null;
};

type UserInfo = {
  id: number;
  name: string;
};

type RelistingSuggestion = {
  listingId: number;
  listingTitle: string;
  currentPrice: number | null;
  suggestedPrice: number | null;
  inactiveSince: string;
  advice: string;
};

const tabs = ["All Items", "Active", "Pending", "Sold"] as const;

type TabKey = (typeof tabs)[number];

const statusOptions = ["ACTIVE", "RESERVED", "SOLD", "SWAPPED", "DONATED"] as const;

const sortOptions = [
  { value: "NEWEST", label: "Newest" },
  { value: "OLDEST", label: "Oldest" },
  { value: "TITLE", label: "Title" },
  { value: "STATUS", label: "Status" }
] as const;

type SortKey = (typeof sortOptions)[number]["value"];

const pageSize = 10;

export default function ListingsPage() {
  const { showToast } = useToast();
  const [listings, setListings] = useState<ListingResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabKey>("All Items");
  const [userId, setUserId] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState<SortKey>("NEWEST");
  const [page, setPage] = useState(1);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editPrice, setEditPrice] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [relistingSuggestions, setRelistingSuggestions] = useState<RelistingSuggestion[]>([]);
  const [suggestionBusyId, setSuggestionBusyId] = useState<number | null>(null);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    let mounted = true;

    apiRequest<UserInfo>("/users/me")
      .then((user) => {
        if (!mounted) return;
        setUserId(user.id);
      })
      .catch(() => {
        if (!mounted) return;
        setUserId(null);
      });

    apiRequest<ListingResponse[]>("/listings/me")
      .then((data) => {
        if (!mounted) return;
        setListings(data);
        setError(null);
      })
      .catch((err) => {
        if (!mounted) return;
        setListings([]);
        const message = err instanceof Error ? err.message : "Unable to load listings";
        setError(message);
        showToast(message, "error");
      });
    apiRequest<RelistingSuggestion[]>("/listings/me/relisting-suggestions")
      .then((data) => {
        if (mounted) setRelistingSuggestions(data);
      })
      .catch((err) => {
        if (mounted) {
          showToast(err instanceof Error ? err.message : "Unable to load relisting suggestions", "error");
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!userId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws/updates`),
      reconnectDelay: 3000,
      debug: () => {}
    });

    client.onConnect = () => {
      client.subscribe(`/topic/listings/${userId}`, (frame) => {
        const payload = JSON.parse(frame.body) as ListingEvent;
        if (payload.action === "DELETED") {
          setListings((prev) => prev.filter((listing) => listing.id !== payload.listingId));
          return;
        }
        if (!payload.listing) return;
        setListings((prev) => {
          const index = prev.findIndex((listing) => listing.id === payload.listing!.id);
          if (index >= 0) {
            const updated = [...prev];
            updated[index] = payload.listing!;
            return updated;
          }
          return [payload.listing!, ...prev];
        });
      });
    };

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [userId]);

  const filteredListings = useMemo(() => {
    if (activeTab === "Active") {
      return listings.filter((listing) => listing.status === "ACTIVE");
    }
    if (activeTab === "Pending") {
      return listings.filter((listing) => listing.status === "RESERVED");
    }
    if (activeTab === "Sold") {
      return listings.filter((listing) =>
        ["SOLD", "SWAPPED", "DONATED"].includes(listing.status)
      );
    }
    return listings;
  }, [listings, activeTab]);

  const sortedListings = useMemo(() => {
    const data = [...filteredListings];
    if (sortBy === "NEWEST") {
      return data.sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
    }
    if (sortBy === "OLDEST") {
      return data.sort((a, b) => (a.createdAt || "").localeCompare(b.createdAt || ""));
    }
    if (sortBy === "TITLE") {
      return data.sort((a, b) => a.title.localeCompare(b.title));
    }
    return data.sort((a, b) => a.status.localeCompare(b.status));
  }, [filteredListings, sortBy]);

  const totalPages = Math.max(1, Math.ceil(sortedListings.length / pageSize));
  const pagedListings = useMemo(() => {
    const start = (page - 1) * pageSize;
    return sortedListings.slice(start, start + pageSize);
  }, [sortedListings, page]);

  const pointsForListing = (listing: ListingResponse) => {
    const base = listing.listingType === "DONATE" ? 200 : listing.listingType === "SWAP" ? 150 : 120;
    const conditionBoost = listing.condition === "NEW"
      ? 20
      : listing.condition === "LIKE_NEW"
      ? 16
      : listing.condition === "GOOD"
      ? 10
      : 6;
    const categoryBoost = listing.category === "ELECTRONICS"
      ? 30
      : listing.category === "FURNITURE"
      ? 25
      : listing.category === "BOOKS"
      ? 12
      : listing.category === "ACCESSORIES"
      ? 14
      : 10;
    return base + conditionBoost + categoryBoost;
  };

  const startEdit = (listing: ListingResponse) => {
    setEditingId(listing.id);
    setEditTitle(listing.title);
    setEditDescription(listing.description);
    setEditPrice(listing.price ? listing.price.toString() : "");
    setError(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditTitle("");
    setEditDescription("");
    setEditPrice("");
    setError(null);
  };

  const saveEdit = async (listing: ListingResponse) => {
    setError(null);
    try {
      const payload = {
        title: editTitle.trim() || listing.title,
        description: editDescription.trim() || listing.description,
        category: listing.category,
        condition: listing.condition,
        listingType: listing.listingType,
        price: listing.listingType === "SELL"
          ? Number(editPrice || listing.price || 0)
          : null,
        imageUrls: listing.imageUrls
      };
      const updated = await apiRequest<ListingResponse>(`/listings/${listing.id}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      });
      setListings((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      cancelEdit();
      showToast("Listing updated successfully.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to update listing";
      setError(message);
      showToast(message, "error");
    }
  };

  const [deletingId, setDeletingId] = useState<number | null>(null);

  const deleteListing = async (listing: ListingResponse) => {
    if (!window.confirm(`Are you sure you want to permanently delete "${listing.title}"?`)) {
      return;
    }
    setDeletingId(listing.id);
    try {
      await apiRequest<void>(`/listings/${listing.id}`, {
        method: "DELETE"
      });
      setListings((prev) => prev.filter((item) => item.id !== listing.id));
      showToast("Listing deleted successfully.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to delete listing";
      setError(message);
      showToast(message, "error");
    } finally {
      setDeletingId(null);
    }
  };

  const updateStatus = async (listing: ListingResponse, status: string) => {
    if (status === listing.status) return;
    try {
      const updated = await apiRequest<ListingResponse>(`/listings/${listing.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status })
      });
      setListings((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
      showToast("Listing status updated.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to update status";
      setError(message);
      showToast(message, "error");
    }
  };

  const dismissRelistingSuggestion = async (suggestion: RelistingSuggestion) => {
    setSuggestionBusyId(suggestion.listingId);
    try {
      await apiRequest<void>(`/listings/${suggestion.listingId}/relisting-suggestion/dismiss`, {
        method: "POST"
      });
      setRelistingSuggestions((previous) => previous.filter((item) => item.listingId !== suggestion.listingId));
      showToast("Relisting suggestion dismissed.", "success");
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to dismiss this suggestion.", "error");
    } finally {
      setSuggestionBusyId(null);
    }
  };

  const applyRelistingSuggestion = async (suggestion: RelistingSuggestion) => {
    const listing = listings.find((item) => item.id === suggestion.listingId);
    if (!listing || suggestion.suggestedPrice === null) {
      showToast("This listing has no suggested price. Edit it to refresh the details or photos.", "error");
      return;
    }
    setSuggestionBusyId(suggestion.listingId);
    try {
      const updated = await apiRequest<ListingResponse>(`/listings/${listing.id}`, {
        method: "PUT",
        body: JSON.stringify({
          title: listing.title,
          description: listing.description,
          category: listing.category,
          condition: listing.condition,
          listingType: listing.listingType,
          price: suggestion.suggestedPrice,
          imageUrls: listing.imageUrls
        })
      });
      setListings((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      try {
        await apiRequest<void>(`/listings/${suggestion.listingId}/relisting-suggestion/dismiss`, {
          method: "POST"
        });
        setRelistingSuggestions((previous) => previous.filter((item) => item.listingId !== suggestion.listingId));
        showToast("Suggested price applied and reminder dismissed.", "success");
      } catch (err) {
        showToast(`Price applied, but the reminder could not be dismissed: ${err instanceof Error ? err.message : "request failed"}`, "error");
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to apply this suggestion.", "error");
    } finally {
      setSuggestionBusyId(null);
    }
  };

  const counts = useMemo(() => {
    return {
      all: listings.length,
      active: listings.filter((listing) => listing.status === "ACTIVE").length,
      pending: listings.filter((listing) => listing.status === "RESERVED").length,
      sold: listings.filter((listing) => ["SOLD", "SWAPPED", "DONATED"].includes(listing.status)).length
    };
  }, [listings]);

  useEffect(() => {
    setPage(1);
  }, [activeTab, sortBy]);

  return (
    <div className="home listings-page">
      <main className="page dashboard-shell listings-shell">
        <DashboardSidebar />

        <section className="listings-main">
          <div className="listings-header">
            <div>
              <span className="section-kicker">Your Inventory</span>
              <h1>My Listings</h1>
              <p>Manage your circular lifestyle. Track your items from listing to success.</p>
            </div>
            <div className="listings-tabs">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  className={`tab-pill ${activeTab === tab ? "active" : ""}`}
                  onClick={() => setActiveTab(tab)}
                >
                  {tab === "All Items"
                    ? `All Items (${counts.all})`
                    : tab === "Active"
                    ? `Active (${counts.active})`
                    : tab === "Pending"
                    ? `Pending (${counts.pending})`
                    : `Sold (${counts.sold})`}
                </button>
              ))}
            </div>
          </div>

          <div className="listings-toolbar">
            <div className="sort">
              <span>Sort by:</span>
              <select value={sortBy} onChange={(event) => setSortBy(event.target.value as SortKey)}>
                {sortOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {relistingSuggestions.length > 0 ? (
            <section aria-label="Relisting suggestions" style={{ margin: "16px 0", padding: "16px", border: "1px solid #fde68a", borderRadius: "12px", background: "#fffbeb" }}>
              <h2 style={{ margin: "0 0 10px", fontSize: "16px" }}>Listing suggestions</h2>
              <div style={{ display: "grid", gap: "10px" }}>
                {relistingSuggestions.map((suggestion) => (
                  <article key={suggestion.listingId} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "12px", flexWrap: "wrap" }}>
                    <div>
                      <strong>{suggestion.listingTitle}</strong>
                      <p style={{ margin: "4px 0 0", color: "#78350f", fontSize: "13px" }}>
                        {suggestion.advice}
                        {suggestion.suggestedPrice !== null ? ` Suggested price: ₹${suggestion.suggestedPrice}.` : ""}
                      </p>
                    </div>
                    <div style={{ display: "flex", gap: "8px" }}>
                      {suggestion.suggestedPrice !== null ? (
                        <button className="btn primary" type="button" disabled={suggestionBusyId === suggestion.listingId} onClick={() => applyRelistingSuggestion(suggestion)}>
                          Apply price
                        </button>
                      ) : null}
                      <button className="btn ghost" type="button" disabled={suggestionBusyId === suggestion.listingId} onClick={() => dismissRelistingSuggestion(suggestion)}>
                        Dismiss
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          <div className="listings-highlight">
            <div className="highlight-card">
              <div>
                <h3>Ready to list something new?</h3>
                <p>
                  Each item you reuse saves an average of 4.5kg of CO2 from entering the
                  atmosphere. Join the circular movement.
                </p>
                <Link className="btn primary" href="/marketplace/add">
                  Create New Listing
                </Link>
              </div>
              <div className="leaf-card" />
            </div>
            <div className="impact-stat">
              <span>Community Impact</span>
              <strong>124kg</strong>
              <p>CO2 Emissions Prevented</p>
              <small>72% of your monthly goal reached</small>
            </div>
          </div>

          {error ? <div className="error">{error}</div> : null}

          <div className="listing-grid-alt">
            {pagedListings.map((listing) => (
              <article className="listing-tile" key={listing.id}>
                <div
                  className="listing-media"
                  style={
                    listing.imageUrls[0]
                      ? { backgroundImage: `url(${listing.imageUrls[0]})` }
                      : undefined
                  }
                >
                  <span className={`status-pill ${listing.status.toLowerCase()}`}>
                    {listing.status}
                  </span>
                </div>
                <div className="listing-tile-body">
                  <div>
                    <h3>{listing.title}</h3>
                    <span className="listing-meta">{listing.category}</span>
                    <span style={{ display: "inline-block", fontSize: "10px", color: "#15803d", background: "#f0fdf4", padding: "2px 6px", borderRadius: "4px", border: "1px solid #bbf7d0", marginTop: "3px" }}>
                      🛡️ Auto-Invalidation Protected
                    </span>
                  </div>
                  <span className="listing-points">
                    {pointsForListing(listing)} pts
                  </span>
                </div>
                <div className="listing-tile-actions">
                  {editingId === listing.id ? (
                    <div className="edit-panel">
                      <div style={{ padding: "8px 10px", background: "#fef3c7", border: "1px solid #fde68a", borderRadius: "8px", fontSize: "11px", color: "#92400e", marginBottom: "8px" }}>
                        ⚠️ <strong>Auto-Invalidation Active:</strong> Modifying price or details will automatically invalidate open buyer requests to prevent transaction disputes.
                      </div>
                      <input
                        value={editTitle}
                        onChange={(event) => setEditTitle(event.target.value)}
                        placeholder="Title"
                      />
                      <textarea
                        value={editDescription}
                        onChange={(event) => setEditDescription(event.target.value)}
                        placeholder="Description"
                      />
                      {listing.listingType === "SELL" ? (
                        <div className="price-input compact">
                          <span>₹</span>
                          <input
                            type="number"
                            min="0"
                            step="1"
                            value={editPrice}
                            onChange={(event) => setEditPrice(event.target.value)}
                          />
                        </div>
                      ) : null}
                      <div className="edit-actions">
                        <button className="btn ghost" onClick={cancelEdit}>
                          Cancel
                        </button>
                        <button className="btn primary" onClick={() => saveEdit(listing)}>
                          Save
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className="listing-actions-row">
                        <button className="btn ghost" onClick={() => startEdit(listing)}>
                          Edit
                        </button>
                        <select
                          className="status-select"
                          value={listing.status}
                          onChange={(event) => updateStatus(listing, event.target.value)}
                        >
                          {statusOptions.map((status) => (
                            <option key={status} value={status}>
                              {status}
                            </option>
                          ))}
                        </select>
                        <button
                          className="btn ghost"
                          style={{
                            color: "#dc2626",
                            borderColor: "rgba(220, 38, 38, 0.2)",
                            padding: "6px 12px",
                            marginLeft: "auto"
                          }}
                          onClick={() => deleteListing(listing)}
                          disabled={deletingId === listing.id}
                        >
                          {deletingId === listing.id ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                      <Link className="btn ghost" href={`/marketplace/${listing.id}`}>
                        View Details
                      </Link>
                    </>
                  )}
                </div>
              </article>
            ))}
            <Link className="listing-tile add" href="/marketplace/add">
              <div className="add-tile">
                <div className="add-icon">+</div>
                <strong>List Another Item</strong>
                <span>Clear out your space and earn more credits.</span>
              </div>
            </Link>
          </div>

          {totalPages > 1 ? (
            <div className="pagination">
              <button className="btn ghost" onClick={() => setPage((prev) => Math.max(1, prev - 1))}>
                Previous
              </button>
              <div className="page-numbers">{page} / {totalPages}</div>
              <button className="btn ghost" onClick={() => setPage((prev) => Math.min(totalPages, prev + 1))}>
                Next
              </button>
            </div>
          ) : null}
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Circularity for a Greener Planet.</p>
        </div>
        <div>
          <h4>Resources</h4>
          <a href="#">Sustainability Report</a>
          <a href="#">Ethical Guidelines</a>
          <a href="#">Help Center</a>
        </div>
        <div>
          <h4>Community</h4>
          <a href="#">Community Forum</a>
          <a href="#">Carbon Credits</a>
        </div>
        <div>
          <h4>Legal</h4>
          <a href="#">Privacy Policy</a>
          <a href="#">Terms of Service</a>
        </div>
      </footer>
    </div>
  );
}
