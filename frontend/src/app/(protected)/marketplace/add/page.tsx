"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type ListingPayload = {
  title: string;
  description: string;
  category: string;
  condition: string;
  listingType: string;
  price: number | null;
  location: string;
  imageUrls: string[];
};

const categoryOptions = [
  { value: "ELECTRONICS", label: "Electronics" },
  { value: "FURNITURE", label: "Furniture" },
  { value: "BOOKS", label: "Books" },
  { value: "ACCESSORIES", label: "Accessories" },
  { value: "OTHER", label: "Other" }
];

const conditionOptions = [
  { value: "NEW", label: "Brand New" },
  { value: "LIKE_NEW", label: "Like New" },
  { value: "GOOD", label: "Good" },
  { value: "FAIR", label: "Fair" }
];

const pickupOptions = [
  "Weekday Mornings (8-10am)",
  "Weekday Afternoons (12-3pm)",
  "Weekday Evenings (5-8pm)",
  "Weekend Mornings (9-12am)",
  "Weekend Afternoons (1-4pm)"
];

export default function AddItemPage() {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("ELECTRONICS");
  const [condition, setCondition] = useState("NEW");
  const [selectedTypes, setSelectedTypes] = useState<string[]>(["SELL"]);
  const [price, setPrice] = useState("0");
  const [imageUrl, setImageUrl] = useState("");
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [imageError, setImageError] = useState<string | null>(null);
  const [isDragActive, setIsDragActive] = useState(false);
  const [location, setLocation] = useState("Main Library Plaza");
  const [pickup, setPickup] = useState(pickupOptions[0]);
  const [openToOffers, setOpenToOffers] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const canAddImage = imageUrls.length < 5 && imageUrl.trim().length > 0;
  const hasLocalImages = imageUrls.some((url) => url.startsWith("data:"));

  useEffect(() => {
    const stored = localStorage.getItem("reusehub.addItem.images");
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (Array.isArray(parsed)) {
        setImageUrls(parsed.filter((entry) => typeof entry === "string").slice(0, 5));
      }
    } catch {
      localStorage.removeItem("reusehub.addItem.images");
    }
  }, []);

  useEffect(() => {
    localStorage.setItem("reusehub.addItem.images", JSON.stringify(imageUrls));
  }, [imageUrls]);

  const toggleType = (type: string) => {
    setSelectedTypes((prev) => {
      if (prev.includes(type)) {
        if (prev.length === 1) return prev; // keep at least one selected
        return prev.filter((t) => t !== type);
      } else {
        return [...prev, type];
      }
    });
  };

  const impactLabel = useMemo(() => {
    if (selectedTypes.includes("DONATE")) return "+180 pts";
    if (selectedTypes.includes("SWAP")) return "+165 pts";
    return "+150 pts";
  }, [selectedTypes]);

  const carbonLabel = useMemo(() => {
    if (category === "FURNITURE") return "~6.2 kg";
    if (category === "ELECTRONICS") return "~4.2 kg";
    if (category === "BOOKS") return "~2.1 kg";
    return "~3.4 kg";
  }, [category]);

  const handleAddImage = () => {
    const trimmed = imageUrl.trim();
    if (!trimmed || imageUrls.length >= 5) return;
    setImageUrls((prev) => [...prev, trimmed]);
    setImageUrl("");
  };

  const addFiles = async (files: FileList | null) => {
    if (!files || imageUrls.length >= 5) return;
    setImageError(null);

    const validFiles = Array.from(files).filter((file) => {
      const isImage = /image\/(png|jpe?g|gif|webp)/.test(file.type);
      const isSizeOk = file.size <= 10 * 1024 * 1024;
      return isImage && isSizeOk;
    });

    if (!validFiles.length) {
      setImageError("Only JPG/PNG files up to 10MB are supported.");
      return;
    }

    const remainingSlots = 5 - imageUrls.length;
    const selectedFiles = validFiles.slice(0, remainingSlots);

    const nextUrls = await Promise.all(
      selectedFiles.map(
        (file) =>
          new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.onerror = () => reject(new Error("Unable to read file"));
            reader.readAsDataURL(file);
          })
      )
    );

    // Add local previews immediately
    setImageUrls((prev) => [...prev, ...nextUrls]);

    // Upload files to server in background and replace previews with hosted URLs
    try {
      const uploaded = await uploadToServer(selectedFiles as File[]);
      setImageUrls((prev) => {
        const updated = [...prev];
        const start = Math.max(0, updated.length - nextUrls.length);
        for (let i = 0; i < uploaded.length; i++) {
          const idx = start + i;
          if (idx >= 0 && idx < updated.length) {
            updated[idx] = uploaded[i];
          } else {
            updated.push(uploaded[i]);
          }
        }
        return updated;
      });
    } catch (err) {
      setImageError("Upload failed");
      showToast("Image upload failed. Please try again.", "error");
    }
  };

  const uploadToServer = async (files: File[]) => {
    const form = new FormData();
    files.forEach((f) => form.append("files", f));
    const apiBase = (process.env.NEXT_PUBLIC_API_URL as string) || "http://localhost:8080";
    const res = await fetch(`${apiBase}/api/uploads`, {
      method: "POST",
      body: form,
      credentials: "include"
    });
    if (!res.ok) throw new Error("Upload failed");
    const urls: string[] = await res.json();
    return urls;
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    void addFiles(event.target.files);
    event.target.value = "";
  };

  const handleDrop = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    setIsDragActive(false);
    void addFiles(event.dataTransfer.files);
  };

  const handleDragOver = (event: React.DragEvent<HTMLButtonElement>) => {
    event.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = () => {
    setIsDragActive(false);
  };

  const handleRemoveImage = (index: number) => {
    setImageUrls((prev) => prev.filter((_, idx) => idx !== index));
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      const isSellSelected = selectedTypes.includes("SELL");
      const primaryType = isSellSelected
        ? "SELL"
        : selectedTypes.includes("SWAP")
        ? "SWAP"
        : "DONATE";

      const numericPrice = isSellSelected ? Number(price || 0) : null;
      const typeNote =
        selectedTypes.length > 1
          ? `\n\n[Available for: ${selectedTypes.map((t) => (t === "SELL" ? "Sale" : t === "SWAP" ? "Swap" : "Free Donation")).join(", ")}]`
          : "";

      const payload: ListingPayload = {
        title: title.trim(),
        description: `${description.trim()}${typeNote}`,
        category,
        condition,
        listingType: primaryType,
        price: numericPrice,
        location,
        imageUrls: imageUrls.filter((url) => !url.startsWith("data:"))
      };

      await apiRequest("/listings", {
        method: "POST",
        body: JSON.stringify(payload)
      });

      setSuccess("Listing created successfully.");
      showToast("Listing created successfully.", "success");
      setTitle("");
      setDescription("");
      setPrice("0");
      setImageUrls([]);
      setImageUrl("");
      localStorage.removeItem("reusehub.addItem.images");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to create listing";
      setError(message);
      showToast(message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="home add-item-page">
      <main className="page add-item-shell">
        <section className="add-item-header">
          <span className="section-kicker">Circularity Hub</span>
          <h1>Add New Item</h1>
          <p>
            Give your pre-loved belongings a second life. List your items for
            sell, swap, or donation and earn impact points.
          </p>
        </section>

        <div className="add-item-grid">
          <div className="add-item-form">
            <section className="form-card">
              <div className="form-card-title">
                <span className="step-badge">1</span>
                <div>
                  <h3>Visual Gallery</h3>
                  <p>Max 5 photos</p>
                </div>
              </div>
              <div className="gallery-grid">
                <button
                  type="button"
                  className={`upload-card ${isDragActive ? "active" : ""}`}
                  onClick={() => fileInputRef.current?.click()}
                  onDrop={handleDrop}
                  onDragOver={handleDragOver}
                  onDragLeave={handleDragLeave}
                >
                  <div className="upload-icon">+</div>
                  <strong>Drag & Drop or Click</strong>
                  <span>PNG, JPG up to 10MB</span>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg"
                    multiple
                    onChange={handleFileChange}
                  />
                </button>
                {imageUrls.map((src, index) => (
                  <div key={`${src}-${index}`} className="preview-card">
                    <img src={src} alt={`Listing preview ${index + 1}`} />
                  </div>
                ))}
              </div>
              {imageError ? <div className="form-error">{imageError}</div> : null}
              <div className="image-url-row">
                <input
                  placeholder="Paste image URL to preview"
                  value={imageUrl}
                  onChange={(event) => setImageUrl(event.target.value)}
                />
                <button className="btn ghost" onClick={handleAddImage} disabled={!canAddImage}>
                  Add
                </button>
              </div>
              {imageUrls.length ? (
                <div className="image-pill-row">
                  {imageUrls.map((url, index) => (
                    <button key={url} className="image-pill" onClick={() => handleRemoveImage(index)}>
                      {`Image ${index + 1}`} x
                    </button>
                  ))}
                </div>
              ) : null}
              {hasLocalImages ? (
                <div className="form-note">Local previews are not uploaded yet. Add hosted URLs to save images.</div>
              ) : null}
            </section>

            <section className="form-card">
              <div className="form-card-title">
                <span className="step-badge">2</span>
                <div>
                  <h3>Essential Details</h3>
                  <p>Tell buyers why it matters.</p>
                </div>
              </div>
              <div className="field-row">
                <label>Item Title</label>
                <input
                  placeholder="e.g., Ergonomic Office Chair"
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </div>
              <div className="field-split">
                <div className="field-row">
                  <label>Category</label>
                  <select value={category} onChange={(event) => setCategory(event.target.value)}>
                    {categoryOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field-row">
                  <label>Condition</label>
                  <select value={condition} onChange={(event) => setCondition(event.target.value)}>
                    {conditionOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="field-row">
                <label>Description</label>
                <textarea
                  placeholder="Tell the community why this item is great."
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </div>
            </section>

            <section className="form-card">
              <div className="form-card-title">
                <span className="step-badge">3</span>
                <div>
                  <h3>Transaction Type</h3>
                  <p>Choose how you want to list.</p>
                </div>
              </div>
              <div className="type-grid">
                {["SELL", "SWAP", "DONATE"].map((type) => (
                  <button
                    key={type}
                    type="button"
                    className={`type-card ${selectedTypes.includes(type) ? "active" : ""}`}
                    onClick={() => toggleType(type)}
                  >
                    <strong>{type === "SELL" ? "Sell" : type === "SWAP" ? "Swap" : "Donate"}</strong>
                    <span>
                      {type === "SELL"
                        ? "Set a fixed price"
                        : type === "SWAP"
                        ? "Exchange for items"
                        : "Free for the community"}
                    </span>
                  </button>
                ))}
              </div>
              <div className="price-row">
                <div className="price-input">
                  <span>₹</span>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    placeholder="0"
                    value={price}
                    onChange={(event) => setPrice(event.target.value)}
                    disabled={!selectedTypes.includes("SELL")}
                  />
                </div>
                <label className={`offer-toggle ${openToOffers ? "active" : ""}`}>
                  <input
                    type="checkbox"
                    checked={openToOffers}
                    onChange={() => setOpenToOffers((prev) => !prev)}
                  />
                  Open to Offers
                </label>
              </div>
            </section>

            <section className="form-card">
              <div className="form-card-title">
                <span className="step-badge">4</span>
                <div>
                  <h3>Logistics</h3>
                  <p>Set your meetup preferences.</p>
                </div>
              </div>
              <div className="field-split">
                <div className="field-row">
                  <label>Campus Hub Location</label>
                  <select value={location} onChange={(event) => setLocation(event.target.value)}>
                    <option>Main Library Plaza</option>
                    <option>Student Union Atrium</option>
                    <option>North Gate Commons</option>
                  </select>
                </div>
                <div className="field-row">
                  <label>Preferred Pickup Time</label>
                  <select value={pickup} onChange={(event) => setPickup(event.target.value)}>
                    {pickupOptions.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </section>

            {error ? <div className="error">{error}</div> : null}
            {success ? <div className="success">{success}</div> : null}

            <div className="form-actions">
              <button className="btn ghost">Save Draft</button>
              <button className="btn primary" onClick={handleSubmit} disabled={submitting}>
                {submitting ? "Listing..." : "List Item Now"}
              </button>
            </div>
          </div>

          <aside className="add-item-sidebar">
            <div className="impact-preview">
              <h4>Impact Preview</h4>
              <p>
                By listing this item, you're contributing to the circular
                economy.
              </p>
              <div className="impact-metric">
                <span>Carbon Saved</span>
                <strong>{carbonLabel}</strong>
              </div>
              <div className="impact-metric">
                <span>Eco Points</span>
                <strong>{impactLabel}</strong>
              </div>
              <p className="impact-footnote">
                Your listing helps avoid the manufacturing of a new item, reducing
                global water use by ~40L on average.
              </p>
            </div>

            <div className="quality-card">
              <h4>Quick Quality Check</h4>
              <ul>
                <li>High-quality photos in natural light increase sales by 40%.</li>
                <li>Honest condition descriptions build community trust.</li>
                <li>Mention if original packaging or accessories are included.</li>
              </ul>
            </div>
          </aside>
        </div>
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
        </div>
        <div>
          <h4>Community</h4>
          <a href="#">Community Forum</a>
          <a href="#">Help Center</a>
        </div>
        <div>
          <h4>Impact</h4>
          <a href="#">Carbon Credits</a>
        </div>
      </footer>
    </div>
  );
}
