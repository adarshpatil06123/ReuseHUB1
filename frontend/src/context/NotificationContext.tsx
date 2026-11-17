"use client";

import React, { createContext, useContext, useEffect, useRef, useState, ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiRequest } from "@/lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export type ChatNotification = {
  notificationType: "CHAT";
  notificationId: string;
  messageId: number;
  threadKey: string;
  listingId: number;
  listingTitle: string;
  senderId: number;
  senderName: string;
  senderAvatar: string | null;
  content: string;
  createdAt: string;
};

export type OrderNotification = {
  notificationType: "ORDER";
  notificationId: string;
  orderId: string;
  listingId: number;
  listingTitle: string;
  actorName: string;
  content: string;
  status: string;
  createdAt: string;
};

export type RentalNotification = {
  notificationType: "RENTAL";
  notificationId: string;
  rentalId: string;
  listingId: number;
  listingTitle: string;
  actorName: string;
  content: string;
  status: string;
  createdAt: string;
};

export type ListingNotification = {
  notificationType: "NEED_MATCH" | "RELISTING";
  notificationId: string;
  listingId: number;
  listingTitle: string;
  actorName: string;
  content: string;
  createdAt: string;
};

export type AppNotification = ChatNotification | OrderNotification | RentalNotification | ListingNotification;

function parseNotification(payload: unknown): AppNotification | null {
  if (!payload || typeof payload !== "object") return null;
  const value = payload as Record<string, unknown>;

  if (
    value.notificationType === "RENTAL" &&
    typeof value.notificationId === "string" &&
    typeof value.rentalId === "string" &&
    typeof value.listingId === "number" &&
    typeof value.listingTitle === "string" &&
    typeof value.actorName === "string" &&
    typeof value.content === "string" &&
    typeof value.status === "string" &&
    typeof value.createdAt === "string"
  ) {
    return {
      notificationType: "RENTAL",
      notificationId: value.notificationId,
      rentalId: value.rentalId,
      listingId: value.listingId,
      listingTitle: value.listingTitle,
      actorName: value.actorName,
      content: value.content,
      status: value.status,
      createdAt: value.createdAt
    };
  }

  if (
    (value.notificationType === "NEED_MATCH" || value.notificationType === "RELISTING") &&
    typeof value.notificationId === "string" &&
    typeof value.listingId === "number" &&
    typeof value.listingTitle === "string" &&
    typeof value.actorName === "string" &&
    typeof value.content === "string" &&
    typeof value.createdAt === "string"
  ) {
    return {
      notificationType: value.notificationType,
      notificationId: value.notificationId,
      listingId: value.listingId,
      listingTitle: value.listingTitle,
      actorName: value.actorName,
      content: value.content,
      createdAt: value.createdAt
    };
  }

  if (
    value.notificationType === "ORDER" &&
    typeof value.notificationId === "string" &&
    typeof value.orderId === "string" &&
    typeof value.listingId === "number" &&
    typeof value.listingTitle === "string" &&
    typeof value.actorName === "string" &&
    typeof value.content === "string" &&
    typeof value.status === "string" &&
    typeof value.createdAt === "string"
  ) {
    return {
      notificationType: "ORDER",
      notificationId: value.notificationId,
      orderId: value.orderId,
      listingId: value.listingId,
      listingTitle: value.listingTitle,
      actorName: value.actorName,
      content: value.content,
      status: value.status,
      createdAt: value.createdAt
    };
  }

  if (
    typeof value.messageId === "number" &&
    typeof value.threadKey === "string" &&
    typeof value.listingId === "number" &&
    typeof value.listingTitle === "string" &&
    typeof value.senderId === "number" &&
    typeof value.senderName === "string" &&
    typeof value.content === "string" &&
    typeof value.createdAt === "string"
  ) {
    return {
      notificationType: "CHAT",
      notificationId: String(value.messageId),
      messageId: value.messageId,
      threadKey: value.threadKey,
      listingId: value.listingId,
      listingTitle: value.listingTitle,
      senderId: value.senderId,
      senderName: value.senderName,
      senderAvatar: typeof value.senderAvatar === "string" ? value.senderAvatar : null,
      content: value.content,
      createdAt: value.createdAt
    };
  }

  return null;
}

type UserMe = {
  id: number;
  name: string;
  email: string;
};

type NotificationContextType = {
  currentUser: UserMe | null;
  unreadCount: number;
  notifications: AppNotification[];
  clearNotifications: () => void;
  markAsRead: () => void;
};

const NotificationContext = createContext<NotificationContextType>({
  currentUser: null,
  unreadCount: 0,
  notifications: [],
  clearNotifications: () => {},
  markAsRead: () => {}
});

export const useNotifications = () => useContext(NotificationContext);

