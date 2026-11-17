"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

type UserProfile = {
  id: number;
  email: string;
  name: string;
  username: string | null;
  profileImageUrl: string | null;
  department: string | null;
  college: string | null;
  phoneNumber: string | null;
  location: string | null;
};

const fallbackProfile: UserProfile = {
  id: 0,
  email: "alex.rivera@edu.mail.com",
  name: "Alex Rivera",
  username: "arivera_eco",
  profileImageUrl: null,
  department: "Environmental Sciences",
  college: "State University of Environmental Sciences",
  phoneNumber: "+1 (555) 012-3456",
  location: "Portland, Oregon"
};

export default function ProfilePage() {
  const { showToast } = useToast();
  const [profile, setProfile] = useState<UserProfile>(fallbackProfile);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<UserProfile>(fallbackProfile);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let mounted = true;
    apiRequest<UserProfile>("/users/me")
      .then((data) => {
        if (!mounted) return;
        setProfile(data);
        setForm(data);
        setError(null);
      })
      .catch((err) => {
        if (!mounted) return;
        setProfile(fallbackProfile);
        setForm(fallbackProfile);
        const message = err instanceof Error ? err.message : "Unable to load profile";
        setError(message);
        showToast(message, "error");
      });
    return () => {
      mounted = false;
    };
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const payload = {
        name: form.name,
        username: form.username,
        profileImageUrl: form.profileImageUrl,
        department: form.department,
        college: form.college,
        phoneNumber: form.phoneNumber,
        location: form.location
      };
      const updated = await apiRequest<UserProfile>("/users/me", {
        method: "PATCH",
        body: JSON.stringify(payload)
      });
      setProfile(updated);
      setForm(updated);
      setEditing(false);
      showToast("Profile updated successfully.", "success");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to update profile";
      setError(message);
      showToast(message, "error");
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setForm(profile);
    setEditing(false);
    setError(null);
  };

  const initials = useMemo(() => {
    return profile.name
      .split(" ")
      .map((part) => part[0])
      .slice(0, 2)
      .join("");
  }, [profile.name]);

  const handleName = form.name || profile.name;
  const handleUsername = form.username || profile.username || handleName.toLowerCase().replace(/\s+/g, "");

  return (
    <div className="home profile-page">
      <main className="page profile-shell">
        <section className="profile-card">
          <div className="profile-avatar">
            {profile.profileImageUrl ? (
              <div
                className="profile-photo"
                style={{ backgroundImage: `url(${profile.profileImageUrl})` }}
              />
            ) : (
              <div className="profile-photo fallback">{initials || "U"}</div>
            )}
          </div>
          <div className="profile-info">
            <div className="profile-header">
              <div>
                <h1>{profile.name}</h1>
                <p>@{handleUsername}</p>
              </div>
              {editing ? (
                <div className="profile-actions">
                  <button className="btn ghost" onClick={handleCancel} disabled={saving}>
                    Cancel
                  </button>
                  <button className="btn primary" onClick={handleSave} disabled={saving}>
                    {saving ? "Saving..." : "Save"}
                  </button>
                </div>
              ) : (
                <button className="btn primary" onClick={() => setEditing(true)}>
                  Edit Profile
                </button>
              )}
            </div>

            {error ? <div className="error">{error}</div> : null}

            <div className="profile-grid">
              <div>
                <span>College / University</span>
                {editing ? (
                  <input
                    value={form.college || ""}
                    onChange={(event) => setForm({ ...form, college: event.target.value })}
                  />
                ) : (
                  <strong>{profile.college || "-"}</strong>
                )}
              </div>
              <div>
                <span>Department</span>
                {editing ? (
                  <input
                    value={form.department || ""}
                    onChange={(event) => setForm({ ...form, department: event.target.value })}
                  />
                ) : (
                  <strong>{profile.department || "-"}</strong>
                )}
              </div>
              <div>
                <span>Mobile Number</span>
                {editing ? (
                  <input
                    value={form.phoneNumber || ""}
                    onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })}
                  />
                ) : (
                  <strong>{profile.phoneNumber || "-"}</strong>
                )}
              </div>
              <div>
                <span>Email Address</span>
                <strong>{profile.email}</strong>
              </div>
              <div>
                <span>Location</span>
                {editing ? (
                  <input
                    value={form.location || ""}
                    onChange={(event) => setForm({ ...form, location: event.target.value })}
                  />
                ) : (
                  <strong>{profile.location || "-"}</strong>
                )}
              </div>
            </div>
          </div>
        </section>

        <section className="profile-links">
          <Link className="profile-link" href="/dashboard/listings">
            <span>My Active Listings</span>
            <span>&gt;</span>
          </Link>
          <Link className="profile-link" href="/dashboard/orders">
            <span>Purchase History</span>
            <span>&gt;</span>
          </Link>
          <Link className="profile-link" href="/dashboard/rentals">
            <span>Rental activity & history</span>
            <span>&gt;</span>
          </Link>
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Circularity for a Greener Planet.</p>
        </div>
        <div>
          <h4>Marketplace</h4>
          <a href="#">Sustainability Report</a>
          <a href="#">Ethical Guidelines</a>
        </div>
        <div>
          <h4>Community</h4>
          <a href="#">Community Forum</a>
          <a href="#">Carbon Credits</a>
        </div>
        <div>
          <h4>Support</h4>
          <a href="#">Help Center</a>
          <a href="#">Privacy Policy</a>
        </div>
      </footer>
    </div>
  );
}
