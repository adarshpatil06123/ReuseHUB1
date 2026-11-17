"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import DashboardSidebar from "@/components/DashboardSidebar";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type ListingResponse = {
  id: number;
  userId: number;
  title: string;
  description: string;
  category: string;
  condition: string;
  listingType: string;
  price: number | string | null;
  status: string;
  imageUrls: string[];
  createdAt: string;
};

type OrderResponse = {
  id: string;
  listingId: number;
  listingTitle: string;
  listingCategory: string;
  listingImageUrl: string | null;
  type: "BUY" | "SWAP" | "DONATE";
  status: string;
  offeredPrice: number | null;
  finalPrice: number | null;
  offeredListingId: number | null;
  offeredListingTitle?: string | null;
  offeredListingImageUrl?: string | null;
  message: string | null;
  role: "BUYER" | "SELLER";
  counterpartyName: string;
  createdAt: string;
  updatedAt: string;
  handoffDeadlineAt?: string | null;
};

type UserProfile = {
  id: number;
  name: string;
  email: string;
  college?: string;
  department?: string;
  ratingAvg?: number;
  ratingCount?: number;
};

type RentalSummary = {
  id: string;
  role: "OWNER" | "RENTER";
  status: string;
};

export default function DashboardPage() {
  const { showToast } = useToast();
  const [listings, setListings] = useState<ListingResponse[]>([]);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [rentals, setRentals] = useState<RentalSummary[]>([]);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionBusyId, setActionBusyId] = useState<string | null>(null);
  const [feedbackMsg, setFeedbackMsg] = useState<{ text: string; type: "success" | "error" } | null>(null);

  useEffect(() => {
    let mounted = true;

    Promise.all([
      apiRequest<UserProfile>("/users/me").catch(() => null),
      apiRequest<ListingResponse[]>("/listings/me").catch(() => []),
      apiRequest<OrderResponse[]>("/orders").catch(() => []),
      apiRequest<{ content: RentalSummary[] }>("/rentals?page=0&size=100").catch(() => ({ content: [] }))
    ]).then(([userData, listingsData, ordersData, rentalsData]) => {
      if (!mounted) return;
      if (userData) setUser(userData);
      setListings(listingsData || []);
      setOrders(ordersData || []);
      setRentals(rentalsData?.content || []);
      setLoading(false);
    });

    return () => {
      mounted = false;
    };
  }, []);

  // Priority 0: Incoming requests (where current user is the seller and status is pending)
  const incomingRequests = useMemo(() => {
    return orders.filter(
      (o) => o.role === "SELLER" && (o.status === "REQUESTED" || o.status === "ACCEPTED")
    );
  }, [orders]);

  // Priority 1: Real Active user listings
  const activeListings = useMemo(() => {
    return listings.filter((l) => l.status === "ACTIVE");
  }, [listings]);

  const rentalActionCount = useMemo(
    () => rentals.filter((rental) =>
      (rental.role === "OWNER" && rental.status === "PENDING")
      || ["APPROVED", "READY_FOR_HANDOVER", "ACTIVE", "RETURN_REQUESTED"].includes(rental.status)
    ).length,
    [rentals]
  );

  // Priority 1: Calculated Real Metrics
  const { totalEcoScore, carbonSavedKg, completedCount } = useMemo(() => {
    const completedOrders = orders.filter((o) => o.status === "COMPLETED");
    const count = completedOrders.length;
    // Estimated carbon footprint reduction: 2.5 kg per completed exchange, 0.8 kg per active listing
    const kg = (count * 2.5 + activeListings.length * 0.8).toFixed(1);
    // Real Eco Score: 50 pts per completed trade + 20 pts per active listing (starts at 0)
    const score = count * 50 + activeListings.length * 20;
    return {
      totalEcoScore: score,
      carbonSavedKg: kg,
      completedCount: count
    };
  }, [orders, activeListings]);

  // Priority 2: Milestone Gamification progress
  const nextMilestone = useMemo(() => {
    if (completedCount < 3) {
      return {
        badge: "Campus Reuser Badge",
        needed: 3 - completedCount,
        title: 'The "Campus Reuser" Badge is within reach!',
        description: `Complete ${3 - completedCount} more exchange${3 - completedCount === 1 ? "" : "s"} to reduce campus waste and unlock your next digital badge.`
      };
    } else if (completedCount < 10) {
      return {
        badge: "Plastic Pioneer Badge",
        needed: 10 - completedCount,
        title: 'The "Plastic Pioneer" Badge is within reach!',
        description: `Complete ${10 - completedCount} more swaps this semester to reduce another 5kg of landfill waste and reach Silver Tier.`
      };
    } else {
      return {
        badge: "Eco Champion Badge",
        needed: 0,
        title: "You are an Eco Champion!",
        description: "You have completed 10+ circular exchanges and established sustainable reuse on campus."
      };
    }
  }, [completedCount]);

  // Handle Order Accept / Reject
  const handleOrderStatus = async (orderId: string, newStatus: "ACCEPTED" | "REJECTED") => {
    setActionBusyId(orderId);
    setFeedbackMsg(null);
    try {
      const updated = await apiRequest<OrderResponse>(`/orders/${orderId}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: newStatus })
      });
      setOrders((prev) => prev.map((o) => (o.id === orderId ? updated : o)));

      const targetOrder = orders.find((o) => o.id === orderId);
      const isSwap = targetOrder?.type === "SWAP" || updated.type === "SWAP";
      const message = newStatus === "ACCEPTED"
        ? (isSwap
            ? "Swap accepted! 🔒 Dual-Resource Lock activated: Both items are now RESERVED."
            : "Request accepted! Listing is now RESERVED.")
        : "Request declined.";

      setFeedbackMsg({
        text: message,
        type: "success"
      });
      showToast(message, "success");
    } catch (err) {
      const rawMsg = err instanceof Error ? err.message : "Failed to update status";
      const friendlyMsg = rawMsg.toLowerCase().includes("conflict")
        ? `Conflict Prevention: ${rawMsg}`
        : rawMsg;
      setFeedbackMsg({
        text: friendlyMsg,
        type: "error"
      });
      showToast(friendlyMsg, "error");
    } finally {
      setActionBusyId(null);
    }
  };

  const formatPrice = (val: number | string | null, type?: string) => {
    if (type === "SWAP") return "Swap";
    if (type === "DONATE" || !val) return "Free";
    const num = Number(val);
    const price = Number.isNaN(num) ? `₹${val}` : `₹${num.toLocaleString("en-IN")}`;
    return type === "RENT" ? `${price} / day` : price;
  };

  return (
    <div className="home dashboard-page">
      <main className="page dashboard-shell">
        <DashboardSidebar />

        <section className="dashboard-main">
          {feedbackMsg && (
            <div
              style={{
                padding: "10px 16px",
                marginBottom: "16px",
                borderRadius: "12px",
                background: feedbackMsg.type === "success" ? "#e6f8ee" : "#fde7e7",
                color: feedbackMsg.type === "success" ? "#0d5c34" : "#8b2a2a",
                fontSize: "13px",
                fontWeight: 500
              }}
            >
              {feedbackMsg.text}
            </div>
          )}

          {/* Top Real-Time Stat Cards */}
          <div className="stat-grid">
            <div className="stat-card">
              <div>
                <span className="stat-label">Total Impact</span>
                <strong>{totalEcoScore}</strong>
                <span className="stat-sub">Eco Score • {completedCount} exchanges</span>
              </div>
              <div className="stat-icon">🌱</div>
            </div>
            <Link className="stat-card" href="/dashboard/rentals">
              <div>
                <span className="stat-label">Rental activity</span>
                <strong>{rentalActionCount}</strong>
                <span className="stat-sub">Requests and ongoing rentals · View activity</span>
              </div>
              <div className="stat-icon">↔</div>
            </Link>
            <div className="stat-card">
              <div>
                <span className="stat-label">Active Listings</span>
                <strong>{activeListings.length}</strong>
                <span className="stat-sub">{listings.length} total items listed</span>
              </div>
              <div className="stat-icon">📦</div>
            </div>
            <div className="stat-card">
              <div>
                <span className="stat-label">Carbon Saved</span>
                <strong>{carbonSavedKg} kg</strong>
                <span className="stat-sub">CO2 emissions avoided</span>
              </div>
              <div className="stat-icon">🌍</div>
            </div>
          </div>

          {/* Real Milestone Banner */}
          <div className="milestone-card">
            <div className="milestone-copy">
              <span className="milestone-pill">{nextMilestone.badge}</span>
              <h2>{nextMilestone.title}</h2>
              <p>{nextMilestone.description}</p>
              <Link className="btn primary" href="/marketplace">
                Explore Items to Swap
              </Link>
            </div>
            <div className="milestone-graphic">
              <div className="milestone-emblem">
                {completedCount >= 10 ? "🏆 Champion" : completedCount >= 3 ? "🥈 Silver" : "🥉 Bronze"}
              </div>
              <span>{completedCount} Completed Trades</span>
            </div>
          </div>

          <div className="dashboard-lower">
            {/* Real Active Listings Table */}
            <div className="listing-panel">
              <div className="panel-head">
                <h3>My Active Listings</h3>
                <Link className="link" href="/dashboard/listings">
                  Manage All ({listings.length}) →
                </Link>
              </div>

              {loading ? (
                <p style={{ fontSize: "13px", color: "var(--muted)", padding: "12px 0" }}>
                  Loading your listings...
                </p>
              ) : listings.length === 0 ? (
                <div style={{ textAlign: "center", padding: "24px 0", color: "var(--muted)" }}>
                  <p style={{ fontSize: "13px", margin: "0 0 12px" }}>You have no listings yet.</p>
                  <Link className="btn primary" href="/marketplace/add" style={{ fontSize: "12px" }}>
                    + List Your First Item
                  </Link>
                </div>
              ) : (
                <div className="listing-table">
                  <div className="table-row header">
                    <span>Item</span>
                    <span>Category</span>
                    <span>Status</span>
                    <span>Price</span>
                  </div>
                  {listings.slice(0, 5).map((item) => {
                    const thumb = item.imageUrls?.[0];
                    return (
                      <div className="table-row" key={item.id}>
                        <div className="table-item">
                          <div
                            className="table-thumb"
                            style={thumb ? { backgroundImage: `url(${thumb})`, backgroundSize: "cover" } : undefined}
                          />
                          <Link
                            href={`/marketplace/${item.id}`}
                            style={{ color: "inherit", textDecoration: "none", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                          >
                            {item.title}
                          </Link>
                        </div>
                        <span className="table-pill">{item.category}</span>
                        <span className={`status-pill ${item.status.toLowerCase()}`}>
                          {item.status}
                        </span>
                        <span style={{ fontWeight: 600, fontSize: "12px" }}>
                          {formatPrice(item.price, item.listingType)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Real Recent Requests Panel */}
            <div className="request-panel">
              <div className="panel-head">
                <h3>Incoming Requests</h3>
                <span className="badge">
                  {incomingRequests.filter((r) => r.status === "REQUESTED").length} New
                </span>
              </div>

              {loading ? (
                <p style={{ fontSize: "13px", color: "var(--muted)", padding: "12px 0" }}>
                  Loading requests...
                </p>
              ) : incomingRequests.length === 0 ? (
                <div style={{ textAlign: "center", padding: "24px 0", color: "var(--muted)", fontSize: "13px" }}>
                  <p style={{ margin: "0 0 8px" }}>No pending requests.</p>
                  <span>When someone wants to buy or swap your items, they'll appear here.</span>
                </div>
              ) : (
                incomingRequests.slice(0, 4).map((request) => {
                  const isPending = request.status === "REQUESTED";
                  return (
                    <div
                      className={`request-card ${isPending ? "highlight" : ""}`}
                      key={request.id}
                    >
                      <div className="request-user">
                        <div
                          className="avatar"
                          style={{
                            width: "36px",
                            height: "36px",
                            borderRadius: "50%",
                            background: "#e2e8f0",
                            display: "grid",
                            placeItems: "center",
                            fontWeight: 700,
                            fontSize: "13px"
                          }}
                        >
                          {request.counterpartyName?.[0]?.toUpperCase() || "U"}
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <strong style={{ display: "block", fontSize: "13px" }}>
                            {request.counterpartyName}
                          </strong>
                          <p style={{ margin: "2px 0", fontSize: "12px" }}>
                            {request.type === "SWAP" ? "🔄 Swap for" : "🛒 Buy"}{" "}
                            <strong>{request.listingTitle}</strong>
                          </p>
                          {request.type === "SWAP" && request.offeredListingTitle && (
                            <div style={{ fontSize: "11px", color: "var(--accent)", fontWeight: 600 }}>
                              ⇄ Offered Item: {request.offeredListingTitle}
                            </div>
                          )}
                          {request.offeredPrice && (
                            <span style={{ fontSize: "11px", color: "var(--accent)", fontWeight: 600, display: "block" }}>
                              Adjustment: ₹{request.offeredPrice.toLocaleString("en-IN")}
                            </span>
                          )}
                          {request.status === "ACCEPTED" && request.type === "SWAP" && (
                            <span style={{ fontSize: "10px", color: "#1d4ed8", fontWeight: 700, display: "inline-block", background: "#eff6ff", padding: "2px 6px", borderRadius: "6px", marginTop: "4px" }}>
                              🔒 Dual-Lock: Both Items Reserved
                            </span>
                          )}
                          {request.status === "ACCEPTED" && (
                            <Link
                              href="/dashboard/orders"
                              style={{ display: "block", marginTop: "5px", fontSize: "11px", color: "var(--accent)", fontWeight: 600 }}
                            >
                              Confirm the handoff in My Orders
                              {request.handoffDeadlineAt
                                ? ` by ${new Date(request.handoffDeadlineAt).toLocaleString()}`
                                : ""}
                            </Link>
                          )}
                        </div>
                      </div>

                      <div className="request-actions">
                        {isPending ? (
                          <>
                            <button
                              className="btn primary"
                              style={{ padding: "6px 12px", fontSize: "12px" }}
                              onClick={() => handleOrderStatus(request.id, "ACCEPTED")}
                              disabled={actionBusyId === request.id}
                            >
                              Accept
                            </button>
                            <button
                              className="btn ghost"
                              style={{ padding: "6px 12px", fontSize: "12px" }}
                              onClick={() => handleOrderStatus(request.id, "REJECTED")}
                              disabled={actionBusyId === request.id}
                            >
                              Decline
                            </button>
                          </>
                        ) : (
                          <Link
                            className="btn ghost"
                            style={{ padding: "6px 12px", fontSize: "12px" }}
                            href={`/marketplace/${request.listingId}/chat`}
                          >
                            Chat & Coordinate
                          </Link>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Empowering students to build a more sustainable future.</p>
        </div>
        <div>
          <h4>Platform</h4>
          <Link href="/marketplace">Browse Marketplace</Link>
          <Link href="/dashboard/listings">My Listings</Link>
          <Link href="/dashboard/orders">Orders & Swaps</Link>
        </div>
        <div>
          <h4>Community</h4>
          <Link href="/wishlist">Wishlist</Link>
          <Link href="/profile">My Profile</Link>
        </div>
        <div>
          <h4>Support</h4>
          <a href="#">Help Center</a>
          <a href="#">Privacy Policy</a>
          <a href="#">Terms of Service</a>
        </div>
      </footer>
    </div>
  );
}
