# Phase 6 — User cover images

## HTTP contract

`/api/v1/user/{userId}/cover-picture` supports:

| Method | Access | Result |
| --- | --- | --- |
| PUT | Owner or ROLE_ADMIN | Multipart `file`; 200 JSON `user_id`, `cover_picture`, `message` |
| GET | Public, matching avatars | Binary JPEG/PNG/WebP; 404 for missing/deleted user, missing reference or unavailable object |
| DELETE | Owner or ROLE_ADMIN | 200 JSON message; idempotent when an existing user has no cover |

Anonymous writes return 401; another ordinary user's writes return 403 without storage work. Missing/deleted target on authorized writes returns 404. Malformed/nonpositive IDs return 400. `UserResponse` appends optional read-only `cover_picture`, omitted when absent. It is a relative storage key, not an absolute filesystem path or a URL; fetch bytes through the GET endpoint. Narrow profile PATCH/PUT requests cannot set a storage key. Search summaries remain lightweight. No default/static frontend cover is synthesized.

Successful binary reads send `Content-Type` from the validated stored extension, `Content-Disposition: inline; filename="cover-picture"`, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. Existing avatar endpoints and cache behavior remain compatible. Clients should refetch after writes/removal.

## Validation and storage

Covers reuse avatar image validation: JPEG, PNG and WebP only, MIME/extension/decoded-image agreement, full image decode, maximum 10 MiB actual bytes and 25,000,000 decoded pixels. Empty/spoofed/malformed images and excessive decoded pixels return 400, unsupported types/extensions 415, oversized bytes/requests 413. Servlet request and file limits are both 10 MiB; multipart framing means an HTTP request near the file ceiling may be rejected by the request limit. Original names do not choose disk paths: UUID filenames live exclusively under `cover-pictures/` within `file.upload-dir`. Upload errors must not leave a persisted key or new orphan.

Avatar and cover replacement share lifecycle helpers while retaining separate entity fields/subtrees. A user row lock serializes image replacement/removal with account-locking operations. New objects are written before flushing the reference. Commit triggers old-object cleanup; rollback removes the new object while keeping the old reference/object. Removal clears the key transactionally and deletes its object only after commit. Stale unrelated profile updates retain `@DynamicUpdate`, so they cannot restore a removed key. Missing files and corrupt keys can still be cleared; containment rejects reads/deletions targeting avatars, attachments or paths outside the cover subtree.

Filesystem and database are not a distributed transaction: process termination or failed best-effort post-commit deletion can leave an orphan. Cleanup failures log a content-free warning. Reconcile old unreferenced objects using an operator-reviewed age window and current database references; never delete files for in-flight transactions. Keep storage writable only by the application and trusted operators. Configured root symlinks are supported; symlinks below the root are rejected. Concurrent hostile filesystem mutation is outside this trusted-directory model. Back up database and persistent media together.

## V7 rollout

V7 adds nullable `users.cover_picture VARCHAR(255)`; existing users receive no cover, and existing avatar/password/session/timestamp values remain intact. V1–V6 checksums remain unchanged. Before release, rehearse V6→V7 on a restored database, inspect column conflicts and DDL lock budget, back up database/media, and ensure upload storage persists across application restarts. Apply V7 before deploying cover-aware binaries (`ddl-auto=validate`). Old binaries tolerate the added nullable column. A binary rollback can leave cover data/files for later reuse; avoid destructive down-migrations.

Release verification: full Maven verify, OpenAPI parity and pinned Redocly lint; clean/baselined/V6-upgrade migration tests; owner/admin/other/anonymous write matrix and public reads; validation/storage isolation; replacement/removal rollback; forced database failure; stale profile race; missing objects; avatar compatibility. Exercise upload/read/replace/remove against the packaged application and disposable MySQL using the production JDBC driver before rollout.
