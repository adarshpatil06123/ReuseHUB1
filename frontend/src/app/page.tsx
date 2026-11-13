"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useSearch } from "@/context/SearchContext";
import { apiRequest } from "@/lib/api";

type ListingItem = {
  id: number;
  title: string;
  price: string | number | null;
  condition: string;
  category: string;
  imageUrls: string[];
};

type DemandZone = {
  location: string;
  wishlistSaves: number;
  openRequests: number;
  needRequests: number;
  demandScore: number;
};

type PublicNeedRequest = {
  id: string;
  userId: number;
  name: string;
  keywords: string;
  preferredLocation: string;
  maxPrice: number | null;
  category: string | null;
  createdAt: string;
};

const DemandHeatmapMap = dynamic(() => import("@/components/DemandHeatmapMap"), {
  ssr: false,
  loading: () => <p className="demand-heatmap-state" role="status">Loading campus map…</p>
});

const formatTimeAgo = (createdAt: string) => {
  const createdAtMs = new Date(createdAt).getTime();
  if (Number.isNaN(createdAtMs)) return "";
  const elapsedMinutes = Math.max(0, Math.floor((Date.now() - createdAtMs) / 60_000));
  if (elapsedMinutes < 1) return "just now";
  if (elapsedMinutes < 60) return `${elapsedMinutes}m ago`;
  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) return `${elapsedHours}h ago`;
  const elapsedDays = Math.floor(elapsedHours / 24);
  return `${elapsedDays}d ago`;
};

