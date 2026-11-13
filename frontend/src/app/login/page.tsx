"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { login } from "@/lib/auth";
import { useToast } from "@/context/ToastContext";

function LoginForm() {
  const { showToast } = useToast();
  const searchParams = useSearchParams();
  const nextParam = searchParams.get("next") || "/";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [resetEmail, setResetEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({ email, password });
      window.location.href = nextParam;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Login failed";
      setError(message);
      showToast(message, "error");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <label className="field">
        Email Address
        <input
          type="email"
          name="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </label>
      <label className="field">
        Password
        <input
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "0.85rem", margin: "4px 0 16px 0" }}>
        <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer", margin: 0 }}>
          <input
            type="checkbox"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          Remember me
        </label>
        <button
          type="button"
          onClick={() => setShowForgotModal(true)}
          style={{ background: "none", border: "none", color: "var(--primary, #0f6b3f)", cursor: "pointer", fontWeight: 500 }}
        >
          Forgot password?
        </button>
      </div>

      {error ? <div className="error">{error}</div> : null}
      <button className="btn primary full auth-submit" type="submit" disabled={loading}>
        {loading ? "Signing in..." : "Sign in"}
      </button>

      {showForgotModal && (
        <div style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.5)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 9999,
          padding: "16px"
        }}>
          <div style={{
            background: "#ffffff",
            padding: "24px",
            borderRadius: "16px",
            maxWidth: "420px",
            width: "100%",
            boxShadow: "0 20px 40px rgba(0,0,0,0.2)"
          }}>
            <h3 style={{ marginTop: 0 }}>Reset Password</h3>
            <p style={{ color: "#666", fontSize: "0.9rem" }}>
              Enter your student email and we'll send you a password recovery link.
            </p>
            <input
              type="email"
              placeholder="e.g. alex@campus.edu"
              value={resetEmail}
              onChange={(e) => setResetEmail(e.target.value)}
              style={{
                width: "100%",
                padding: "10px 12px",
                borderRadius: "8px",
                border: "1px solid #ccc",
                margin: "12px 0 20px 0"
              }}
            />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
              <button
                type="button"
                className="btn ghost"
                onClick={() => setShowForgotModal(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn primary"
                onClick={() => {
                  if (!resetEmail.includes("@")) {
                    showToast("Please enter a valid campus email", "error");
                    return;
                  }
                  setShowForgotModal(false);
                  showToast("Password reset instructions sent to " + resetEmail, "success");
                }}
              >
                Send Reset Link
              </button>
            </div>
          </div>
        </div>
      )}
    </form>
  );
}

export default function LoginPage() {
  return (
    <main className="auth-split">
      <section className="auth-hero auth-hero-login">
        <div className="auth-hero-content">
          <div className="brand-chip">
            <span className="brand-icon">R</span>
            <span>ReuseHub</span>
          </div>
          <h1>
            Circularity for a <span>Greener</span> Planet.
          </h1>
          <p>
            Join over 50,000 students and community members exchanging goods,
            reducing waste, and building a sustainable future.
          </p>
          <div className="auth-stats">
            <div>
              <strong>12k+</strong>
              <span>Items Reused</span>
            </div>
            <div>
              <strong>4.2t</strong>
              <span>CO2 Saved</span>
            </div>
          </div>
        </div>
      </section>

      <section className="auth-panel auth-panel-login">
        <div className="auth-card auth-card-login">
          <h2>Welcome back</h2>
          <p className="auth-subtitle">Sign in and continue your impact journey.</p>

          <div className="auth-social">
            <button className="btn ghost full auth-provider" type="button">
              Google
            </button>
            <button className="btn ghost full auth-provider" type="button">
              Apple
            </button>
          </div>

          <div className="auth-divider auth-divider-login">
            <span>Or continue with</span>
          </div>

          <Suspense fallback={<div>Loading login...</div>}>
            <LoginForm />
          </Suspense>

          <p className="auth-footnote">
            New here? <Link href="/register">Create an account</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
