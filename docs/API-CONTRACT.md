# API contract (browser -> nginx -> api-gateway -> services)

Base URL for the SPA: **`/api/v1`** (same origin; nginx proxies `/api/` to the gateway, `/otlp/` to the
OpenTelemetry Collector, `/media/` to the S3 bucket holding uploaded images).
All endpoints except `/auth/**` need `Authorization: Bearer <accessToken>`.
The gateway answers a missing/invalid token with **401 and an empty body** - handle non-JSON/empty bodies.
Service errors are JSON: `{ "timestamp", "status", "error", "message", "path" }` - show `message`.
IDs are UUID strings. Timestamps are ISO-8601 strings (LocalDateTime, no zone, UTC server time).

## Auth (public)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/register` | `{firstName,lastName,email,password(min 6),headline?,location?}` | 201 `AuthResponse` |
| POST | `/auth/login` | `{email,password}` | 200 `AuthResponse` |

`AuthResponse = { accessToken, refreshToken, userId, email, firstName, lastName, tokenType:"Bearer" }`
(access token lives 24h; there is no refresh endpoint -> on any 401 clear the session and go to /login).
Errors: 400 validation (`message` = first failing rule), 401 bad credentials ("Invalid email or password"),
409 duplicate e-mail ("Email already registered").

## Users
`UserResponse = { id,email,firstName,lastName,headline,about,location,profilePhotoUrl,coverPhotoUrl,role,skills:string[],createdAt }`
(`role`: NORMAL_USER | ADMIN | RECRUITER; any field except id/email/names may be null)

| Method | Path | Notes |
|---|---|---|
| GET | `/users/{id}` | profile (404 if unknown) |
| PUT | `/users/{id}/profile` | JSON `{headline,about,location,skills}`; only own profile (403) -> `UserResponse` |
| POST | `/users/{id}/profile-photo` | `multipart/form-data`, field `file` (image, <=10MB); only own (403) -> `UserResponse` |
| GET | `/users/suggestions?limit=12` | people you may know: not you, not connected, no pending request -> `UserResponse[]` |
| GET | `/users/{id}/connection-status` | relationship between the caller and `{id}` -> `{status, connectionId}`; status = `SELF`\|`NONE`\|`PENDING_SENT`\|`PENDING_RECEIVED`\|`CONNECTED`\|`REJECTED` |
| POST | `/users/{id}/connect` | send request; text/plain body "Connection request sent"; 409 if already sent/connected; 400 if self |
| PUT | `/users/connection/{connectionId}/accept` | only the receiver; text/plain body |
| PUT | `/users/connection/{connectionId}/reject` | only the receiver; text/plain body |
| GET | `/users/{id}/connections/pending` | only own (403); incoming requests -> `Connection[]` |
| GET | `/users/{id}/connections` | accepted connections of `{id}` (both directions) -> `UserResponse[]` |

`Connection = { id, requesterId, receiverId, status:"PENDING"|"CONNECTED"|"REJECTED", createdAt, updatedAt }`
(for pending requests fetch the requester's profile with `GET /users/{requesterId}` and cache it client side)

## Posts
`PostResponse = { id, authorId, content, imageUrl|null, likeCount, commentCount, likedByMe, createdAt, updatedAt }`
`Comment = { id, postId, authorId, content, createdAt }`

| Method | Path | Notes |
|---|---|---|
| POST | `/posts` | `multipart/form-data`: `content` (required, text), `image` (optional file <=10MB). Author = caller. 201 -> `PostResponse` |
| GET | `/posts/{id}` | -> `PostResponse` (404) |
| GET | `/posts?ids=a,b,c` | batch, **same order as `ids`**, unknown/deleted ids skipped (max 50) -> `PostResponse[]` |
| GET | `/posts/user/{userId}` | newest first -> `PostResponse[]` |
| POST | `/posts/{id}/like` | toggles like for the caller; text/plain "Post liked" / "Post unliked" |
| GET | `/posts/{id}/comments` | newest first -> `Comment[]` |
| POST | `/posts/{id}/comments` | `application/x-www-form-urlencoded` (or JSON `{content}`): `content`; 201 -> `Comment` |
| DELETE | `/posts/{id}` | only the author (403); text/plain |

## Feed
`GET /feed/{userId}?page=0&size=10` -> `string[]` of post ids, newest first (own posts + posts of your connections).
Only own feed (403). Resolve with `GET /posts?ids=...`. The feed is built asynchronously from Kafka events, so a
brand-new post can take ~1-2 s to show up in *other* people's feeds; after creating a post, insert the returned
`PostResponse` into the local cache instead of refetching.

## Search (Elasticsearch)
`UserDocument = { id, firstName, lastName, headline, location, skills:string[], email, profilePhotoUrl }`
`PostDocument = { id, content, authorId, imageUrl, createdAt }`

| Method | Path | Notes |
|---|---|---|
| GET | `/search/people?q=` | name / headline / location, whole words |
| GET | `/search/skills?skill=` | exact skill keyword |
| GET | `/search/posts?q=` | fuzzy full-text on content |

Indexing is asynchronous (Kafka): new users/posts become searchable after ~1-2 s.

## Notifications (Redis-backed, newest first, max 100 per user)
`Notification = { id, type, title, message, refId, createdAt, read }`
`type` = `WELCOME` | `CONNECTION_REQUEST` | `CONNECTION_ACCEPTED` | `POST_LIKED` | `POST_COMMENTED`
`refId` = id to link to: user id for CONNECTION_* / WELCOME, post id for POST_*

| Method | Path | Notes |
|---|---|---|
| GET | `/notifications` | caller's notifications -> `Notification[]` |
| GET | `/notifications/unread-count` | -> `{ "count": 3 }` |
| POST | `/notifications/read-all` | marks all read; 204 |

## UI routes and test ids (the e2e/Selenium tests rely on these - keep them stable)
Routes: `/login`, `/register`, `/` (feed, protected), `/network`, `/profile/:userId`, `/search?q=...&type=people|posts|skills`, `/notifications`; unknown -> `/`.

`data-testid` values:
* login: `login-form` `login-email` `login-password` `login-submit` `login-error` `go-register`
* register: `register-form` `register-first-name` `register-last-name` `register-email` `register-password` `register-headline` `register-location` `register-submit` `register-error` `go-login`
* navbar: `nav-home` `nav-network` `nav-notifications` `nav-notifications-badge` (only when unread > 0) `nav-me` `nav-logout` `nav-search-form` `nav-search-input`
* feed: `composer-input` `composer-image-input` `composer-submit` `feed-empty` `post-card` `post-author-link` `post-content` `post-like-button` `post-like-count` `post-comment-toggle` `post-comment-count` `post-comment-input` `post-comment-submit` `comment-item` `post-delete-button`
* network: `invitation-card` `invitation-accept` `invitation-reject` `connection-card` `suggestion-card` `suggestion-connect`
* profile: `profile-name` `profile-headline` `profile-about` `profile-skill` `profile-edit-button` `profile-form` `profile-headline-input` `profile-about-input` `profile-location-input` `profile-skills-input` `profile-save` `profile-photo-input` `profile-connect-button` `profile-status`
* search: `search-tab-people` `search-tab-posts` `search-tab-skills` `search-result-person` `search-result-post` `search-empty`
* notifications: `notification-item` `notifications-mark-all-read` `notifications-empty`
* generic: `toast` (every toast), `page-loading`

Seeded account: `admin@example.com` / `password123` (role ADMIN, id `00000000-0000-4000-8000-000000000001`).
