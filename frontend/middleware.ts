import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC_PATHS = new Set(["/login", "/register"]);
const PUBLIC_PREFIXES = ["/_next", "/favicon", "/images", "/icons", "/public"];
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const ACCESS_COOKIE = "rh_access";
const DEFAULT_AUTH_REDIRECT = "/dashboard";

async function hasValidSession(request: NextRequest) {
  const cookieHeader = request.headers.get("cookie");
  if (!cookieHeader) {
    return false;
  }

  try {
    const response = await fetch(`${API_BASE}/users/me`, {
      method: "GET",
      headers: {
        cookie: cookieHeader
      },
      cache: "no-store"
    });

    return response.ok;
  } catch {
    return false;
  }
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return NextResponse.next();
  }

  const token = request.cookies.get(ACCESS_COOKIE)?.value;
  const isPublicPath = PUBLIC_PATHS.has(pathname);

  if (!token) {
    if (isPublicPath) {
      return NextResponse.next();
    }

    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }

  const sessionOk = await hasValidSession(request);
  if (!sessionOk) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("next", pathname);
    const response = NextResponse.redirect(url);
    response.cookies.set(ACCESS_COOKIE, "", { maxAge: 0, path: "/" });
    return response;
  }

  if (isPublicPath) {
    const url = request.nextUrl.clone();
    url.pathname = DEFAULT_AUTH_REDIRECT;
    url.searchParams.delete("next");
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"]
};
