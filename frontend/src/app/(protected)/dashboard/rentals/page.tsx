"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import DashboardSidebar from "@/components/DashboardSidebar";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type RentalStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED"
  | "READY_FOR_HANDOVER"
  | "ACTIVE"
  | "RETURN_REQUESTED"
  | "COMPLETED"
  | "DISPUTED"
  | "EXPIRED";

type RentalHistory = {
  previousStatus: RentalStatus | null;
  newStatus: RentalStatus;
  changedByUserId: number | null;
  reason: string | null;
  changedAt: string;
};

type Rental = {
  id: string;
  listingId: number;
  listingTitle: string;
  listingImageUrl: string | null;
  location: string;
  renterId: number;
  renterName: string;
  ownerId: number;
  ownerName: string;
  role: "RENTER" | "OWNER";
  startDate: string;
  endDate: string;
  rentalDays: number;
  dailyRate: number;
  rentalAmount: number;
  securityDeposit: number;
  totalEstimate: number;
  message: string | null;
  status: RentalStatus;
  statusReason: string | null;
  expiresAt: string;
  ownerHandoverConfirmedAt: string | null;
  renterHandoverConfirmedAt: string | null;
  returnRequestedAt: string | null;
  returnedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  statusHistory: RentalHistory[];
};

type RentalPage = {
  content: Rental[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

const tabs = [
  { value: "ALL", label: "All" },
  { value: "INCOMING", label: "Incoming" },
  { value: "MY_RENTALS", label: "My requests" },
  { value: "UPCOMING", label: "Upcoming" },
  { value: "ACTIVE", label: "Active" },
  { value: "HISTORY", label: "History" }
] as const;

type Tab = (typeof tabs)[number]["value"];

const statusLabels: Record<RentalStatus, string> = {
  PENDING: "Pending owner response",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  READY_FOR_HANDOVER: "Ready for handover",
  ACTIVE: "Active rental",
  RETURN_REQUESTED: "Return requested",
  COMPLETED: "Completed",
  DISPUTED: "Return issue reported",
  EXPIRED: "Expired"
};

const formatMoney = (value: number) => `₹${Number(value || 0).toLocaleString("en-IN")}`;
const today = () => new Date().toLocaleDateString("en-CA");

export default function RentalsPage() {
  const { showToast } = useToast();
  const [rentals, setRentals] = useState<Rental[]>([]);
  const [activeTab, setActiveTab] = useState<Tab>("ALL");
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [disputeId, setDisputeId] = useState<string | null>(null);
  const [disputeReason, setDisputeReason] = useState("");

  const loadRentals = useCallback(async () => {
    try {
      const data = await apiRequest<RentalPage>(`/rentals?page=${page}&size=20`);
      setRentals(data.content);
      setTotalPages(data.totalPages);
      setError(null);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Unable to load rentals.";
      setError(message);
      showToast(message, "error");
    } finally {
      setLoading(false);
    }
  }, [page, showToast]);

  useEffect(() => {
    void loadRentals();
    const intervalId = window.setInterval(() => void loadRentals(), 30_000);
    return () => window.clearInterval(intervalId);
  }, [loadRentals]);

  const visibleRentals = useMemo(() => {
    if (activeTab === "INCOMING") return rentals.filter((rental) => rental.role === "OWNER");
    if (activeTab === "MY_RENTALS") return rentals.filter((rental) => rental.role === "RENTER");
    if (activeTab === "UPCOMING") {
      return rentals.filter((rental) => ["APPROVED", "READY_FOR_HANDOVER"].includes(rental.status));
    }
    if (activeTab === "ACTIVE") {
      return rentals.filter((rental) =>
        ["ACTIVE", "RETURN_REQUESTED", "DISPUTED"].includes(rental.status)
      );
    }
    if (activeTab === "HISTORY") {
      return rentals.filter((rental) =>
        ["REJECTED", "CANCELLED", "COMPLETED", "EXPIRED"].includes(rental.status)
      );
    }
    return rentals;
  }, [rentals, activeTab]);

  const applyAction = async (
    rental: Rental,
    action: "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED"
  ) => {
    setBusyId(rental.id);
    try {
      const updated = await apiRequest<Rental>(`/rentals/${rental.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: action })
      });
      setRentals((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      showToast(action === "APPROVED" ? "Rental request approved."
        : action === "REJECTED" ? "Rental request rejected."
        : action === "COMPLETED" ? "Return confirmed. The rental is complete."
        : "Rental cancelled.", "success");
    } catch (actionError) {
      showToast(actionError instanceof Error ? actionError.message : "Unable to update rental.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const postAction = async (
    rental: Rental,
    endpoint: "handover" | "return"
  ) => {
    setBusyId(rental.id);
    try {
      const updated = await apiRequest<Rental>(`/rentals/${rental.id}/${endpoint}`, { method: "POST" });
      setRentals((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      showToast(endpoint === "handover" ? "Handover confirmation recorded." : "Return request sent to the owner.", "success");
    } catch (actionError) {
      showToast(actionError instanceof Error ? actionError.message : "Unable to update rental.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const submitDispute = async (rental: Rental) => {
    setBusyId(rental.id);
    try {
      const updated = await apiRequest<Rental>(`/rentals/${rental.id}/dispute`, {
        method: "POST",
        body: JSON.stringify({ reason: disputeReason })
      });
      setRentals((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      setDisputeId(null);
      setDisputeReason("");
      showToast("Return issue reported to the other participant.", "success");
    } catch (actionError) {
      showToast(actionError instanceof Error ? actionError.message : "Unable to report this issue.", "error");
    } finally {
      setBusyId(null);
    }
  };

  const availableActions = (rental: Rental) => {
    const actions = [];
    if (rental.role === "OWNER" && rental.status === "PENDING") {
      actions.push(
        <button key="approve" className="btn primary" type="button" onClick={() => void applyAction(rental, "APPROVED")} disabled={busyId === rental.id}>Approve</button>,
        <button key="reject" className="btn ghost" type="button" onClick={() => void applyAction(rental, "REJECTED")} disabled={busyId === rental.id}>Reject</button>
      );
    }
    if (rental.role === "RENTER" && rental.status === "PENDING") {
      actions.push(<button key="cancel" className="btn ghost" type="button" onClick={() => void applyAction(rental, "CANCELLED")} disabled={busyId === rental.id}>Cancel request</button>);
    }
    if ((rental.status === "APPROVED" || rental.status === "READY_FOR_HANDOVER")
      && today() < rental.startDate) {
      actions.push(<button key="cancel-approved" className="btn ghost" type="button" onClick={() => void applyAction(rental, "CANCELLED")} disabled={busyId === rental.id}>Cancel rental</button>);
    }
    if (rental.status === "APPROVED" || rental.status === "READY_FOR_HANDOVER") {
      const alreadyConfirmed = rental.role === "OWNER"
        ? Boolean(rental.ownerHandoverConfirmedAt)
        : Boolean(rental.renterHandoverConfirmedAt);
      if (!alreadyConfirmed) {
        actions.push(<button key="handover" className="btn primary" type="button" onClick={() => void postAction(rental, "handover")} disabled={busyId === rental.id}>Confirm handover</button>);
      }
    }
    if (rental.role === "RENTER" && rental.status === "ACTIVE") {
      actions.push(<button key="return" className="btn primary" type="button" onClick={() => void postAction(rental, "return")} disabled={busyId === rental.id}>Request return</button>);
    }
    if (rental.role === "OWNER" && rental.status === "RETURN_REQUESTED") {
      actions.push(<button key="complete" className="btn primary" type="button" onClick={() => void applyAction(rental, "COMPLETED")} disabled={busyId === rental.id}>Confirm returned</button>);
    }
    if (rental.status === "ACTIVE" || rental.status === "RETURN_REQUESTED") {
      actions.push(<button key="dispute" className="btn ghost" type="button" onClick={() => setDisputeId(rental.id)}>Report an issue</button>);
    }
    return actions;
  };

  return (
    <div className="home dashboard-page">
      <main className="page dashboard-shell">
        <DashboardSidebar />
        <section className="dashboard-main">
          <div className="section-head">
            <div>
              <span className="section-kicker">Rentals</span>
              <h1>Rental activity</h1>
              <p>Manage requests, handovers, returns, and rental history.</p>
            </div>
            <Link className="btn primary" href="/marketplace?type=RENT">Browse rentals</Link>
          </div>

          <div className="pill-row" role="tablist" aria-label="Filter rental activity">
            {tabs.map((tab) => (
              <button key={tab.value} type="button" role="tab" aria-selected={activeTab === tab.value}
                className={`pill-btn ${activeTab === tab.value ? "active" : ""}`}
                onClick={() => setActiveTab(tab.value)}>
                {tab.label}
              </button>
            ))}
          </div>

          {loading ? <p role="status">Loading your rentals…</p> : null}
          {!loading && error ? <div className="error" role="alert">{error}</div> : null}
          {!loading && !error && visibleRentals.length === 0 ? (
            <div className="empty-state" style={{ padding: "32px", textAlign: "center" }}>
              <h2>No rentals here yet</h2>
              <p>Rental requests and activity will appear here.</p>
              <Link className="btn primary" href="/marketplace?type=RENT">Explore rental listings</Link>
            </div>
          ) : null}

          <div style={{ display: "grid", gap: "16px", marginTop: "20px" }}>
            {visibleRentals.map((rental) => {
              const otherUserId = rental.role === "OWNER" ? rental.renterId : rental.ownerId;
              const actions = availableActions(rental);
              return (
                <article key={rental.id} className="form-card rental-dashboard-card">
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "16px", flexWrap: "wrap" }}>
                    <div style={{ display: "flex", gap: "14px", minWidth: 0 }}>
                      {rental.listingImageUrl ? (
                        <img src={rental.listingImageUrl} alt="" width={88} height={88} style={{ borderRadius: "12px", objectFit: "cover" }} />
                      ) : null}
                      <div>
                        <Link href={`/marketplace/${rental.listingId}`}><h2 style={{ margin: "0 0 6px" }}>{rental.listingTitle}</h2></Link>
                        <span className={`rental-status rental-status-${rental.status.toLowerCase()}`}>{statusLabels[rental.status]}</span>
                        <p style={{ margin: "8px 0 0" }}>
                          {rental.role === "OWNER" ? "Renter" : "Owner"}: {rental.role === "OWNER" ? rental.renterName : rental.ownerName}
                          {" · "}{rental.location || "Campus handover"}
                        </p>
                      </div>
                    </div>
                    <div>
                      <Link className="btn ghost" href={`/marketplace/${rental.listingId}/chat?userId=${otherUserId}`}>Contact participant</Link>
                    </div>
                  </div>

                  <div className="rental-summary-grid">
                    <div><span>Requested dates</span><strong>{rental.startDate} – {rental.endDate} inclusive</strong></div>
                    <div><span>Duration</span><strong>{rental.rentalDays} day(s)</strong></div>
                    <div><span>Rental estimate</span><strong>{formatMoney(rental.rentalAmount)}</strong></div>
                    <div><span>Refundable deposit estimate</span><strong>{formatMoney(rental.securityDeposit)}</strong></div>
                    <div><span>Total estimate</span><strong>{formatMoney(rental.totalEstimate)}</strong></div>
                  </div>
                  <p className="form-note">Estimates only. ReuseHub does not process payments or deposits.</p>
                  {rental.message ? <p><strong>Message:</strong> {rental.message}</p> : null}
                  {rental.statusReason ? <p><strong>Update:</strong> {rental.statusReason}</p> : null}
                  {rental.status === "PENDING" ? (
                    <p className="form-note">Request expires {new Date(rental.expiresAt).toLocaleString()} if not answered.</p>
                  ) : null}
                  {rental.ownerHandoverConfirmedAt || rental.renterHandoverConfirmedAt ? (
                    <p className="form-note">
                      Handover: Owner {rental.ownerHandoverConfirmedAt ? "confirmed" : "pending"} ·
                      Renter {rental.renterHandoverConfirmedAt ? "acknowledged" : "pending"}
                    </p>
                  ) : null}
                  {rental.returnRequestedAt ? (
                    <p className="form-note">
                      Return requested {new Date(rental.returnRequestedAt).toLocaleString()}
                      {rental.returnedAt ? ` · Confirmed ${new Date(rental.returnedAt).toLocaleString()}` : ""}
                    </p>
                  ) : null}

                  {rental.statusHistory.length > 0 ? (
                    <details className="rental-timeline">
                      <summary>View status timeline ({rental.statusHistory.length})</summary>
                      <ol>
                        {rental.statusHistory.map((entry, index) => (
                          <li key={`${entry.newStatus}-${entry.changedAt}-${index}`}>
                            <strong>{entry.previousStatus ? `${statusLabels[entry.previousStatus]} → ` : ""}{statusLabels[entry.newStatus]}</strong>
                            <span>
                              {" · "}
                              {entry.changedByUserId === null
                                ? "ReuseHub"
                                : entry.changedByUserId === rental.ownerId
                                  ? rental.ownerName
                                  : rental.renterName}
                            </span>
                            <time dateTime={entry.changedAt}> · {new Date(entry.changedAt).toLocaleString()}</time>
                            {entry.reason ? <p>{entry.reason}</p> : null}
                          </li>
                        ))}
                      </ol>
                    </details>
                  ) : null}

                  {rental.status === "DISPUTED" ? (
                    <p className="error" role="status">
                      This rental has a return issue reported. Contact the other participant to resolve it; ReuseHub does not process a deposit or provide a dispute tribunal.
                    </p>
                  ) : null}

                  {disputeId === rental.id ? (
                    <div className="rental-dispute-form">
                      <label htmlFor={`dispute-${rental.id}`}>Describe the return issue</label>
                      <textarea id={`dispute-${rental.id}`} maxLength={1000} value={disputeReason}
                        onChange={(event) => setDisputeReason(event.target.value)} />
                      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }}>
                        <button className="btn ghost" type="button" onClick={() => { setDisputeId(null); setDisputeReason(""); }}>Cancel</button>
                        <button className="btn primary" type="button" disabled={!disputeReason.trim() || busyId === rental.id}
                          onClick={() => void submitDispute(rental)}>Report issue</button>
                      </div>
                    </div>
                  ) : null}

                  {actions.length > 0 ? (
                    <div className="rental-actions">{actions}</div>
                  ) : null}
                </article>
              );
            })}
          </div>
          {!loading && totalPages > 1 ? (
            <div className="rental-actions" aria-label="Rental history pages">
              <button className="btn ghost" type="button" disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
              <span>Page {page + 1} of {totalPages}</span>
              <button className="btn ghost" type="button" disabled={page + 1 >= totalPages}
                onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}>Next</button>
            </div>
          ) : null}
        </section>
      </main>
    </div>
  );
}
