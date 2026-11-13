"use client";

import Link from "next/link";
import DashboardSidebar from "@/components/DashboardSidebar";
import { useEffect, useMemo, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiRequest, uploadRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

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
  handoffDeadlineAt: string | null;
  buyerHandoffConfirmedAt: string | null;
  sellerHandoffConfirmedAt: string | null;
  collateralAmount: number | null;
  collateralStatus: "NOT_APPLICABLE" | "HELD" | "RELEASED" | "REFUNDED";
  collateralLockedAt: string | null;
  collateralSettledAt: string | null;
  bundleId: string | null;
  bundlePosition: number | null;
  bundleSize: number | null;
};

type DisputeEvidence = {
  id: string;
  sequenceNumber: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  recordHash: string;
  integrityVerified: boolean;
  uploadedBy: string;
  uploadedAt: string;
};

type DisputeResponse = {
  id: string;
  orderId: string;
  listingTitle: string;
  reason: string;
  status: "OPEN" | "CLOSED";
  openedBy: string;
  openedAt: string;
  buyerCloseConfirmedAt: string | null;
  sellerCloseConfirmedAt: string | null;
  closedAt: string | null;
  evidence: DisputeEvidence[];
};

type OrderEvent = {
  action: string;
  order: OrderResponse;
};

type UserInfo = {
  id: number;
  name: string;
};

const tabs = ["All Orders", "Buying", "Selling", "Swapping"] as const;

type TabKey = (typeof tabs)[number];

