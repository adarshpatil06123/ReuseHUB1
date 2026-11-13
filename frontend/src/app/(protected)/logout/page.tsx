"use client";

import { useEffect } from "react";
import { logout } from "@/lib/auth";

export default function LogoutPage() {
  useEffect(() => {
    let mounted = true;
    logout().finally(() => {
      if (!mounted) return;
      window.location.href = "/login";
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <main className="auth-shell">
      <section className="card">
        <h1>Signing out...</h1>
        <p className="hint">You will be redirected shortly.</p>
      </section>
    </main>
  );
}
