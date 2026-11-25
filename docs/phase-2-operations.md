# Phase 2 — Resource and media hardening

## Release contents

This release centralizes post authorization, validates bounded uploads, secures stored-media paths and download headers, makes avatar replacement transactional, validates JWT/CORS configuration at startup, and normalizes security/validation errors. It needs no database migration beyond the existing Phase 1 V1–V3 schema.

Existing routes, successful payloads, roles, access-token claims, refresh sessions, row IDs and logical post IDs are retained. Roll out after Phase 1. The application is still Spring MVC → services → JPA/MySQL.

## Visibility and mutation policy

| Parent post state | Anonymous | Owner | Other authenticated user | Admin |
| --- | --- | --- | --- | --- |
| Active APPROVED | 401 | Read | Read | Read |
| Active PENDING_APPROVAL / REJECTED / SUPERSEDED | 401 | Read | 404 | Read |
| Deleted or missing | 401 | 404 | 404 | 404 |

- Apply this matrix to direct post reads, attachment metadata/downloads, vote counts/current vote, and vault operations. All APIs in this table continue to require JWT authentication.
- Post history returns only individually readable versions. Missing, deleted or wholly inaccessible history returns `[]`; owners/admins retain active moderation/history visibility.
- Version creation, attachment uploads and deletion require owner/admin. A forbidden mutation on a visible post returns 403; a guessed inaccessible post returns 404. Authorized repeated logical-post deletion remains 204 and preserves original deletion provenance.
- New votes require APPROVED status even for owners/admins. Other readable statuses return 400; inaccessible parents return 404. The actor can only change their own vote.
- Vault entries belong to their saving user, including when an administrator guesses another user's entry ID. Lists hide entries whose parent is no longer readable without deleting retained rows. Duplicate saves return JSON 409.
- `user_id` in post creation or multipart attachment upload is ignored and deprecated in OpenAPI. Ownership comes from the authenticated principal.

## Upload and storage controls

| Upload | Allowed formats | Limits |
| --- | --- | --- |
| Post attachments | JPEG (`.jpg`, `.jpeg`), PNG, PDF | Three per version; 10 MiB per file and total file payload |
| Avatar | JPEG, PNG, WebP | One current avatar; 10 MiB per file |
| Decoded images | All supported image uploads | At most 25,000,000 pixels per image |
| HTTP multipart request | All uploads | 10 MiB including multipart overhead |

The HTTP request limit includes boundaries and form fields, so a file exactly 10 MiB cannot fit in a 10 MiB multipart request. Service-level limits also apply to callers that bypass multipart parsing. Unsupported extensions/types return 415; empty, malformed or mismatched content returns 400; byte-limit failures return 413. Extension, nonempty declared MIME and detected content must agree. PNG/JPEG/WebP are decoded after checking dimensions; the TwelveMonkeys ImageIO WebP reader preserves existing avatar support. PDF validation checks the PDF signature; it is not a malware scan or a document-safety guarantee.

Uploads use generated UUID storage names. Display filenames are reduced to bounded ASCII basenames; downloaded legacy names receive the same sanitation. Attachment downloads use `Content-Disposition: attachment`, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`. Avatars retain public inline reads with no-store/nosniff headers.

`file.upload-dir` remains the storage root. Attachment paths must resolve under its `posts/` subtree, avatars under `profile-pictures/`. A configured root may itself be a deployment symlink; symlinks beneath that root are rejected on reads and writes. Only the application service account and trusted operators should be able to modify storage directories: path validation does not make concurrent hostile filesystem mutation safe. Keep the root and database together in backups and persistent mounts.

Attachment validation finishes for the entire batch before writing files. Database rollback removes newly written objects. Avatar replacements lock the user row, save a new object/reference, delete the old object only after commit, and remove the new object on rollback. User scalar updates write only changed columns, so a profile edit loaded before avatar replacement cannot restore the stale, deleted avatar reference. Concurrent changes to the same scalar field retain last-writer semantics. Filesystem cleanup is best effort and logs failures; process crashes or unavailable storage can leave orphan objects. Reconcile against database references before any operator cleanup. Post soft deletion continues to retain attachments indefinitely; this release adds no purge operation.

Post revisions can share a stored attachment path. When reconciling storage, count references from all revisions, including superseded and soft-deleted rows, before removing any object. A path can remain in use even when the matching post revision ID is not current.

## Startup configuration

- `SECRET_KEY` / `jwt.secret` must be valid Base64 decoding to at least 32 secret bytes. Startup fails for missing, malformed or weak values without logging the value. Keep the existing valid secret during rollout so issued access and refresh JWTs remain usable; no key rotation is required.
- `app.cors.allowed-origins` must contain explicit HTTP(S) origins, without wildcards, credentials, paths, query strings or fragments. Empty origin lists fail startup.
- Development/test profiles preserve explicit HTTP development origins. Outside those profiles, remote origins require HTTPS; loopback HTTP remains usable. Explicit `prod` or `production` activates strict validation even when combined with `dev`.
- Existing datasource/JWT environment bindings live in `application-dev.yml`. Deployments using those bindings can set `SPRING_PROFILES_ACTIVE=dev,prod` to retain them while activating production origin validation, and `APP_CORS_ALLOWED_ORIGINS=https://arca.dcism.org` for the intended browser client. Deployments using a dedicated production profile should configure the equivalent datasource and JWT properties there.
- Spring's CORS filter owns preflight validation. The JWT filter no longer reflects arbitrary origins or manufactures permission headers.

## Errors and compatibility checks

MVC and JWT/security-filter errors use `timestamp`, numeric `status`, `error`, and `message`. Existing security identifiers (`unauthorized`, `forbidden`) remain stable. No stack traces, signing keys or database failure messages are exposed in generic 500 responses. Legacy missing vault-entry and public-avatar reads retain empty 404 bodies; CORS rejections remain Spring's preflight 403 response.

Updated post/vote/vault request schemas explicitly use their existing snake_case wire names: `post_id`, `vote_type`, `new_label`, `user_id`, `department_id` and `post_tag`. Contract verification submits requests constructed using the generated property names through MVC, in addition to checking generated/checked-in parity. This corrects documentation; it does not introduce a camelCase wire contract.

Before rollout, run `./mvnw --batch-mode verify` and the README's pinned OpenAPI lint command. Tests exercise MySQL-backed status/actor matrices, IDOR and spoofed ownership, private-history filtering, upload rollback, avatar commit/rollback, canonical/symlink containment, byte/pixel bounds, safe headers, invalid configuration, and pre-rollout JWT acceptance.

The contract test can export the generated contract with `./mvnw --batch-mode test -Dtest=OpenApiContractTest -Darca.openapi.export=true`; inspect `target/openapi-generated.json` against `openapi.yaml`. The export is produced before drift assertions, allowing a contract update to be reviewed while the check is failing. Preserve the checked-in server URLs and API info when updating generated operations.

Observe startup validation, 400/401/403/404/409/413/415 rates, and media cleanup warnings after release. Never bypass origin validation with `*`, weaken the signing key, or point public media reads at the general attachment tree to resolve a client failure. Existing Phase 1 deletion correlation logs and metrics remain available.
