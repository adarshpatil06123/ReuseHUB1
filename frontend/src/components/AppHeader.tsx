"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useNotifications } from "@/context/NotificationContext";
import { useSearch } from "@/context/SearchContext";

const navItems = [
  { href: "/marketplace", label: "Marketplace" },
  { href: "/wishlist", label: "Wishlist" },
  { href: "/dashboard", label: "Dashboard" }
];

const isActivePath = (pathname: string, href: string) => {
  if (href === "/marketplace") {
    return pathname === "/marketplace" || pathname.startsWith("/marketplace/");
  }
  if (href === "/dashboard") {
    return pathname === "/dashboard" || pathname.startsWith("/dashboard/");
  }
  return pathname === href;
};

export default function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { searchQuery, setSearchQuery } = useSearch();
  const { currentUser, unreadCount, notifications, markAsRead, clearNotifications } = useNotifications();
  const [dropdownOpen, setDropdownOpen] = useState(false);

  return (
    <header className="topbar">
      <Link className="logo" href="/">
        ReuseHub
      </Link>
      <nav className="nav">
        {navItems.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={isActivePath(pathname, item.href) ? "active" : ""}
          >
            {item.label}
          </Link>
        ))}
      </nav>
      <div className="topbar-actions">
        <div className="search">
          <input
            type="search"
            placeholder="Search marketplace..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              if (pathname !== "/marketplace") {
                router.push("/marketplace");
              }
            }}
          />
        </div>

        {/* Real-time Notification Bell with Token Badge */}
        <div className="notification-bell-wrapper">
          <button
            type="button"
            className="notification-bell-btn"
            onClick={() => {
              setDropdownOpen((prev) => !prev);
              if (!dropdownOpen) markAsRead();
            }}
            aria-label="View notifications"
          >
            <svg
              className="bell-icon-svg"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="currentColor"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path d="M12 2C10.9 2 10 2.9 10 4C7.24 4.46 5 6.97 5 10V16L3.5 17.5C3.18 17.82 3 18.25 3 18.7C3 19.42 3.58 20 4.3 20H19.7C20.42 20 21 19.42 21 18.7C21 18.25 20.82 17.82 20.5 17.5L19 16V10C19 6.97 16.76 4.46 14 4C14 2.9 13.1 2 12 2Z" />
              <path d="M12 23C13.1 23 14 22.1 14 21H10C10 22.1 10.9 23 12 23Z" />
            </svg>
            {unreadCount > 0 && (
              <span className="notification-badge-token">
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            )}
          </button>

          {dropdownOpen && (
            <div className="notification-dropdown">
              <div className="notification-dropdown-header">
                <h4>Notifications</h4>
                {notifications.length > 0 && (
                  <button type="button" onClick={clearNotifications} className="btn-clear-notifs">
                    Clear all
                  </button>
                )}
              </div>
              <div className="notification-dropdown-list">
                {notifications.length === 0 ? (
                  <div className="notification-empty">No new notifications</div>
                ) : (
                  notifications.map((notif) => (
                    <Link
                      key={notif.notificationId}
                      href={notif.notificationType === "ORDER"
                        ? "/dashboard/orders"
                        : notif.notificationType === "CHAT"
                          ? notif.listingId === 0
                            ? `/marketplace/0/chat?userId=${notif.senderId}`
                            : `/marketplace/${notif.listingId}/chat`
                          : `/marketplace/${notif.listingId}`}
                      className="notification-item"
                      onClick={() => setDropdownOpen(false)}
                    >
                      <div className="notif-avatar">
                        {("actorName" in notif ? notif.actorName : notif.senderName)
                          ? ("actorName" in notif ? notif.actorName : notif.senderName)[0]?.toUpperCase()
                          : "?"}
                      </div>
                      <div className="notif-body">
                        <strong>
                          {"actorName" in notif ? notif.actorName : notif.senderName}
                          {notif.notificationType === "ORDER"
                            ? " · Order update"
                            : notif.notificationType === "NEED_MATCH"
                              ? " · Need match"
                              : notif.notificationType === "RELISTING"
                                ? " · Relisting suggestion"
                                : ""}
                        </strong>
                        <p>{notif.content}</p>
                        <span className="notif-meta">
                          {notif.notificationType === "ORDER" ? "Order: " : "Re: "}
                          {notif.listingTitle}
                        </span>
                      </div>
                    </Link>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        <Link className="logout-link" href="/logout">
          Logout
        </Link>
        <Link className="avatar" href="/profile" title={currentUser?.name || "Profile"}>
          {currentUser?.name ? currentUser.name[0].toUpperCase() : "A"}
        </Link>
      </div>
    </header>
  );
}
