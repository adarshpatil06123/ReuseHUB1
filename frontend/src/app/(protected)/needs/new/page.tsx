"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type DragEvent, type FormEvent } from "react";
import { useNotifications } from "@/context/NotificationContext";
import { useToast } from "@/context/ToastContext";
import { apiRequest, uploadRequest } from "@/lib/api";

type NeedMatch = {
  id: string;
  listing: {
    id: number;
    title: string;
    category: string;
    price: number | null;
    imageUrls: string[];
  };
  matchedAt: string;
};

type NeedRequest = {
  id: string;
  keywords: string;
  category: string | null;
  listingType: string | null;
  maxPrice: number | null;
  details: string | null;
  minimumCondition: string | null;
  preferredLocation: string;
  neededBy: string;
  showOnHeatmap: boolean;
  acceptDonations: boolean;
  referenceImageUrl: string | null;
  active: boolean;
  createdAt: string;
  matches: NeedMatch[];
};

type DemandZone = {
  location: string;
  wishlistSaves: number;
  openRequests: number;
  needRequests: number;
  demandScore: number;
};

const categories = [
  { key: "tech", value: "ELECTRONICS", label: "STEM & Tech", description: "Calculators, sensors, boards", icon: "⌘" },
  { key: "dorm", value: "FURNITURE", label: "Dorm Essentials", description: "Kettles, fridges, lamps", icon: "▱" },
  { key: "books", value: "BOOKS", label: "Books & Notes", description: "Textbooks, guides", icon: "▤" },
  { key: "fashion", value: "ACCESSORIES", label: "Fashion & Shoes", description: "Bags, shoes, clothing", icon: "◈" },
  { key: "sports", value: "ACCESSORIES", label: "Sports & Gym", description: "Rackets, dumbbells, gear", icon: "◉" },
  { key: "other", value: "OTHER", label: "Other Items", description: "Stationery, cables", icon: "⬡" }
];

const locations = [
  "Hall 4 (Boys Quad)",
  "24/7 Library Hub",
  "Main Cafeteria",
  "North Campus",
  "South Campus"
];

const conditionOptions = [
  { value: "LIKE_NEW", label: "Used - Like New" },
  { value: "GOOD", label: "Used - Good" },
  { value: "FAIR", label: "Any Functional Condition" }
];

