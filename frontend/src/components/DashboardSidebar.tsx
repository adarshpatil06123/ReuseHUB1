"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { apiRequest } from "@/lib/api";
import { useNotifications } from "@/context/NotificationContext";

type OrderItem = {
  id: string;
  role: "BUYER" | "SELLER";
  status: string;
};

const navItems = [
  { href: "/dashboard", label: "Overview" },
  { href: "/dashboard/listings", label: "My Listings" },
  { href: "/dashboard/rentals", label: "Rentals" },
  { href: "/dashboard/orders", label: "Orders" },
  { href: "/marketplace/1/chat", label: "Messages" }
];

const isActivePath = (pathname: string, href: string) => {
  if (href === "/dashboard") {
    return pathname === "/dashboard";
  }
  if (href.startsWith("/dashboard/")) {
    return pathname.startsWith(href);
  }
  if (href.startsWith("/marketplace")) {
    return pathname.startsWith("/marketplace") && pathname.includes("/chat");
  }
  return pathname === href;
};

export default function DashboardSidebar() {
  const pathname = usePathname();
  const { unreadCount } = useNotifications();
  const [pendingOrdersCount, setPendingOrdersCount] = useState<number>(0);

  useEffect(() => {
    let mounted = true;
    const loadOrders = () => {
      apiRequest<OrderItem[]>("/orders")
        .then((orders) => {
          if (!mounted) return;
          const pending = orders.filter(
            (o) => o.role === "SELLER" && o.status === "REQUESTED"
          ).length;
          setPendingOrdersCount(pending);
        })
        .catch(() => {});
    };

    loadOrders();
    const interval = setInterval(loadOrders, 10000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <aside className="dashboard-sidebar">
      <div className="impact-card-panel">
        <div className="impact-badge">Impact Dashboard</div>
        <p>Rank: Eco-Warrior</p>
      </div>
      <nav className="impact-nav">
        {navItems.map((item) => {
          const isActive = isActivePath(pathname, item.href);
          const isOrders = item.label === "Orders";
          const isMessages = item.label === "Messages";

          return (
            <Link
              key={item.href}
              href={item.href}
              className={isActive ? "active" : ""}
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
            >
              <span>{item.label}</span>
              {isOrders && pendingOrdersCount > 0 && (
                <span
                  style={{
                    background: "#f59e0b",
                    color: "#ffffff",
                    fontSize: "11px",
                    fontWeight: 700,
                    borderRadius: "999px",
                    padding: "1px 7px"
                  }}
                >
                  {pendingOrdersCount}
                </span>
              )}
              {isMessages && unreadCount > 0 && (
                <span
                  style={{
                    background: "#0f172a",
                    color: "#ffffff",
                    fontSize: "11px",
                    fontWeight: 700,
                    borderRadius: "999px",
                    padding: "1px 7px"
                  }}
                >
                  {unreadCount}
                </span>
              )}
            </Link>
          );
        })}
      </nav>
      <Link className="btn primary full" href="/marketplace/add">
        + Add Item
      </Link>
    </aside>
  );
}
