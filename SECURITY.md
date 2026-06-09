# Parkomfy Security Notes

## Implemented (academic prototype level)

- **Password hashing:** BCrypt via `spring-security-crypto` (`AuthService`). Legacy SHA-256 hashes are still accepted for migration.
- **Admin API protection:** `/api/v1/admin/*` requires `X-Auth-Token` from a successful ADMIN login (`AdminAuthFilter`).
- **Secrets:** DB credentials and admin seed password read from environment variables with safe defaults for local demo:
  - `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`
  - `PARKOMFY_ADMIN_PASSWORD` (default `1234`)
- **KVKK:** Public `GET /api/v1/legal/kvkk` disclosure text; registration requires explicit consent in the mobile app.

## Not implemented (out of scope for demo)

- **TLS/HTTPS:** Server and mobile client use plain HTTP/WS on the LAN. Production would terminate TLS at a reverse proxy or enable `server.ssl.*`.
- **Full authorization:** Only admin routes are guarded. User-specific endpoints (reservations, sessions) are not token-scoped yet.
- **Production hardening:** Rate limiting, audit logs, refresh tokens, password reset, and penetration testing are future work.

## Report "Limitations" snippet

> Security measures include BCrypt password storage, admin token guards, environment-based secrets, and KVKK consent at registration. The prototype runs over HTTP on a local network; HTTPS, full role-based access control, and production-grade KVKK data-retention automation are identified as future work.
