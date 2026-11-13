# Technical Requirement Document - ReuseHub (V1)

## 1. System Architecture Overview

### Recommended Architecture: Modular Monolith (NOT microservices)

One campus, one product, one codebase. Keep it modular internally, not distributed.

### High-Level Components

```
[ Next.js Frontend ]
        |
        | HTTPS (REST API)
        |
[ Spring Boot Backend ]
        |
        | JPA / ORM
        |
[ MySQL Database ]
        |
[ Object Storage (Images - S3/GCS) ]
```

### Core Backend Modules

- Auth Module
- User Module
- Listing Module
- Chat Module
- Transaction Module
- Notification Module (lightweight)

## 2. Frontend Responsibilities (Next.js)

Frontend contains orchestration + UI only. No business rules.

### Responsibilities

- Authentication (token storage, session handling)
- Form handling (listing creation, edits)
- Rendering feeds (SSR/CSR hybrid)
- Chat UI (WebSocket or polling)
- API integration layer
- Basic state management (minimal, avoid over-centralizing)

### Key Pages

- Home Feed
- Listing Detail
- Create/Edit Listing
- Chat Interface
- Profile Dashboard

### Frontend Must NOT Do

- Price validation logic
- Transaction state handling
- Trust or security decisions

## 3. Backend Responsibilities (Spring Boot)

### Auth Service

- Email verification
- JWT generation
- Session validation

### User Service

- Profile management
- Rating aggregation

### Listing Service

- CRUD operations
- Filtering and search
- Status transitions (ACTIVE -> SOLD)

### Chat Service

- Real-time messaging (WebSocket preferred)
- Item-scoped chat rooms

### Transaction Service

- Offer handling
- Swap logic
- Request lifecycle (PENDING -> ACCEPTED -> COMPLETED)

### Notification Service (basic)

- Trigger events -> store notifications
- Email fallback

## 4. Database Schema Proposal (MySQL)

### Users Table

```
id (PK)
email (unique)
password_hash
name
profile_image_url
department
rating_avg
rating_count
is_active
email_verified
created_at
updated_at
```

### Listings Table

```
id (PK)
user_id (FK)
title
description
category
condition
listing_type (SELL | SWAP | DONATE)
price (nullable)
status (ACTIVE | RESERVED | SOLD | SWAPPED | DONATED)
created_at
updated_at
```

### Listing_Images Table

```
id (PK)
listing_id (FK)
image_url
```

### Chats Table

```
id (PK)
listing_id (FK)
buyer_id (FK)
seller_id (FK)
created_at
```

### Messages Table

```
id (PK)
chat_id (FK)
sender_id (FK)
content
created_at
```

### Transactions Table

```
id (PK)
listing_id (FK)
buyer_id (FK)
seller_id (FK)
status (PENDING | ACCEPTED | COMPLETED | CANCELLED)
created_at
updated_at
```

### Swap_Offers Table

```
id (PK)
listing_id (FK)
offered_listing_id (FK)
status (PENDING | ACCEPTED | REJECTED)
```

### Ratings Table

```
id (PK)
from_user_id
to_user_id
transaction_id
rating (1-5)
comment
```

### Notifications Table

```
id (PK)
user_id
type
message
is_read
created_at
```

## 5. API Structure (REST)

### Auth

```
POST /auth/register
POST /auth/login
POST /auth/verify-email
POST /auth/logout
```

### Users

```
GET /users/me
GET /users/{id}
```

### Listings

```
POST   /listings
GET    /listings
GET    /listings/{id}
PUT    /listings/{id}
DELETE /listings/{id}
PATCH  /listings/{id}/status
```

### Search and Filters

```
GET /listings?category=&price_min=&price_max=&type=&keyword=
```

### Chat

```
GET  /chats/{listingId}
POST /messages
GET  /messages/{chatId}
```

WebSocket endpoint:

```
/ws/chat
```

### Transactions

```
POST /transactions/request
POST /transactions/{id}/accept
POST /transactions/{id}/complete
POST /transactions/{id}/cancel
```

### Swap

```
POST /swaps
POST /swaps/{id}/accept
POST /swaps/{id}/reject
```

