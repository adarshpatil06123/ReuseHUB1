import { cookies } from "next/headers";
import { redirect } from "next/navigation";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const LOGIN_PATH = "/login";

function getCookieHeader(): Record<string, string> {
  const cookieStore = cookies();
  const raw = cookieStore.toString();
  return raw ? { cookie: raw } : {};
}

function buildRedirect(nextPath?: string) {
  if (!nextPath) {
    return LOGIN_PATH;
  }
  return `${LOGIN_PATH}?next=${encodeURIComponent(nextPath)}`;
}

export async function requireAuth(nextPath?: string) {
  const cookieStore = cookies();
  if (!cookieStore.get("rh_access")) {
    redirect(buildRedirect(nextPath));
  }

  const response = await fetch(`${API_BASE}/users/me`, {
    method: "GET",
    headers: {
      ...getCookieHeader()
    },
    cache: "no-store"
  });

  if (!response.ok) {
    redirect(buildRedirect(nextPath));
  }
}
