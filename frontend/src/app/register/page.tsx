"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { register } from "@/lib/auth";
import { useToast } from "@/context/ToastContext";

function RegisterForm() {
  const { showToast } = useToast();
  const searchParams = useSearchParams();
  const nextParam = searchParams.get("next") || "/dashboard";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [college, setCollege] = useState("");
  const [department, setDepartment] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await register({
        email,
        password,
        name,
        college: college || undefined,
        department: department || undefined
      });
      window.location.href = nextParam;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Registration failed";
      setError(message);
      showToast(message, "error");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <label className="field">
        Full Name
        <input
          type="text"
          name="name"
          autoComplete="name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
        />
      </label>
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
      <div className="field-row split">
        <label className="field">
          University
          <select
            name="college"
            value={college}
            onChange={(event) => setCollege(event.target.value)}
          >
            <option value="">Select Institution</option>
            <option>New Horizon College of Engineering</option>
            <option>Other</option>
          </select>
        </label>
        <label className="field">
          Department
          <input
            type="text"
            name="department"
            value={department}
            onChange={(event) => setDepartment(event.target.value)}
          />
        </label>
      </div>
      <div className="field-row split">
        <label className="field">
          Password
          <input
            type="password"
            name="password"
            autoComplete="new-password"
            minLength={8}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
      </div>

      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={accepted}
          onChange={() => setAccepted((prev) => !prev)}
          required
        />
        <span>
          I agree to the <strong>Ethical Guidelines</strong> and <strong>Terms of Service</strong>.
        </span>
      </label>

      {error ? <div className="error">{error}</div> : null}
      <button
        className="btn primary full auth-submit"
        type="submit"
        disabled={loading || !accepted}
      >
        {loading ? "Creating..." : "Sign Up"}
      </button>
    </form>
  );
}

export default function RegisterPage() {
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
          <h2>Join the Circular Community</h2>
          <p className="auth-subtitle">Create your account and start making impact today.</p>

          <div className="auth-social">
            <button className="btn ghost full auth-provider" type="button">Google</button>
            <button className="btn ghost full auth-provider" type="button">Apple</button>
          </div>

          <div className="auth-divider auth-divider-login">
            <span>Or continue with</span>
          </div>

          <Suspense fallback={<div>Loading registration form...</div>}>
            <RegisterForm />
          </Suspense>

          <p className="auth-footnote">
            Already have an account? <Link href="/login">Log In</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