### Notifications

```
GET /notifications
PATCH /notifications/{id}/read
```

## 6. Authentication Strategy

### JWT-Based Authentication

Flow:

1. User logs in
2. Backend issues:
   - Access token (short-lived)
   - Refresh token (optional for V1)

Storage:

- Frontend uses HTTP-only cookies

Email Verification:

- Token-based verification link

Avoid:

- OAuth for V1
- Complex RBAC (only admin vs user)

## 7. Third-Party Dependencies

- Email: SMTP-based provider (e.g., SendGrid)
- Object Storage: S3-compatible (AWS S3 or equivalent)
- Image processing: server-side resize and format validation
- Observability: application logs + metrics (Prometheus/Grafana optional)

## 8. Scalability Considerations (Lean)

- Start single-region, single database instance
- Use read replicas only when needed
- Use CDN for listing images
- Queue background jobs for email and notifications
- Avoid premature sharding or microservices

## 9. Order Collateral and Disputes (Current MVP)

- Accepted BUY orders record a simulated collateral amount using the seller-accepted price, falling back to the offer or listing price. It is marked held on acceptance, released after the two-party handoff, and refunded on cancellation or no-show.
- This is lifecycle tracking only. The MVP has no payment provider, wallet, or real funds custody; the frontend must never describe this as money actually held by ReuseHub.
- A buyer or seller can open one dispute for an accepted, completed, or no-show order, upload PNG/JPEG/PDF evidence, and view the evidence integrity metadata. Evidence downloads require an order participant.
- Evidence files are stored outside the public listing-upload directory. SHA-256 file digests and an ordered hash chain make file or record changes detectable through the application; there are no evidence edit/delete endpoints. This is tamper-evident application storage, not a substitute for immutable object storage or independently anchored audit logs.
- Disputes are participant-managed: they remain open until both parties confirm closure. This MVP does not provide an admin tribunal or binding ruling.

## 10. Need Requests, Bundles, and Relisting Suggestions

- Buyers can post Need Requests from the campus demand heatmap. Requests persist keywords, category, notes, acceptable condition, maximum price, meetup zone, timeline, donation preference, and an optional uploaded reference image. Saving as a draft keeps the request private and does not run matching; publishing it immediately matches active listings, and later matching listings publish a live notification. Matches are also refreshed in the request view every 15 seconds as a reconnect/polling fallback. Notifications are prompts only and do not create an order.
- Published requests can optionally contribute one anonymous demand signal to their selected zone on `GET /demand/heatmap`. Only aggregate counts are returned; request descriptions, identity, and contact details are not exposed through the heatmap.
- Buyers can request a bundle of 2–5 active SELL listings owned by the same seller. Each item remains an order row linked by a bundle ID. Seller acceptance, cancellation/rejection, handoff completion, and no-show settlement operate on the full bundle within a single database transaction; all listings are reserved before acceptance and finalized together.
- Bundle collateral is simulated per item at its listing price. No payment is processed or funds held. Bundle prices are not negotiated through the single-order final-price field.
- Active listings with no update for 14 days receive one relisting suggestion with a 10% lower-price recommendation when priced. Owners can apply the suggestion or dismiss it; the backend scheduled job and notification timing can be configured through `reusehub.listings.relisting-after` and the relisting check interval.

## 11. Campus Demand Heatmap

- The public `GET /demand/heatmap` endpoint aggregates anonymous demand by location. Demand signals are wishlist saves, open order requests for active listings, and published Need Requests whose owner opted into the heatmap; each signal contributes one point to the zone score.
- The homepage renders a Leaflet/OpenStreetMap map centered on New Horizon College of Engineering, below the latest listings, with demand circles and separate wishlist/request counts. Hub coordinates are approximate offsets around the campus center because the current listing schema stores location names, not coordinates; this limitation is disclosed in the UI. Zones with no current activity are omitted. No user identity or precise location data is exposed.
- The homepage fetches demand on load and polls the aggregation endpoint every 60 seconds, with a manual refresh action. This is near-real-time polling rather than a push-based live stream. The Need Request composer refreshes every 15 seconds and also reacts to existing WebSocket match notifications.
