import { NextResponse } from "next/server";
import { headers } from "next/headers";

export const dynamic = "force-dynamic";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

type RouteContext = {
  params: {
    listingId: string;
  };
};

async function proxyRequest(method: "POST" | "DELETE", listingId: string) {
  const cookieHeader = headers().get("cookie") || "";
  const response = await fetch(`${API_BASE}/wishlist/${listingId}`, {
    method,
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store"
  });

  const text = await response.text();
  return new NextResponse(text, {
    status: response.status,
    headers: {
      "Content-Type": response.headers.get("content-type") || "application/json"
    }
  });
}

export async function POST(_request: Request, context: RouteContext) {
  return proxyRequest("POST", context.params.listingId);
}

export async function DELETE(_request: Request, context: RouteContext) {
  return proxyRequest("DELETE", context.params.listingId);
}
