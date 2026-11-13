"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiRequest } from "@/lib/api";
import { useToast } from "@/context/ToastContext";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

type ListingDetail = {
  id: number;
  userId: number;
  title: string;
  description: string;
  category: string;
  condition: string;
  listingType: string;
  price: string | null;
  status: string;
  imageUrls: string[];
};

type UserInfo = {
  id: number;
  name: string;
  profileImageUrl: string | null;
};

type ChatMessage = {
  id: number;
  threadKey: string;
  senderId: number;
  content: string;
  createdAt: string;
};

type ChatHistoryResponse = {
  threadKey: string;
  messages: ChatMessage[];
};

type ChatSummary = {
  threadKey: string;
  listingId: number;
  otherUserId: number;
  otherUserName: string;
  otherUserAvatar: string | null;
  lastMessage: string;
  lastMessageAt: string | null;
};

export default function ChatPage({ params }: { params: { id: string } }) {
  const { showToast } = useToast();
  const searchParams = useSearchParams();
  const directUserId = Number(searchParams.get("userId")) || null;
  const [listing, setListing] = useState<ListingDetail | null>(null);
  const [seller, setSeller] = useState<UserInfo | null>(null);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatList, setChatList] = useState<ChatSummary[]>([]);
  const [draft, setDraft] = useState<string>("");
  const [connected, setConnected] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [chatMenuOpen, setChatMenuOpen] = useState<boolean>(false);
  const [chatActionBusy, setChatActionBusy] = useState<boolean>(false);
  const clientRef = useRef<Client | null>(null);
  const pendingMessagesRef = useRef<Array<{
    listingId: number;
    userId: number;
    otherUserId: number;
    content: string;
  }>>([]);

  const flushPendingMessages = useCallback(() => {
    const client = clientRef.current;
    if (!client?.connected) return;

    while (pendingMessagesRef.current.length > 0) {
      const message = pendingMessagesRef.current[0];
      try {
        client.publish({
          destination: "/app/chat.send",
          body: JSON.stringify(message)
        });
        pendingMessagesRef.current.shift();
      } catch {
        return;
      }
    }
  }, []);

  // Active chat state (can be initialized from URL listing/seller or selected from sidebar)
  const [activeListingId, setActiveListingId] = useState<number>(Number(params.id) || 0);
  const [activeOtherUser, setActiveOtherUser] = useState<UserInfo | null>(null);

  // Load current user profile
  useEffect(() => {
    let mounted = true;
    apiRequest<UserInfo>("/users/me")
      .then((user) => {
        if (!mounted) return;
        setCurrentUserId(user.id);
      })
      .catch(() => {
        if (!mounted) return;
        setCurrentUserId(null);
      });
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (!directUserId || !currentUserId || directUserId === currentUserId) return;
    let mounted = true;
    apiRequest<UserInfo>(`/users/${directUserId}`)
      .then((user) => {
        if (!mounted) return;
        setActiveListingId(0);
        setActiveOtherUser(user);
        setSeller(user);
        setListing(null);
      })
      .catch(() => {
        if (mounted) showToast("Could not open this conversation.", "error");
      });
    return () => {
      mounted = false;
    };
  }, [directUserId, currentUserId, showToast]);

  // Fetch chat list from backend
  const fetchChatList = () => {
    apiRequest<ChatSummary[]>("/chats")
      .then((data) => {
        setChatList(data || []);
      })
      .catch(() => {
        setChatList([]);
      });
  };

  useEffect(() => {
    fetchChatList();
  }, []);

  // Load listing details whenever activeListingId changes
  useEffect(() => {
    if (!activeListingId) return;
    let mounted = true;

    apiRequest<ListingDetail>(`/listings/${activeListingId}`)
      .then((data) => {
        if (!mounted) return;
        setListing(data);
        // If we don't have an active other user yet and this was the initial param listing
        if (!activeOtherUser && data.userId) {
          // If the listing belongs to someone else, set them as the other user
          if (currentUserId && data.userId === currentUserId) {
            // It's the user's own listing; wait for chatList to populate counterparty or pick first
            return;
          }
          apiRequest<UserInfo>(`/users/${data.userId}`)
            .then((user) => {
              if (!mounted) return;
              if (currentUserId && user.id === currentUserId) return;
              setSeller(user);
              setActiveOtherUser(user);
            })
            .catch(() => {});
        }
      })
      .catch(() => {
        if (!mounted) return;
        setListing(null);
      });

    return () => {
      mounted = false;
    };
  }, [activeListingId, currentUserId, activeOtherUser]);

  // When initial listing loads, if current user is seller or if no activeOtherUser, select first from chatList
  useEffect(() => {
    if (chatList.length > 0 && !activeOtherUser) {
      // Find if there's a chat matching activeListingId first
      const matchingChat = chatList.find((c) => c.listingId === activeListingId) || chatList[0];
      setActiveListingId(matchingChat.listingId);
      setActiveOtherUser({
        id: matchingChat.otherUserId,
        name: matchingChat.otherUserName,
        profileImageUrl: matchingChat.otherUserAvatar
      });
    }
  }, [chatList, activeOtherUser, activeListingId]);

  // Select a conversation from sidebar
  const handleSelectChat = (chat: ChatSummary) => {
    setChatMenuOpen(false);
    setActiveListingId(chat.listingId);
    setActiveOtherUser({
      id: chat.otherUserId,
      name: chat.otherUserName,
      profileImageUrl: chat.otherUserAvatar
    });
    setMessages([]);
  };

  const handleChatAction = async (action: "clear" | "delete" | "block") => {
    if (!threadKey || chatActionBusy) return;

    const confirmation = action === "clear"
      ? "Clear all messages in this chat?"
      : action === "delete"
        ? "Delete this chat and its message history?"
        : `Block ${activeOtherUser?.name || "this user"}? They will no longer be able to message you.`;
    if (!window.confirm(confirmation)) return;

    setChatActionBusy(true);
    try {
      if (action === "clear") {
        await apiRequest<void>(`/chats/${threadKey}/messages`, { method: "DELETE" });
        setMessages([]);
        fetchChatList();
        showToast("Chat messages cleared.", "success");
      } else {
        if (action === "delete") {
          await apiRequest<void>(`/chats/${threadKey}`, { method: "DELETE" });
        } else {
          await apiRequest<void>(`/chats/${threadKey}/block`, { method: "POST" });
        }
        setChatList((current) => current.filter((chat) => chat.threadKey !== threadKey));
        setActiveListingId(0);
        setActiveOtherUser(null);
        setSeller(null);
        setListing(null);
        setMessages([]);
        fetchChatList();
        showToast(action === "delete" ? "Chat deleted." : "User blocked.", "success");
      }
      setChatMenuOpen(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Could not complete this action.";
      showToast(message, "error");
    } finally {
      setChatActionBusy(false);
    }
  };

  const threadKey = useMemo(() => {
    if (activeListingId < 0 || !activeOtherUser?.id || !currentUserId) return null;
    const low = Math.min(currentUserId, activeOtherUser.id);
    const high = Math.max(currentUserId, activeOtherUser.id);
    return `${activeListingId}-${low}-${high}`;
  }, [activeListingId, activeOtherUser?.id, currentUserId]);

  // Scroll messages to bottom
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Load message history via REST immediately upon selecting thread for zero-latency loading
  useEffect(() => {
    if (!threadKey) return;
    let mounted = true;
    apiRequest<ChatMessage[]>(`/chats/${threadKey}/messages`)
      .then((history) => {
        if (!mounted || !history) return;
        setMessages(history);
      })
      .catch(() => {});
    return () => {
      mounted = false;
    };
  }, [threadKey]);

  useEffect(() => {
    if (!threadKey || !activeOtherUser?.id || !currentUserId) {
      setConnected(false);
      return;
    }

    setConnected(false);
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws/chat`),
      reconnectDelay: 2500,
      debug: () => {}
    });

    client.onConnect = () => {
      setConnected(true);
      // History subscription
      client.subscribe(`/topic/chats/${threadKey}/history`, (frame) => {
        try {
          const payload = JSON.parse(frame.body) as ChatHistoryResponse;
          if (payload.messages) {
            setMessages(payload.messages);
          }
        } catch (e) {
          console.error("Failed to parse history", e);
        }
      });
      // Live messages subscription
      client.subscribe(`/topic/chats/${threadKey}/messages`, (frame) => {
        try {
          const payload = JSON.parse(frame.body) as ChatMessage;
          setMessages((prev) => {
            // Avoid duplicate optimistic messages
            const exists = prev.some((m) => m.id === payload.id);
            if (exists) return prev;
            // Remove optimistic temp message if matching
            const filtered = prev.filter(
              (m) => !(m.id < 0 && m.senderId === payload.senderId && m.content === payload.content)
            );
            return [...filtered, payload];
          });
          // Refresh conversation previews in sidebar
          fetchChatList();
        } catch (e) {
          console.error("Failed to parse message", e);
        }
      });

      // Join chat
      client.publish({
        destination: "/app/chat.join",
        body: JSON.stringify({
          listingId: activeListingId,
          userId: currentUserId,
          otherUserId: activeOtherUser.id
        })
      });
      flushPendingMessages();
    };

    client.onDisconnect = () => setConnected(false);

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [threadKey, activeListingId, activeOtherUser?.id, currentUserId, flushPendingMessages]);

  const handleSend = () => {
    const text = draft.trim();
    if (!text || !activeOtherUser?.id || !currentUserId) return;

    // Optimistically append the message right away for instant feedback
    const optimisticMessage: ChatMessage = {
      id: -Date.now(),
      threadKey: threadKey || "",
      senderId: currentUserId,
      content: text,
      createdAt: new Date().toISOString()
    };
    setMessages((prev) => [...prev, optimisticMessage]);
    setDraft("");

    pendingMessagesRef.current.push({
      listingId: activeListingId,
      userId: currentUserId,
      otherUserId: activeOtherUser.id,
      content: text
    });
    flushPendingMessages();
  };

  const formatTime = (value: string) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  };

  const formatRelative = (value: string | null) => {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const diff = Date.now() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return "Just now";
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  };

  const listingTypeLabel = useMemo(() => {
    if (!listing) return "";
    if (listing.listingType === "SWAP") return "Swap Only";
    if (listing.listingType === "DONATE") return "Free";
    return "Direct Buy";
  }, [listing]);

  const priceLabel = useMemo(() => {
    if (!listing) return "";
    if (listing.listingType === "SWAP") return "Trade";
    if (listing.listingType === "DONATE") return "Free";
    const numericPrice = Number(String(listing.price || "0").replace(/[^0-9.]/g, ""));
    if (Number.isNaN(numericPrice)) return "₹0";
    return `₹${numericPrice.toLocaleString()}`;
  }, [listing]);

  return (
    <div className="home chat-page">
      <main className="page chat-shell">
        <aside className="impact-sidebar">
          <div className="impact-card-panel">
            <div className="impact-badge">Impact Dashboard</div>
            <p>Rank: Eco-Warrior</p>
          </div>
          <nav className="impact-nav">
            <Link href="/dashboard" className="impact-nav-link">Overview</Link>
            <Link href="/dashboard/listings" className="impact-nav-link">My Listings</Link>
            <Link href="/dashboard/orders" className="impact-nav-link">Orders</Link>
            <button className="active">Messages</button>
          </nav>
          <Link className="btn primary full" href="/marketplace/add">
            + Add Item
          </Link>
        </aside>

        <section className="message-panel">
          <div className="message-panel-header">
            <h2>Messages</h2>
            <div className="chat-search">
              <span>Search</span>
              <input
                placeholder="Search chats..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
          <div className="message-panel-list">
            {chatList.length === 0 ? (
              <div className="chat-empty">No conversations yet.</div>
            ) : (
              chatList
                .filter((chat) =>
                  !searchQuery.trim() ||
                  chat.otherUserName.toLowerCase().includes(searchQuery.toLowerCase()) ||
                  chat.lastMessage?.toLowerCase().includes(searchQuery.toLowerCase())
                )
                .map((chat) => {
                  const isActive = threadKey ? chat.threadKey === threadKey : false;
                  const initial = chat.otherUserName ? chat.otherUserName[0]?.toUpperCase() : "?";
                  return (
                    <button
                      key={chat.threadKey}
                      type="button"
                      onClick={() => handleSelectChat(chat)}
                      className={`chat-preview ${isActive ? "active" : ""}`}
                    >
                      <div className="avatar">{initial}</div>
                      <div className="chat-preview-body">
                        <strong>{chat.otherUserName}</strong>
                        <span>{chat.lastMessage || "No messages yet"}</span>
                      </div>
                      <time>{formatRelative(chat.lastMessageAt) || ""}</time>
                    </button>
                  );
                })
            )}
          </div>
        </section>

        <section className="thread-panel">
          <div className="thread-header">
            <div className="thread-user">
              <div className="avatar">
                {activeOtherUser?.name ? activeOtherUser.name[0]?.toUpperCase() : (seller?.name ? seller.name[0]?.toUpperCase() : "?")}
              </div>
              <div>
                <strong>{activeOtherUser?.name || seller?.name || "Unknown"}</strong>
                <span>{connected ? "Online" : "Connecting..."}</span>
              </div>
            </div>
            <div className="thread-actions">
              <div className="chat-menu">
                <button
                  className="icon-btn"
                  type="button"
                  aria-label="Chat options"
                  aria-haspopup="menu"
                  aria-expanded={chatMenuOpen}
                  disabled={!threadKey}
                  onClick={() => {
                    setChatMenuOpen((open) => !open);
                  }}
                >
                  ⋯
                </button>
                {chatMenuOpen && (
                  <div className="chat-menu-popover" role="menu">
                    <button
                      type="button"
                      role="menuitem"
                      disabled={chatActionBusy}
                      onClick={() => handleChatAction("clear")}
                    >
                      Clear chat
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      disabled={chatActionBusy}
                      onClick={() => handleChatAction("delete")}
                    >
                      Delete chat
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      className="danger"
                      disabled={chatActionBusy}
                      onClick={() => handleChatAction("block")}
                    >
                      Block user
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="thread-body">
            {messages.length === 0 ? (
              <div className="thread-empty-state">
                <div className="thread-empty-icon">💬</div>
                <h3>Start conversation with {activeOtherUser?.name || "seller"}</h3>
                <p>Say hello, ask questions about the item, or negotiate a swap or price!</p>
              </div>
            ) : (
              <>
                <div className="thread-date">
                  {new Date(messages[messages.length - 1].createdAt).toLocaleDateString()}
                </div>

                {messages.map((message) => {
                  const isMe = message.senderId === currentUserId;
                  const isPending = message.id < 0;
                  return (
                    <div
                      key={message.id}
                      className={`message-bubble ${isMe ? "me" : "other"} ${isPending ? "pending" : ""}`}
                    >
                      <p>{message.content}</p>
                      <span>{isPending ? "Sending..." : formatTime(message.createdAt)}</span>
                    </div>
                  );
                })}
              </>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="thread-input">
            <button className="icon-btn" aria-label="Add attachment">+</button>
            <div className="input-shell">
              <input
                placeholder="Type a message..."
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") handleSend();
                }}
              />
            </div>
            <button className="btn primary" onClick={handleSend}>
              Send
            </button>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="item-card">
            <div
              className="item-image"
              style={
                listing?.imageUrls?.[0]
                  ? { backgroundImage: `url(${listing.imageUrls[0]})` }
                  : undefined
              }
            />
            <div className="item-header">
              <h4>{listing?.title || (activeListingId === 0 ? "Direct conversation" : "Loading...")}</h4>
              {listingTypeLabel && <span className="tag">{listingTypeLabel}</span>}
            </div>
            <p>{listing?.description || (activeListingId === 0 ? `Chat directly with ${activeOtherUser?.name || "this campus member"}.` : "")}</p>
            {priceLabel && <div className="item-price">{priceLabel}</div>}
          </div>
          <div className="safe-card">
            <strong>Safety First</strong>
            <p>
              Always meet in public spaces and verify items before finalizing any
              swap. Use ReuseHub secure checkout for cash transactions.
            </p>
          </div>
        </aside>
      </main>
    </div>
  );
}