// Audio chime using Web Audio API for a smooth, pleasant modern tone
function playChime() {
  try {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();

    const now = ctx.currentTime;
    const osc1 = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const gain = ctx.createGain();

    osc1.type = "sine";
    osc2.type = "triangle";

    // Play two subtle pleasing harmonic frequencies
    osc1.frequency.setValueAtTime(587.33, now); // D5
    osc1.frequency.exponentialRampToValueAtTime(880, now + 0.12); // A5

    osc2.frequency.setValueAtTime(440, now); // A4
    osc2.frequency.exponentialRampToValueAtTime(659.25, now + 0.12); // E5

    gain.gain.setValueAtTime(0.08, now);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.5);

    osc1.connect(gain);
    osc2.connect(gain);
    gain.connect(ctx.destination);

    osc1.start(now);
    osc2.start(now);
    osc1.stop(now + 0.52);
    osc2.stop(now + 0.52);
  } catch {
    // Audio context may require initial user gesture
  }
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<UserMe | null>(null);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [activePopup, setActivePopup] = useState<AppNotification | null>(null);
  const router = useRouter();
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // Load current user
  useEffect(() => {
    let mounted = true;
    apiRequest<UserMe>("/users/me")
      .then((data) => {
        if (mounted) setCurrentUser(data);
      })
      .catch(() => {});
    return () => {
      mounted = false;
    };
  }, []);

  // Connect STOMP socket to receive real-time notifications
  useEffect(() => {
    if (!currentUser?.id) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws/chat`),
      reconnectDelay: 4000,
      debug: () => {}
    });

    client.onConnect = () => {
      client.subscribe(`/topic/users/${currentUser.id}/notifications`, (frame) => {
        try {
          const payload = parseNotification(JSON.parse(frame.body));
          if (!payload) return;
          // Add to notifications queue
          setNotifications((prev) => [payload, ...prev.slice(0, 19)]);
          setUnreadCount((prev) => prev + 1);

          setActivePopup(payload);
          playChime();

          if (timerRef.current) clearTimeout(timerRef.current);
          timerRef.current = setTimeout(() => {
            setActivePopup(null);
          }, 6000);
        } catch (e) {
          console.error("Failed to parse notification payload", e);
        }
      });
    };

    client.activate();

    return () => {
      client.deactivate();
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [currentUser?.id]);

  const clearNotifications = () => {
    setNotifications([]);
    setUnreadCount(0);
  };

  const markAsRead = () => {
    setUnreadCount(0);
  };

  const handleOpenNotification = (notif: AppNotification) => {
    setActivePopup(null);
    setUnreadCount((c) => Math.max(0, c - 1));
    router.push(notif.notificationType === "RENTAL"
      ? "/dashboard/rentals"
      : notif.notificationType === "ORDER"
        ? "/dashboard/orders"
      : notif.notificationType === "CHAT"
        ? notif.listingId === 0
          ? `/marketplace/0/chat?userId=${notif.senderId}`
          : `/marketplace/${notif.listingId}/chat`
        : `/marketplace/${notif.listingId}`);
  };

  return (
    <NotificationContext.Provider
      value={{
        currentUser,
        unreadCount,
        notifications,
        clearNotifications,
        markAsRead
      }}
    >
      {children}

      {/* Floating Notification Popup Toast */}
      {activePopup && (
        <div className="chat-notification-toast-container">
          <div
            className="chat-notification-toast"
            onClick={() => handleOpenNotification(activePopup)}
            role="alert"
          >
            <div className="toast-avatar">
              {("actorName" in activePopup ? activePopup.actorName : activePopup.senderName)
                ? ("actorName" in activePopup ? activePopup.actorName : activePopup.senderName)[0]?.toUpperCase()
                : "?"}
            </div>
            <div className="toast-content">
              <div className="toast-header-row">
                <span className="toast-sender">
                  {"actorName" in activePopup ? activePopup.actorName : activePopup.senderName}
                </span>
                <span className="toast-badge">
                  {activePopup.notificationType === "ORDER"
                    ? "Order update"
                    : activePopup.notificationType === "RENTAL"
                      ? "Rental update"
                    : activePopup.notificationType === "CHAT"
                      ? "New message"
                      : activePopup.notificationType === "NEED_MATCH"
                        ? "Need match"
                        : "Relisting suggestion"}
                </span>
              </div>
              <p className="toast-message">{activePopup.content}</p>
              <span className="toast-item-tag">
                📦 {activePopup.listingTitle}
                {activePopup.notificationType === "ORDER" ? " · View My Orders" : ""}
              </span>
            </div>
            <button
              className="toast-close"
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setActivePopup(null);
              }}
              aria-label="Close notification"
            >
              ✕
            </button>
          </div>
        </div>
      )}
    </NotificationContext.Provider>
  );
}
