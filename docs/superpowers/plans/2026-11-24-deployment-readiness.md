# Deployment Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gate backend deployment on application startup and database connectivity.

**Architecture:** Expose a status-only Actuator readiness group at `/readyz` on the application port. Require HTTP 200 and `UP` in the SSH deployment check.

**Tech Stack:** Spring Boot Actuator, MySQL, GitHub Actions.

**Spec:** Backend handoff — ARCA, P0.

## Global Constraints

Existing Actuator health and metrics access stays admin-only. Verify browser routing independently of the deployment gate.

## Review Focus

- A running app with a refusing readiness state or broken database must return 503.
- An unrelated HTTP 200 must not pass the deployment gate.
- Unauthenticated health responses must not expose component details.

### Task 1: Readiness gate

- [x] Add `DeploymentReadinessIntegrationTest` covering `GET /readyz` with the real datasource and an application readiness transition; induce an unavailable database connection at the datasource boundary.
- [x] Run `./mvnw -Dtest=DeploymentReadinessIntegrationTest test` and observe the missing route.
- [x] Configure `readinessState,db` in `application.properties`, expose only status, and allow `GET /readyz` in `SecurityConfig`.
- [x] Update `.github/workflows/deploy-backend.yml` to require `200` and `UP`, and document the new gate in `README.md`.
- [x] Run `./mvnw verify`, including the focused test.

### Task 2: Production evidence

- [x] Check the direct browser API path and the frontend same-origin path.
- [x] Check server upload storage with a temporary write, and rehearse the matched backup again in an isolated environment.
- [ ] Verify the app's actual request path and authenticated API access after the first frontend deployment.

**Evidence:** A direct browser request from `https://arca.dcism.org` to the backend `/api/v1/user/me` with `X-Request-ID` returned the expected JSON `401`. The frontend and same-origin route returned `503`; the frontend has not been deployed. DCISM `uploads` is on ext4 and passed a temporary create/delete check. The November 24 paired backup restored into isolated MariaDB 11.8.6; its sole attachment reference resolved to the restored file and matched the recorded byte count.