export default function OrdersPage() {
  const { showToast } = useToast();
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabKey>("All Orders");
  const [userId, setUserId] = useState<number | null>(null);
  const [search, setSearch] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [handoffBusyId, setHandoffBusyId] = useState<string | null>(null);
  const [statusBusyId, setStatusBusyId] = useState<string | null>(null);
  const [disputes, setDisputes] = useState<Record<string, DisputeResponse>>({});
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

    apiRequest<OrderResponse[]>("/orders")
      .then((data) => {
        if (!mounted) return;
        setOrders(data);
        setError(null);
      })
      .catch((err) => {
        if (!mounted) return;
        setOrders([]);
        const message = err instanceof Error ? err.message : "Unable to load orders";
        setError(message);
        showToast(message, "error");
      });

    apiRequest<DisputeResponse[]>("/disputes")
      .then((data) => {
        if (!mounted) return;
        setDisputes(Object.fromEntries(data.map((dispute) => [dispute.orderId, dispute])));
      })
      .catch((err) => {
        if (!mounted) return;
        const message = err instanceof Error ? err.message : "Unable to load disputes";
        showToast(message, "error");
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
      client.subscribe(`/topic/orders/${userId}`, (frame) => {
        const payload = JSON.parse(frame.body) as OrderEvent;
        setOrders((prev) => {
          const existingIndex = prev.findIndex((order) => order.id === payload.order.id);
          if (existingIndex >= 0) {
            const updated = [...prev];
            updated[existingIndex] = payload.order;
            return updated;
          }
          return [payload.order, ...prev];
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

  const filteredOrders = useMemo(() => {
    let data = orders;
    if (activeTab === "Buying") {
      data = data.filter((order) => order.role === "BUYER");
    } else if (activeTab === "Selling") {
      data = data.filter((order) => order.role === "SELLER");
    } else if (activeTab === "Swapping") {
      data = data.filter((order) => order.type === "SWAP");
    }

    if (search.trim()) {
      const term = search.toLowerCase();
      data = data.filter((order) =>
        order.listingTitle.toLowerCase().includes(term) ||
        order.counterpartyName.toLowerCase().includes(term)
      );
    }

    return data;
  }, [orders, activeTab, search]);

    function DisputePanel({
      order,
      dispute,
      onChange
    }: {
      order: OrderResponse;
      dispute: DisputeResponse | null;
      onChange: (dispute: DisputeResponse) => void;
    }) {
      const { showToast } = useToast();
      const [reason, setReason] = useState("");
      const [file, setFile] = useState<File | null>(null);
      const [busy, setBusy] = useState(false);

      const openDispute = async () => {
        setBusy(true);
        try {
          const created = await apiRequest<DisputeResponse>(`/orders/${order.id}/disputes`, {
            method: "POST",
            body: JSON.stringify({ reason })
          });
          onChange(created);
          setReason("");
          showToast("Dispute opened. Both participants can now upload evidence.", "success");
        } catch (error) {
          showToast(error instanceof Error ? error.message : "Unable to open the dispute.", "error");
        } finally {
          setBusy(false);
        }
      };

      const uploadEvidence = async () => {
        if (!dispute || !file) return;
        setBusy(true);
        try {
          const formData = new FormData();
          formData.append("file", file);
          const updated = await uploadRequest<DisputeResponse>(`/disputes/${dispute.id}/evidence`, formData);
          onChange(updated);
          setFile(null);
          showToast("Evidence uploaded and integrity checks recorded.", "success");
        } catch (error) {
          showToast(error instanceof Error ? error.message : "Unable to upload evidence.", "error");
        } finally {
          setBusy(false);
        }
      };

      const confirmClose = async () => {
        if (!dispute) return;
        setBusy(true);
        try {
          const updated = await apiRequest<DisputeResponse>(`/disputes/${dispute.id}/close`, {
            method: "POST"
          });
          onChange(updated);
          showToast(
            updated.status === "CLOSED"
              ? "Both participants confirmed. The dispute is closed."
              : "Your closure confirmation is recorded; the other participant must also confirm.",
            "success"
          );
        } catch (error) {
          showToast(error instanceof Error ? error.message : "Unable to update the dispute.", "error");
        } finally {
          setBusy(false);
        }
      };

      const downloadEvidence = async (evidence: DisputeEvidence) => {
        if (!dispute || !evidence.integrityVerified) return;
        try {
          const response = await fetch(
            `${API_BASE}/disputes/${dispute.id}/evidence/${evidence.id}/download`,
            { credentials: "include" }
          );
          if (!response.ok) {
            const payload = await response.json().catch(() => ({}));
            throw new Error(payload.message || "Unable to download evidence.");
          }
          const objectUrl = URL.createObjectURL(await response.blob());
          const link = document.createElement("a");
          link.href = objectUrl;
          link.download = evidence.originalFilename;
          link.click();
          URL.revokeObjectURL(objectUrl);
        } catch (error) {
          showToast(error instanceof Error ? error.message : "Unable to download evidence.", "error");
        }
      };

      const canOpen = !dispute;
      const hasConfirmedClose = dispute
        ? Boolean(order.role === "BUYER" ? dispute.buyerCloseConfirmedAt : dispute.sellerCloseConfirmedAt)
        : false;

      return (
        <div style={{
          marginTop: "10px",
          padding: "12px",
          background: "#fff",
          border: "1px solid #e2e8f0",
          borderRadius: "10px"
        }}>
          <strong style={{ fontSize: "13px" }}>Participant dispute & evidence</strong>
          {canOpen ? (
            <div style={{ display: "grid", gap: "8px", marginTop: "8px" }}>
              <label style={{ fontSize: "12px", color: "var(--muted)" }} htmlFor={`dispute-reason-${order.id}`}>
                Describe the issue (up to 1,000 characters)
              </label>
              <textarea
                id={`dispute-reason-${order.id}`}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={1000}
                rows={3}
                placeholder="What happened?"
                style={{ width: "100%", resize: "vertical", font: "inherit", padding: "8px", borderRadius: "8px", border: "1px solid var(--line)" }}
              />
              <button
                className="btn ghost"
                type="button"
                onClick={openDispute}
                disabled={busy || !reason.trim()}
              >
                {busy ? "Opening..." : "Open dispute"}
              </button>
            </div>
          ) : (
            <div style={{ marginTop: "7px", fontSize: "12px" }}>
              <div>
                <strong>{dispute.status}</strong> · opened by {dispute.openedBy} on {new Date(dispute.openedAt).toLocaleString()}
              </div>
              <p style={{ margin: "5px 0 8px", color: "var(--muted)" }}>{dispute.reason}</p>
              {dispute.status === "OPEN" && (
                <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap", marginBottom: "8px" }}>
                  <input
                    type="file"
                    accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf"
                    onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                    aria-label="Choose dispute evidence"
                  />
                  <button
                    className="btn ghost"
                    type="button"
                    onClick={uploadEvidence}
                    disabled={busy || !file}
                  >
                    {busy ? "Working..." : "Upload evidence"}
                  </button>
                  <span style={{ color: "var(--muted)" }}>PNG, JPEG, or PDF · max 10 MB</span>
                </div>
              )}
              {dispute.evidence.length > 0 ? (
                <ul style={{ display: "grid", gap: "7px", paddingLeft: "18px", margin: "8px 0" }}>
                  {dispute.evidence.map((evidence) => (
                    <li key={evidence.id}>
                      <span>{evidence.originalFilename} · {evidence.uploadedBy} · {Math.ceil(evidence.sizeBytes / 1024)} KB</span>
                      <div style={{ overflowWrap: "anywhere", color: "var(--muted)", fontSize: "11px", margin: "3px 0" }}>
                        SHA-256: {evidence.sha256}
                      </div>
                      <button
                        className="btn ghost"
                        type="button"
                        onClick={() => downloadEvidence(evidence)}
                        disabled={!evidence.integrityVerified}
                      >
                        {evidence.integrityVerified ? "Integrity verified · download" : "Integrity check failed"}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p style={{ margin: "7px 0", color: "var(--muted)" }}>No evidence uploaded yet.</p>
              )}
              {dispute.status === "OPEN" ? (
                <div style={{ display: "grid", gap: "7px", marginTop: "8px" }}>
                  <div style={{ color: "var(--muted)" }}>
                    Buyer: {dispute.buyerCloseConfirmedAt ? "confirmed closure" : "not confirmed"} · Seller: {dispute.sellerCloseConfirmedAt ? "confirmed closure" : "not confirmed"}
                  </div>
                  <button
                    className="btn ghost"
                    type="button"
                    onClick={confirmClose}
                    disabled={busy || hasConfirmedClose}
                  >
                    {hasConfirmedClose ? "You confirmed closure" : "Confirm mutual closure"}
                  </button>
                </div>
              ) : (
                <p style={{ margin: "7px 0", color: "var(--muted)" }}>
                  Both participants confirmed closure. This participant workflow does not issue an admin or tribunal ruling.
                </p>
              )}
            </div>
          )}
        </div>
      );
    }

  const formatDate = (value: string) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleDateString([], { month: "short", day: "2-digit", year: "numeric" });
  };

  const formatPrice = (value: number | null) => {
    if (value === null || Number.isNaN(value)) return "";
    return `$${value.toFixed(2)}`;
  };

  const applyOrderUpdate = (previous: OrderResponse[], updated: OrderResponse, original: OrderResponse) =>
    previous.map((item) => {
      const isUpdatedOrder = item.id === updated.id;
      const isSameBundle = original.bundleId !== null && item.bundleId === original.bundleId;
      if (!isUpdatedOrder && !isSameBundle) return item;
      return {
        ...item,
        status: updated.status,
        handoffDeadlineAt: updated.handoffDeadlineAt,
        buyerHandoffConfirmedAt: updated.buyerHandoffConfirmedAt,
        sellerHandoffConfirmedAt: updated.sellerHandoffConfirmedAt,
        collateralStatus: updated.collateralStatus,
        collateralLockedAt: updated.collateralLockedAt,
        collateralSettledAt: updated.collateralSettledAt,
        ...(isUpdatedOrder ? updated : {})
      };
    });

  const updateOrderStatus = async (order: OrderResponse, status: "ACCEPTED" | "REJECTED") => {
    setStatusBusyId(order.id);
    try {
      const updated = await apiRequest<OrderResponse>(`/orders/${order.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status })
      });
      setOrders((prev) => applyOrderUpdate(prev, updated, order));
      showToast(
        status === "ACCEPTED"
          ? "Order accepted. The listing is reserved; coordinate the handoff in My Orders."
          : "Order rejected.",
        "success"
      );
    } catch (err) {
      showToast(err instanceof Error ? err.message : "Unable to update this order.", "error");
    } finally {
      setStatusBusyId(null);
    }
  };

  const confirmHandoff = async (order: OrderResponse) => {
    if (!navigator.geolocation) {
      showToast("This browser does not support location access.", "error");
      return;
    }

    setHandoffBusyId(order.id);
    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(
          resolve,
          (geoError) => {
            const message = geoError.code === geoError.PERMISSION_DENIED
              ? "Location permission was denied. Allow location access to confirm the handoff."
              : geoError.code === geoError.TIMEOUT
                ? "Could not get your location in time. Try again when location services are available."
                : "Unable to get your location. Check your device location settings and try again.";
            reject(new Error(message));
          },
          { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
        );
      });
      const updated = await apiRequest<OrderResponse>(`/orders/${order.id}/handoff-confirmation`, {
        method: "POST",
        body: JSON.stringify({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        })
      });
      setOrders((prev) => applyOrderUpdate(prev, updated, order));

      if (updated.status === "COMPLETED") {
        showToast("Handoff confirmed by both participants. The order is complete.", "success");
      } else if (updated.status === "NO_SHOW") {
        showToast("The handoff window expired. The listing has been returned to the marketplace.", "error");
      } else {
        showToast("Your location is confirmed. Waiting for the other participant to check in nearby.", "success");
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to confirm the handoff.";
      showToast(message, "error");
    } finally {
      setHandoffBusyId(null);
    }
  };

  return (
    <div className="home orders-page">
      <main className="page dashboard-shell orders-shell">
        <DashboardSidebar />

        <section className="orders-main">
          <div className="orders-header">
            <div>
              <h1>My Orders</h1>
              <p>Track your sustainable exchanges, purchases, and sales.</p>
            </div>
            <div className="search">
              <input
                placeholder="Search orders..."
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            <div className="savings-pill">
              <span>Carbon Savings</span>
              <strong>12.4 kg CO2</strong>
            </div>
          </div>

          <div className="orders-tabs">
            {tabs.map((tab) => (
              <button
                key={tab}
                className={`tab-pill ${activeTab === tab ? "active" : ""}`}
                onClick={() => setActiveTab(tab)}
              >
                {tab}
              </button>
            ))}
          </div>

          <div
            style={{
              margin: "16px 0",
              background: "#eff6ff",
              border: "1px solid #bfdbfe",
              borderRadius: "14px",
              padding: "12px 18px",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              fontSize: "12px",
              color: "#1e40af",
              flexWrap: "wrap",
              gap: "10px"
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span style={{ fontSize: "16px" }}>🔒</span>
              <span>
                <strong>Order protection:</strong> Listing reservations and handoff checks are enforced. Any collateral shown below is <strong>simulated tracking only</strong>; ReuseHub does not move or hold money.
              </span>
            </div>
            <span style={{ fontWeight: 700, background: "#dbeafe", color: "#1d4ed8", padding: "3px 10px", borderRadius: "999px", fontSize: "11px" }}>
              No payment provider connected
            </span>
          </div>

          {error ? <div className="error">{error}</div> : null}

          <div className="orders-list">
            {filteredOrders.length === 0 ? (
              <div className="empty-card">No orders yet.</div>
            ) : (
              filteredOrders.map((order) => (
                <article className="order-card" key={order.id}>
                  <div className="order-media">
                    <div
                      className="order-image"
                      style={
                        order.listingImageUrl
                          ? { backgroundImage: `url(${order.listingImageUrl})` }
                          : undefined
                      }
                    />
                  </div>
                  <div className="order-body">
                    <div className="order-tags" style={{ display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                      <span className="order-pill">{order.type}</span>
                      {order.bundleSize && order.bundleSize > 1 ? (
                        <span className="order-pill">
                          Bundle · item {order.bundlePosition}/{order.bundleSize}
                        </span>
                      ) : null}
                      <span className={`status-pill ${order.status.toLowerCase()}`}>
                        {order.status === "INVALIDATED"
                          ? "AUTO-INVALIDATED"
                          : order.status === "NO_SHOW"
                            ? "NO-SHOW — REQUEUED"
                            : order.status}
                      </span>
                      {order.type === "SWAP" && order.status === "ACCEPTED" && (
                        <span className="dual-lock-pill">
                          🔒 DUAL-LOCK ACTIVE
                        </span>
                      )}
                    </div>
                    <h3>{order.listingTitle}</h3>
                    {order.type === "SWAP" && order.offeredListingTitle && (
                      <div style={{ margin: "3px 0 6px", fontSize: "12px", color: "var(--accent)", fontWeight: 600 }}>
                        🔄 Offered Swap Item: {order.offeredListingTitle}
                      </div>
                    )}
                    <p>{order.message || `${order.counterpartyName} is coordinating.`}</p>
                    {order.status === "INVALIDATED" && (
                      <div style={{ marginTop: "6px", padding: "6px 10px", background: "#fef3c7", border: "1px solid #fde68a", borderRadius: "8px", fontSize: "12px", color: "#92400e" }}>
                        ⚠️ {order.message || "Auto-invalidated: Terms were modified by the seller to prevent disputes."}
                      </div>
                    )}
                    {order.status === "ACCEPTED" && (
                      <div style={{
                        marginTop: "12px",
                        padding: "12px",
                        background: "#f8fafc",
                        border: "1px solid #e2e8f0",
                        borderRadius: "10px"
                      }}>
                        <strong style={{ fontSize: "13px" }}>Geofenced handoff</strong>
                        <p style={{ margin: "4px 0 8px", fontSize: "12px", color: "var(--muted)" }}>
                          Both participants must check in within 100 meters of each other
                          {order.handoffDeadlineAt
                            ? ` before ${new Date(order.handoffDeadlineAt).toLocaleString()}`
                            : " within 24 hours of acceptance"}.
                          Location is used to verify this handoff only.
                        </p>
                        <div style={{ fontSize: "12px", marginBottom: "8px" }}>
                          <span>
                            {order.role === "BUYER" ? "You" : order.counterpartyName}:{" "}
                            {(order.role === "BUYER" ? order.buyerHandoffConfirmedAt : order.sellerHandoffConfirmedAt)
                              ? "checked in"
                              : "not checked in"}
                          </span>
                          {" · "}
                          <span>
                            {order.role === "BUYER" ? order.counterpartyName : "You"}:{" "}
                            {(order.role === "BUYER" ? order.sellerHandoffConfirmedAt : order.buyerHandoffConfirmedAt)
                              ? "checked in"
                              : "not checked in"}
                          </span>
                        </div>
                        <button
                          className="btn primary"
                          style={{ fontSize: "12px", padding: "7px 12px" }}
                          onClick={() => confirmHandoff(order)}
                          disabled={
                            handoffBusyId === order.id ||
                            Boolean(order.role === "BUYER"
                              ? order.buyerHandoffConfirmedAt
                              : order.sellerHandoffConfirmedAt)
                          }
                        >
                          {handoffBusyId === order.id
                            ? "Checking location..."
                            : (order.role === "BUYER"
                              ? order.buyerHandoffConfirmedAt
                              : order.sellerHandoffConfirmedAt)
                              ? "You checked in"
                              : "Confirm handoff location"}
                        </button>
                      </div>
                    )}
                    {order.status === "NO_SHOW" && (
                      <div style={{
                        marginTop: "8px",
                        padding: "8px 10px",
                        background: "#fff7ed",
                        border: "1px solid #fed7aa",
                        borderRadius: "8px",
                        fontSize: "12px",
                        color: "#9a3412"
                      }}>
                        The handoff window expired without both confirmations. The reserved listing(s) are active on the marketplace again.
                      </div>
                    )}
                    <div className="order-meta">
                      <span>{order.role === "BUYER" ? "Buying" : "Selling"}</span>
                      <span>{formatPrice(order.finalPrice ?? order.offeredPrice)}</span>
                    </div>
                    {order.type === "BUY" && order.collateralStatus !== "NOT_APPLICABLE" && (
                      <div style={{
                        marginTop: "10px",
                        padding: "10px 12px",
                        background: "#f0fdf4",
                        border: "1px solid #bbf7d0",
                        borderRadius: "10px",
                        fontSize: "12px"
                      }}>
                        <strong>Simulated collateral · {formatPrice(order.collateralAmount)}</strong>
                        <div style={{ marginTop: "3px", color: "var(--muted)" }}>
                          {order.collateralStatus === "HELD"
                            ? "Tracked as held until handoff is completed or the order is cancelled/no-show."
                            : order.collateralStatus === "RELEASED"
                              ? "Marked released after both participants confirmed the handoff."
                              : "Marked refunded after cancellation or a no-show."}
                          {" "}No funds were moved or held.
                        </div>
                      </div>
                    )}
                    {(order.status === "ACCEPTED" || order.status === "COMPLETED" || order.status === "NO_SHOW") && (
                      <DisputePanel
                        order={order}
                        dispute={disputes[order.id] ?? null}
                        onChange={(dispute) => {
                          setDisputes((previous) => ({ ...previous, [order.id]: dispute }));
                        }}
                      />
                    )}
                  </div>
                  <div className="order-actions">
                    <span className="order-date">{formatDate(order.createdAt)}</span>
                    <div className="order-buttons">
                      {order.role === "SELLER" && order.status === "REQUESTED" && (
                        <>
                          <button
                            className="btn primary"
                            type="button"
                            aria-label={`Accept order for ${order.listingTitle}`}
                            onClick={() => updateOrderStatus(order, "ACCEPTED")}
                            disabled={statusBusyId === order.id}
                          >
                            {statusBusyId === order.id ? "Updating..." : "Accept"}
                          </button>
                          <button
                            className="btn ghost"
                            type="button"
                            aria-label={`Reject order for ${order.listingTitle}`}
                            onClick={() => updateOrderStatus(order, "REJECTED")}
                            disabled={statusBusyId === order.id}
                          >
                            {statusBusyId === order.id ? "Updating..." : "Reject"}
                          </button>
                        </>
                      )}
                      <Link className="btn ghost" href={`/marketplace/${order.listingId}`}>
                        Details
                      </Link>
                      <Link className="btn" href={`/marketplace/${order.listingId}/chat`}>
                        Contact Partner
                      </Link>
                    </div>
                  </div>
                </article>
              ))
            )}
          </div>
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Circularity for a Greener Planet.</p>
        </div>
        <div>
          <h4>Platform</h4>
          <a href="#">Sustainability Report</a>
          <a href="#">Ethical Guidelines</a>
          <a href="#">Carbon Credits</a>
        </div>
        <div>
          <h4>Community</h4>
          <a href="#">Community Forum</a>
          <a href="#">Help Center</a>
          <a href="#">Success Stories</a>
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