export default function HomePage() {
  const router = useRouter();
  const { searchQuery, setSearchQuery } = useSearch();
  const [recentListings, setRecentListings] = useState<ListingItem[]>([]);
  const [demandZones, setDemandZones] = useState<DemandZone[]>([]);
  const [publicNeedRequests, setPublicNeedRequests] = useState<PublicNeedRequest[]>([]);
  const [demandLoading, setDemandLoading] = useState(true);
  const [demandRefreshing, setDemandRefreshing] = useState(false);
  const [demandError, setDemandError] = useState<string | null>(null);
  const [demandUpdatedAt, setDemandUpdatedAt] = useState<Date | null>(null);
  const [selectedDemandZone, setSelectedDemandZone] = useState<string | null>(null);

  // Load real-time listings from database
  useEffect(() => {
    apiRequest<ListingItem[]>("/listings")
      .then((data) => {
        if (data && data.length > 0) {
          setRecentListings(data.slice(0, 4));
        }
      })
      .catch(() => {
        // Keeps graceful defaults if offline
      });
  }, []);

  useEffect(() => {
    let active = true;
    const refreshDemand = async (isInitialLoad = false) => {
      if (isInitialLoad) {
        setDemandLoading(true);
      } else {
        setDemandRefreshing(true);
      }
      try {
        const [zones, publicRequests] = await Promise.all([
          apiRequest<DemandZone[]>("/demand/heatmap"),
          apiRequest<PublicNeedRequest[]>("/demand/requests")
        ]);
        if (!active) return;
        setDemandZones(zones);
        setPublicNeedRequests(publicRequests.slice(0, 6));
        setDemandUpdatedAt(new Date());
        setDemandError(null);
      } catch (error) {
        if (!active) return;
        setDemandError(error instanceof Error ? error.message : "Unable to load campus demand.");
      } finally {
        if (active) {
          setDemandLoading(false);
          setDemandRefreshing(false);
        }
      }
    };

    void refreshDemand(true);
    const intervalId = window.setInterval(() => {
      void refreshDemand();
    }, 60_000);
    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  const refreshDemandNow = async () => {
    setDemandRefreshing(true);
    try {
      const [zones, publicRequests] = await Promise.all([
        apiRequest<DemandZone[]>("/demand/heatmap"),
        apiRequest<PublicNeedRequest[]>("/demand/requests")
      ]);
      setDemandZones(zones);
      setPublicNeedRequests(publicRequests.slice(0, 6));
      setDemandUpdatedAt(new Date());
      setDemandError(null);
    } catch (error) {
      setDemandError(error instanceof Error ? error.message : "Unable to refresh campus demand.");
    } finally {
      setDemandRefreshing(false);
    }
  };

  const visibleDemandZones = selectedDemandZone
    ? demandZones.filter((zone) => zone.location === selectedDemandZone)
    : demandZones;
  const highestDemandScore = Math.max(...demandZones.map((zone) => zone.demandScore), 1);

  return (
    <div className="home">
      <header className="topbar">
        <Link className="logo" href="/">
          ReuseHub
        </Link>
        <nav className="nav">
          <Link className="active" href="/marketplace">
            Marketplace
          </Link>
          <Link href="/wishlist">Wishlist</Link>
          <Link href="/dashboard">Dashboard</Link>
        </nav>
        <div className="topbar-actions">
          <div className="search">
            <input
              type="search"
              placeholder="Search marketplace..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                router.push("/marketplace");
              }}
            />
          </div>
          <Link className="logout-link" href="/logout">
            Logout
          </Link>
          <Link className="avatar" href="/profile">
            A
          </Link>
        </div>
      </header>

      <main className="page">
        {/* HERO SECTION */}
        <section className="hero-grid" id="marketplace">
          <div className="hero-copy">
            <span className="pill">The campus circular economy</span>
            <h1>
              Reuse Smart. <span>Buy Better.</span> Build a Sustainable Campus.
            </h1>
            <p>
              Join the student marketplace where sustainability meets affordability.
              Swap, sell, and find pre-loved campus essentials right on campus.
            </p>

            <div className="hero-cta" style={{ marginTop: "28px" }}>
              <Link className="btn primary" href="/marketplace">
                Explore Items
              </Link>
              <Link className="btn ghost" href="/marketplace/add">
                Sell an Item
              </Link>
            </div>
          </div>

          <div className="hero-media">
            <div className="media-grid">
              <div className="img-card laptop" />
              <div className="img-card lifestyle" />
              <div className="img-card stack" />
              <div className="img-card desk" />
            </div>
          </div>
        </section>

        {/* LIVE DATABASE CAMPUS FAVORITES */}
        <section className="listings" aria-label="Recently listed">
          <div className="section-head">
            <div>
              <span className="section-kicker">Live from Database</span>
              <h2>Campus Favorites & Trending</h2>
            </div>
            <Link className="link" href="/marketplace">
              View All Marketplace &rarr;
            </Link>
          </div>
          {recentListings.length === 0 ? (
            <p>No listings yet. Be the first to add one.</p>
          ) : (
            <div className="listing-grid">
              {recentListings.map((item) => {
                const cover = item.imageUrls?.[0];
                return (
                  <Link
                    key={item.id}
                    href={`/marketplace/${item.id}`}
                    style={{ textDecoration: "none", color: "inherit" }}
                  >
                    <article className="listing-card" style={{ borderRadius: "14px" }}>
                      <div
                        className="listing-image-box"
                        style={{
                          height: "110px",
                          background: cover ? `url(${cover}) center/cover no-repeat` : "#f3f4f6",
                          borderRadius: "14px 14px 0 0"
                        }}
                      />
                      <div className="badge" style={{ top: "8px", left: "8px", fontSize: "9px", padding: "2px 6px" }}>
                        {item.condition.replace("_", " ")}
                      </div>
                      <div className="listing-body" style={{ padding: "8px 12px 10px" }}>
                        <h3 style={{ fontSize: "13px", margin: "0 0 2px", fontWeight: 600 }}>{item.title}</h3>
                        <p style={{ fontSize: "11px" }}>₹{Number(item.price || 0).toLocaleString()} • {item.category}</p>
                      </div>
                    </article>
                  </Link>
                );
              })}
            </div>
          )}
        </section>

        <section className="demand-heatmap" aria-labelledby="demand-heatmap-title">
          <div className="demand-heatmap-header">
            <div className="demand-heatmap-heading">
              <span className="demand-live-label"><span aria-hidden="true" /> Live campus radar</span>
              <h2 id="demand-heatmap-title">Campus Demand Heat Map</h2>
              <p>
                Real-time requested essentials across campus. See where students are showing interest.
              </p>
            </div>
            <div className="demand-heatmap-controls">
              <div className="demand-zone-filters" role="group" aria-label="Filter demand by campus zone">
                <button
                  type="button"
                  className={selectedDemandZone === null ? "active" : ""}
                  onClick={() => setSelectedDemandZone(null)}
                >
                  All Zones
                </button>
                {demandZones.map((zone) => (
                  <button
                    type="button"
                    className={selectedDemandZone === zone.location ? "active" : ""}
                    key={zone.location}
                    onClick={() => setSelectedDemandZone(zone.location)}
                  >
                    {zone.location}
                  </button>
                ))}
              </div>
              <Link className="demand-post-request" href="/needs/new">
                + Post a Need Request
              </Link>
            </div>
          </div>

          <div className="demand-heatmap-status">
            <span className="demand-heatmap-updated" aria-live="polite">
              {demandUpdatedAt
                ? `Updated ${demandUpdatedAt.toLocaleTimeString()}`
                : "Waiting for first update"}
              {" · "}Auto-refresh: 60 sec
            </span>
            <button
              className="demand-refresh-button"
              type="button"
              onClick={refreshDemandNow}
              disabled={demandLoading || demandRefreshing}
            >
              {demandRefreshing ? "Refreshing…" : "Refresh now"}
            </button>
          </div>

          {demandLoading ? (
            <p className="demand-heatmap-state" role="status">Loading campus demand…</p>
          ) : demandError && demandZones.length === 0 ? (
            <p className="demand-heatmap-state error" role="alert">{demandError}</p>
          ) : demandZones.length === 0 ? (
            <p className="demand-heatmap-state">
              No demand activity to show yet. Wishlist saves and requests for active listings will appear here.
            </p>
          ) : (
            <>
              {demandError ? (
                <p className="demand-heatmap-state error" role="alert">
                  Could not refresh demand. Showing the last successful update: {demandError}
                </p>
              ) : null}
              <div className="demand-heatmap-content">
                <div className="demand-heatmap-primary">
                  <div className="demand-campus-map" role="group" aria-label="Approximate campus demand heatmap">
                    <DemandHeatmapMap zones={visibleDemandZones} />
                    {visibleDemandZones[0] ? (
                      <div className="demand-map-callout">
                        <span className="demand-map-pulse" aria-hidden="true" />
                        Highest activity: {visibleDemandZones[0].location}
                      </div>
                    ) : null}
                  </div>
                  <div className="demand-card-heading">
                    <h3>High-frequency demand zones</h3>
                    <div className="demand-activity-legend">
                      <span><i className="peak" /> Peak</span>
                      <span><i className="high" /> High</span>
                      <span><i className="steady" /> Steady</span>
                    </div>
                  </div>
                  <div className="demand-zone-grid">
                    {visibleDemandZones.map((zone) => {
                      const intensity = zone.demandScore / highestDemandScore;
                      const percent = Math.max(1, Math.round(intensity * 100));
                      const level = intensity >= 0.8 ? "peak" : intensity >= 0.5 ? "high" : "steady";
                      return (
                        <article className={`demand-zone ${level}`} key={zone.location}>
                          <div className="demand-zone-heading">
                            <span className="demand-zone-level">
                              <i aria-hidden="true" />
                              {level === "peak" ? "Peak demand" : level === "high" ? "High demand" : "Steady interest"}
                            </span>
                            <strong className="demand-zone-percent">{percent}%</strong>
                          </div>
                          <h4>{zone.location}</h4>
                          <p>{zone.needRequests} need requests · {zone.openRequests} open orders · {zone.wishlistSaves} wishlist saves</p>
                          <div
                            className="demand-zone-progress"
                            role="meter"
                            aria-label={`${zone.location} relative demand`}
                            aria-valuemin={0}
                            aria-valuemax={100}
                            aria-valuenow={percent}
                          >
                            <span style={{ width: `${percent}%` }} />
                          </div>
                          <div className="demand-zone-footer">
                            <span>{zone.demandScore} total interest signals</span>
                            <button type="button" onClick={() => setSelectedDemandZone(zone.location)}>
                              View on map <span aria-hidden="true">→</span>
                            </button>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>

                <aside className="demand-live-panel" aria-label="Live campus demand by zone">
                  <div className="demand-live-panel-header">
                    <h3><span aria-hidden="true" /> Live campus requests</h3>
                    <span>{demandRefreshing ? "Updating…" : "Updated now"}</span>
                  </div>
                  <div className="demand-live-list">
                    {publicNeedRequests.length > 0 ? publicNeedRequests.map((request) => {
                      const maxPriceLabel = request.maxPrice != null
                        ? `₹${Number(request.maxPrice).toLocaleString("en-IN")}`
                        : "Open to offers";

                      return (
                        <article className="demand-request-card" key={request.id}>
                          <div className="demand-request-card-top">
                            <strong>{request.name} <span>· {request.preferredLocation}</span></strong>
                            <time dateTime={request.createdAt}>{formatTimeAgo(request.createdAt)}</time>
                          </div>
                          <p>Wants: {request.keywords}</p>
                          <div className="demand-request-card-bottom">
                            <span>Willing to pay: {maxPriceLabel}</span>
                            <Link href={`/marketplace/0/chat?userId=${request.userId}`}>Message</Link>
                          </div>
                        </article>
                      );
                    }) : (
                      <p className="demand-request-empty">No public requests yet. New campus requests will appear here.</p>
                    )}
                  </div>
                  <Link href="/needs/new" className="demand-live-cta">
                    Have a need? Post a Request
                  </Link>
                </aside>
              </div>
            </>
          )}
        </section>

        {/* HOW IT WORKS PROCESS */}
        <section className="process" id="tracker">
          <span className="section-kicker">The process</span>
          <h2>Simple. Sustainable. Smart.</h2>
          <p>Three steps to reducing your footprint and upgrading your student life.</p>
          <div className="process-steps">
            <div>
              <div className="step">1</div>
              <h3>List Your Item</h3>
              <p>Snap a photo, set a price, and tag your impact in under a minute.</p>
            </div>
            <div>
              <div className="step">2</div>
              <h3>Connect Safely</h3>
              <p>Chat in real-time with verified students on campus. No external links.</p>
            </div>
            <div>
              <div className="step">3</div>
              <h3>Exchange Locally</h3>
              <p>Meet at a campus hotspot and complete the trade securely.</p>
            </div>
          </div>
        </section>

        {/* WHY CIRCULAR */}
        <section className="why" id="community">
          <div className="why-copy">
            <span className="section-kicker">Why circular?</span>
            <h2>Keeping Value Within Our Community.</h2>
            <p>
              The traditional "take-make-waste" model is broken. ReuseHub keeps
              everyday essentials circulating inside campus, reducing emissions and
              building a stronger local student network.
            </p>
            <ul>
              <li>Zero shipping waste</li>
              <li>Student email verification</li>
            </ul>
          </div>
          <div className="why-cards">
            <div className="mini-card">
              <h4>Circular Model</h4>
              <p>Reuse items longer and reduce carbon waste.</p>
            </div>
            <div className="photo-card green" />
            <div className="photo-card people" />
            <div className="mini-card">
              <h4>Community Hub</h4>
              <p>Connect students across disciplines for mutual benefit.</p>
            </div>
          </div>
        </section>
      </main>

      {/* WORKING FOOTER WITH REAL LINKS & MODAL TRIGGERS */}
      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Empowering students to build a more sustainable future.</p>
        </div>
        <div>
          <h4>Platform</h4>
          <Link href="/marketplace">Marketplace</Link>
          <Link href="/wishlist">Wishlist</Link>
          <Link href="/dashboard">Dashboard</Link>
          <Link href="/dashboard/orders">My Orders & Swaps</Link>
        </div>
        <div>
          <h4>Community & Impact</h4>
          <Link href="/marketplace?category=BOOKS">Book Exchange</Link>
          <Link href="/marketplace?category=ELECTRONICS">Tech Recycle</Link>
          <Link href="/profile">Student Profile</Link>
        </div>
        <div>
          <h4>Campus Sustainability</h4>
          <p style={{ fontSize: "0.85rem", color: "#666", lineHeight: "1.5" }}>
            ReuseHub circular network operates exclusively for verified campus members.
          </p>
          <div style={{ marginTop: "12px", fontSize: "0.85rem", color: "#888" }}>
            © {new Date().getFullYear()} ReuseHub. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  );
}