export default function NewNeedRequestPage() {
  const { showToast } = useToast();
  const { currentUser, notifications } = useNotifications();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const lastMatchNotification = useRef<string | null>(null);
  const [categoryKey, setCategoryKey] = useState("tech");
  const [keywords, setKeywords] = useState("");
  const [details, setDetails] = useState("");
  const [acceptableConditions, setAcceptableConditions] = useState<string[]>(["LIKE_NEW"]);
  const [maxPrice, setMaxPrice] = useState("");
  const [acceptDonations, setAcceptDonations] = useState(false);
  const [location, setLocation] = useState(locations[0]);
  const [neededBy, setNeededBy] = useState("TODAY");
  const [showOnHeatmap, setShowOnHeatmap] = useState(true);
  const [referenceImageUrl, setReferenceImageUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [requests, setRequests] = useState<NeedRequest[]>([]);
  const [latestRequest, setLatestRequest] = useState<NeedRequest | null>(null);
  const [zones, setZones] = useState<DemandZone[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const refreshRequests = useCallback(async () => {
    try {
      const data = await apiRequest<NeedRequest[]>("/needs");
      setRequests(data);
      setLatestRequest((current) => current ? data.find((request) => request.id === current.id) ?? current : null);
      setLoadError(null);
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Unable to refresh your need requests.");
    }
  }, []);

  const refreshRadar = useCallback(async () => {
    try {
      const data = await apiRequest<DemandZone[]>("/demand/heatmap");
      setZones(data);
      setLastUpdated(new Date());
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Unable to refresh the campus radar.");
    }
  }, []);

  const refreshLiveData = useCallback(async () => {
    await Promise.all([refreshRequests(), refreshRadar()]);
    setLoading(false);
  }, [refreshRadar, refreshRequests]);

  useEffect(() => {
    void refreshLiveData();
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === "visible") void refreshLiveData();
    }, 15_000);
    return () => window.clearInterval(intervalId);
  }, [refreshLiveData]);

  useEffect(() => {
    const latest = notifications[0];
    if (latest?.notificationType === "NEED_MATCH" && latest.notificationId !== lastMatchNotification.current) {
      lastMatchNotification.current = latest.notificationId;
      void refreshRequests();
    }
  }, [notifications, refreshRequests]);

  const handleReferenceUpload = async (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      showToast("Choose a PNG, JPG, or WebP image.", "error");
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      showToast("The reference image must be 8 MB or smaller.", "error");
      return;
    }

    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("files", file);
      const uploaded = await uploadRequest<string[]>("/api/uploads", formData);
      if (!uploaded[0]) throw new Error("The server did not return an image URL.");
      setReferenceImageUrl(uploaded[0]);
      showToast("Reference image uploaded.", "success");
    } catch (error) {
      showToast(error instanceof Error ? error.message : "Unable to upload the reference image.", "error");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleDrop = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    void handleReferenceUpload(event.dataTransfer.files);
  };

  const conditionPriority: Record<string, number> = {
    LIKE_NEW: 1,
    GOOD: 2,
    FAIR: 3
  };

  const minimumCondition = acceptableConditions.length
    ? acceptableConditions.reduce((current, value) => (
        conditionPriority[value] > conditionPriority[current] ? value : current
      ))
    : "LIKE_NEW";

  const toggleAcceptableCondition = (value: string) => {
    setAcceptableConditions((current) => {
      if (current.includes(value)) {
        return current.filter((item) => item !== value);
      }
      return [...current, value];
    });
  };

  const submitRequest = async (active: boolean, event?: FormEvent<HTMLFormElement>) => {
    event?.preventDefault();
    if (!keywords.trim()) {
      showToast("Add the item name or model you need.", "error");
      return;
    }
    if (acceptableConditions.length === 0) {
      showToast("Select at least one acceptable condition.", "error");
      return;
    }
    if (uploading) {
      showToast("Wait for the reference image upload to finish.", "error");
      return;
    }

    setSaving(true);
    try {
      const request = await apiRequest<NeedRequest>("/needs", {
        method: "POST",
        body: JSON.stringify({
          keywords: keywords.trim(),
          category: selectedCategory.value,
          listingType: null,
          maxPrice: maxPrice ? Number(maxPrice) : null,
          details: details.trim() || null,
          minimumCondition,
          preferredLocation: location,
          neededBy,
          showOnHeatmap,
          acceptDonations,
          referenceImageUrl,
          active
        })
      });
      setLatestRequest(request);
      setRequests((previous) => [request, ...previous.filter((item) => item.id !== request.id)]);
      await refreshRadar();
      showToast(active ? "Your request is live and matching listings now." : "Draft saved. Publish it when you are ready.", "success");
    } catch (error) {
      showToast(error instanceof Error ? error.message : "Unable to save the need request.", "error");
    } finally {
      setSaving(false);
    }
  };

  const nearbyNeedCount = zones.find((zone) => zone.location === location)?.needRequests ?? 0;
  const submittedMatches = latestRequest?.matches ?? [];
  const selectedCategory = categories.find((item) => item.key === categoryKey) ?? categories[0];

  return (
    <main className="need-request-page">
      <div className="need-request-shell">
        <nav className="need-request-breadcrumb" aria-label="Breadcrumb">
          <Link href="/">Home</Link><span>/</span><Link href="/">Live Demand Radar</Link><span>/</span><strong>Post a Request</strong>
        </nav>

        <header className="need-request-hero">
          <div>
            <div className="need-request-live-pill"><span /> Live Campus Mesh</div>
            <h1>Broadcast Your Demand to Campus</h1>
            <p>Can&apos;t find what you need? Post a request. Students nearby can see it on the live radar and connect with you.</p>
          </div>
          <aside className="need-response-speed">
            <span className="need-speed-icon">ϟ</span>
            <div><small>DEMAND SPEED</small><strong>New matches appear <b>as soon as they&apos;re posted</b></strong></div>
          </aside>
        </header>

        {loadError && <p className="need-request-error" role="status">{loadError} Live data will retry automatically.</p>}

        <div className="need-request-columns">
          <form className="need-request-form" onSubmit={(event) => void submitRequest(true, event)}>
            <section className="need-form-card">
              <div className="need-form-step"><h2><span>♧</span> 1. Select item category</h2><small>Step 1 of 5</small></div>
              <div className="need-category-grid">
                {categories.map((item) => (
                  <button
                    className={`need-category-option ${categoryKey === item.key ? "selected" : ""}`}
                    key={item.key}
                    type="button"
                    aria-pressed={categoryKey === item.key}
                    onClick={() => setCategoryKey(item.key)}
                  >
                    <span className="need-category-icon">{item.icon}</span>
                    <span><strong>{item.label}</strong><small>{item.description}</small></span>
                  </button>
                ))}
              </div>
            </section>

            <section className="need-form-card">
              <div className="need-form-step"><h2><span>☷</span> 2. What are you looking for?</h2><small className="need-required">* Required</small></div>
              <label className="need-field-label" htmlFor="need-keywords">Item name or model</label>
              <div className="need-input-with-count">
                <input id="need-keywords" required maxLength={120} value={keywords} onChange={(event) => setKeywords(event.target.value)} placeholder="e.g. Casio FX-991EX ClassWiz Calculator" />
                <span>{keywords.length}/120</span>
              </div>
              <small className="need-field-help">Be specific with model numbers or editions for faster student responses.</small>

              <label className="need-field-label" htmlFor="need-details">Specific requirements &amp; notes</label>
              <textarea id="need-details" maxLength={2000} value={details} onChange={(event) => setDetails(event.target.value)} placeholder="Share the details that matter: model, size, features, when you need it…" rows={4} />

              <div className="need-condition-field">
                <span className="need-field-label">Acceptable condition</span>
                <div className="need-condition-options">
                  {conditionOptions.map((option) => {
                    const selected = acceptableConditions.includes(option.value);
                    return (
                      <button
                        className={selected ? "selected" : ""}
                        type="button"
                        key={option.value}
                        aria-pressed={selected}
                        onClick={() => toggleAcceptableCondition(option.value)}
                      >
                        <span>{selected ? "✓" : "○"}</span>{option.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </section>

            <section className="need-form-card">
              <div className="need-form-step"><h2><span>▣</span> 3. Maximum willing to pay</h2></div>
              <div className="need-price-row">
                <label className="need-price-input"><span>₹</span><input aria-label="Maximum willing to pay in rupees" type="number" min="0.01" step="0.01" value={maxPrice} onChange={(event) => setMaxPrice(event.target.value)} placeholder="1200" /></label>
                <label className="need-donation-toggle">
                  <input type="checkbox" checked={acceptDonations} onChange={(event) => setAcceptDonations(event.target.checked)} />
                  <span><strong>Accepting free donations / borrow</strong><small>Include free listings in my matches</small></span>
                </label>
              </div>
              <div className="need-price-tip"><span>💡</span><p><strong>Campus fair range: ₹700 – ₹1,450</strong> based on recent transfers. Clear, realistic pricing can help you connect sooner.</p></div>
            </section>

            <section className="need-form-card">
              <div className="need-form-step"><h2><span>♧</span> 4. Handoff zone &amp; timeline</h2></div>
              <div className="need-handoff-row">
                <label><span className="need-field-label">Preferred meetup zone / hostel</span>
                  <select value={location} onChange={(event) => setLocation(event.target.value)}>
                    {locations.map((item) => <option key={item} value={item}>{item}</option>)}
                  </select>
                </label>
                <fieldset className="need-timeline">
                  <legend>When is it needed?</legend>
                  {[
                    { value: "TODAY", label: "⚡ Today" },
                    { value: "THIS_WEEK", label: "This Week" },
                    { value: "ANYTIME", label: "Anytime" }
                  ].map((item) => (
                    <button className={neededBy === item.value ? "selected" : ""} type="button" key={item.value} onClick={() => setNeededBy(item.value)}>{item.label}</button>
                  ))}
                </fieldset>
              </div>
              <label className="need-map-toggle">
                <input type="checkbox" checked={showOnHeatmap} onChange={(event) => setShowOnHeatmap(event.target.checked)} />
                <span><strong>Show approximate pin on Campus Demand Heat Map</strong><small>Only your general zone is shared. Your room number and private contact details stay hidden until you approve an offer.</small></span>
              </label>
            </section>

            <section className="need-form-card">
              <div className="need-form-step"><h2><span>▧</span> 5. Reference photo or spec sheet (optional)</h2></div>
              <input ref={fileInputRef} className="need-file-input" type="file" accept="image/png,image/jpeg,image/webp" onChange={(event) => void handleReferenceUpload(event.target.files)} />
              {referenceImageUrl ? (
                <div className="need-uploaded-image">
                  <img src={referenceImageUrl} alt="Uploaded item reference" />
                  <span>Reference image attached</span>
                  <button type="button" onClick={() => setReferenceImageUrl(null)}>Remove</button>
                </div>
              ) : (
                <button className="need-upload-drop" type="button" onClick={() => fileInputRef.current?.click()} onDragOver={(event) => event.preventDefault()} onDrop={handleDrop} disabled={uploading}>
                  <span className="need-upload-icon">{uploading ? "…" : "☁"}</span>
                  <span><strong>{uploading ? "Uploading image…" : "Click to upload or drag and drop"}</strong><small>PNG, JPG, or WebP up to 8 MB. Helps students identify the exact item.</small></span>
                </button>
              )}
            </section>

            <div className="need-form-actions">
              <button className="need-draft-button" type="button" disabled={saving || uploading || !keywords.trim()} onClick={() => void submitRequest(false)}>
                {saving ? "Saving…" : "Save as Draft"}
              </button>
              <button className="need-broadcast-button" type="submit" disabled={saving || uploading}>
                {saving ? "Broadcasting…" : "🚀 Broadcast Request to Live Heat Map"}
              </button>
            </div>
          </form>

          <aside className="need-request-aside">
            <section className="need-preview-heading"><span>◉</span><strong>REAL-TIME RADAR PREVIEW</strong><span className={`need-sync-status ${loading ? "syncing" : ""}`}>{loading ? "Syncing…" : "Sync Active"}</span></section>
            <article className="need-request-preview">
              <div className="need-preview-user">
                <span className="need-avatar">{currentUser?.name?.slice(0, 1).toUpperCase() ?? "Y"}</span>
                <span><strong>You{currentUser?.name ? ` (${currentUser.name})` : ""}</strong><small>Verified Student · {location}</small></span>
                <b>{neededBy === "TODAY" ? "Urgent (<24H)" : neededBy === "THIS_WEEK" ? "This week" : "Anytime"}</b>
              </div>
              <div className="need-preview-meta"><span>{selectedCategory.label.toUpperCase()}</span><strong>{acceptDonations ? "Free listings included" : "Paid & swap listings"}</strong></div>
              <h3>{keywords || "Your requested item will appear here"}</h3>
              <p>{details || "Add model details or requirements to help nearby students find the right item."}</p>
              {referenceImageUrl && <img className="need-preview-photo" src={referenceImageUrl} alt="Requested item reference" />}
              <div className="need-preview-footer"><span>WILLING TO PAY</span><strong>{maxPrice ? `₹${Number(maxPrice).toLocaleString("en-IN")}` : "Open to offers"}</strong></div>
              {latestRequest && <div className="need-preview-match-status" role="status">
                {latestRequest.active
                  ? `${submittedMatches.length} matching listing${submittedMatches.length === 1 ? "" : "s"} found`
                  : "Draft saved — not visible to other students yet"}
              </div>}
            </article>

            <section className="need-live-radar">
              <div className="need-radar-title"><span>⌁</span><strong>Live Demand Radar</strong><small>{nearbyNeedCount} in this zone</small></div>
              <div className="need-radar-insight"><span>▣</span><p><strong>{nearbyNeedCount} live need request{nearbyNeedCount === 1 ? "" : "s"} nearby</strong><small>Only aggregate demand is shown on the campus heat map; request details stay private.</small></p></div>
              {latestRequest?.active && submittedMatches.length > 0 ? (
                <div className="need-match-list">
                  <strong>New matches for your request</strong>
                  {submittedMatches.slice(0, 3).map((match) => (
                    <Link key={match.id} href={`/marketplace/${match.listing.id}`}>
                      <span>{match.listing.title}</span><b>{match.listing.price === null ? "Free / swap" : `₹${match.listing.price}`}</b>
                    </Link>
                  ))}
                </div>
              ) : (
                <div className="need-radar-recent"><span>◷</span><p><strong>{latestRequest?.active ? "Searching active campus listings" : "Matches appear as listings are posted"}</strong><small>Matching updates are delivered live and refreshed every 15 seconds.</small></p></div>
              )}
              <div className="need-zero-waste">♧ &nbsp; Zero Packaging Waste <strong>100% in-person handoff</strong></div>
              <small className="need-radar-updated" aria-live="polite">{lastUpdated ? `Last synced ${lastUpdated.toLocaleTimeString()}` : "Loading campus demand…"}</small>
            </section>

            <section className="need-safe-zone"><span>♧</span><p><strong>Campus Safe Zone Guarantee</strong><small>Meetups take place on university premises. For late-evening handoffs, use the 24/7 Library Hub or Main Cafeteria entrance.</small></p></section>
            {requests.length > 0 && <Link className="need-manage-link" href="/wishlist">Manage my {requests.length} need request{requests.length === 1 ? "" : "s"} →</Link>}
          </aside>
        </div>
      </div>
    </main>
  );
}
