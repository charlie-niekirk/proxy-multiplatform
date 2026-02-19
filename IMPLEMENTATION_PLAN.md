# CMP Proxy Desktop Implementation Plan (Compose Multiplatform)

## 1) Product Scope and Constraints (MVP)

Build a **desktop-first** HTTP(S) proxying application inspired by Proxyman/Charles, with a modern Kotlin/Compose architecture:

- **UI framework:** Compose Multiplatform (Desktop target initially)
- **DI:** Metro
- **State management:** Orbit MVI
- **Persistence:** JSON DataStore

### Core MVP capabilities

1. Intercept and inspect HTTP traffic.
2. Optional HTTPS interception through local MITM certificate.
3. Inspect request/response details (headers, body, timings, sizes, protocol/error metadata).
4. Rule-based request/response modification.
5. Certificate onboarding flow for external devices (download endpoint + QR code).

### Explicit behavior decisions

- **HTTPS decryption is OFF by default.**
- Root certificate is **not generated on first launch**; it is generated only when the user explicitly enables SSL decryption.
- The app exposes a local URL path (for proxy clients) such as `http://cmp-proxy/SSL` to download/install the CA certificate.

---

## 2) Project Structure (Nested Modules)

Use Gradle nested modules to keep boundaries clear and future-proof:

- `:app:desktop`
  - Compose desktop app entrypoint, DI bootstrap, navigation shell.
- `:core:model`
  - Domain models (`Session`, `HttpRequest`, `HttpResponse`, `Rule`, `ProxySettings`, `CertificateState`).
- `:core:proxy`
  - HTTP proxy server, CONNECT handling, session capture pipeline, cert endpoint.
- `:core:tls`
  - Root CA generation, host cert issuance, secure key material handling.
- `:core:rules`
  - Matcher engine + transform actions for request/response rewrites.
- `:core:storage`
  - DataStore JSON serializers, repositories, migrations.
- `:feature:sessions`
  - Session list/detail Orbit containers and UI.
- `:feature:rules`
  - Rule CRUD/test UI + Orbit state.
- `:feature:settings`
  - Proxy/SSL settings, certificate onboarding, mobile setup instructions.

> Note: This replaces flat modules like `:feature-*` and `:core-*` with nested modules (`:feature:*`, `:core:*`).

---

## 3) Runtime Architecture

## Proxy flow

1. Accept inbound client request.
2. Build preliminary session context.
3. For HTTP requests: process directly.
4. For HTTPS `CONNECT`:
   - If SSL decryption disabled: tunnel pass-through only.
   - If enabled: perform MITM with generated cert for target host.
5. Apply request rules (pre-upstream).
6. Forward upstream and receive response.
7. Apply response rules (pre-client).
8. Emit incremental session updates to UI and memory store.

## State flow (Orbit MVI)

- `SessionsContainer`: live stream, filters, selection.
- `SessionDetailContainer`: headers/body/metrics/errors/applied rules tabs.
- `RulesContainer`: ordering, enable/disable, validation, test preview.
- `SettingsContainer`: proxy host/port, SSL decryption toggle, certificate status, QR state.

## DI graph (Metro)

Bind clear interfaces:

- `ProxyRuntimeService`
- `TlsService`
- `CertificateDistributionService`
- `SessionRepository`
- `RuleEngine`
- `SettingsRepository`

---

## 4) HTTPS Strategy (Opt-In)

### Default state

- SSL decryption setting = `false` by default.
- Proxy still supports HTTPS tunneling via CONNECT without decryption.

### First-time enable flow

When user turns on SSL decryption:

1. Show consent/explainer dialog.
2. Generate root CA keypair + self-signed certificate.
3. Persist metadata and key paths.
4. Start MITM capability and host cert issuance cache.
5. Display trust-install guidance per platform.

### Disable flow

When user turns SSL decryption off:

- Stop MITM interception immediately.
- Return to pure CONNECT tunneling.
- Keep generated cert for future re-enable unless user explicitly resets/removes it.

---

## 5) Certificate Distribution URL + QR Code

Implement a local, non-TLS endpoint from the proxy runtime:

- `http://cmp-proxy/SSL`

Behavior:

1. If certificate exists, serve certificate download (`application/x-x509-ca-cert` or platform-appropriate content type).
2. If certificate missing, return actionable page/instructions to enable SSL decryption first.
3. Optionally include a simple HTML page with install steps and fingerprint.

### Hostname handling

Support the friendly host `cmp-proxy` by:

- Intercepting this host at proxy layer as an internal route (without upstream forwarding).
- Also exposing fallback direct URL using desktop LAN IP + proxy port for reliability in heterogeneous networks.

### QR code onboarding

In `:feature:settings`:

- Generate QR code for certificate URL(s), e.g.:
  - `http://cmp-proxy/SSL`
  - fallback: `http://<desktop-lan-ip>:<port>/SSL`
- Display both QR and copyable URL.
- Include short instructions for iOS/Android trust steps.

---

## 6) Session Model and Inspection

For each captured transaction:

- Request: method, URL, scheme, authority, headers, body bytes, timestamp.
- Response: status, headers, body bytes, timestamps (first byte/end).
- Metrics: total duration, upload/download sizes, protocol, error cause.
- Rule trace: list of applied rules + mutation summary.

UI capabilities:

- Body viewers: raw text, JSON pretty view, hex preview for binary.
- Header search/filter and copy actions.
- Timing/size panel.

---

## 7) Rule Engine (Configurable Modifications)

Rule schema (DataStore JSON persisted):

- Identity: `id`, `name`, `enabled`, `priority`.
- Scope: request/response/both.
- Matchers: host/path/method/header/body constraints.
- Actions:
  - replace/append request body
  - replace/append response body
  - add/update/remove headers
  - optional status override/mock response (phase 2)

Execution semantics:

- Evaluate in ascending priority.
- Deterministic mutation order.
- Record before/after snippets for observability (size-limited).

---

## 8) Storage and Persistence

Use JSON DataStore files (schema-versioned):

- `settings.json`
  - proxy port, capture toggles, SSL enabled flag, body limits.
- `rules.json`
  - rules list and version.
- `cert.json`
  - fingerprint, createdAt, file paths, status.

Session persistence strategy:

- MVP: in-memory bounded ring buffer.
- Phase 2: optional disk archive/export.

---

## 9) Security and Reliability Baseline

- Never print private key material to logs.
- Enforce max body capture size.
- Redact sensitive headers in exports (`Authorization`, `Cookie`, etc.).
- Graceful degradation when TLS interception fails (continue tunneled mode when configured).
- Internal `/SSL` route should only expose certificate/public metadata, never private key.

---

## 10) Milestones

### Milestone 1 — Foundation

- Create nested modules (`:app:desktop`, `:core:*`, `:feature:*`).
- Setup Metro + Orbit + DataStore wiring.
- Basic shell UI and settings persistence.

### Milestone 2 — HTTP capture

- Implement HTTP proxy capture/forward path.
- Session list/detail UI with headers/body basics.

### Milestone 3 — HTTPS opt-in decryption

- Implement SSL toggle workflow.
- Deferred certificate generation on first enable.
- CONNECT MITM when enabled, tunnel-only when disabled.

### Milestone 4 — Certificate onboarding

- Internal `/SSL` endpoint for certificate delivery.
- QR generation and mobile instructions in settings.

### Milestone 5 — Rules

- Rule CRUD UI + persistence.
- Request/response mutation pipeline with traceability.

### Milestone 6 — Hardening

- Performance tuning for large traffic.
- Better errors/filters/search/export.
- Packaging and installer readiness.

---

## 11) Definition of Done (MVP)

- Desktop app runs as local proxy and captures HTTP sessions.
- HTTPS tunnel pass-through works by default without decryption.
- SSL decryption can be explicitly enabled by user.
- Certificate is generated on demand and downloadable via `/SSL` URL.
- Settings UI displays QR code for mobile certificate onboarding.
- Request/response inspection and rule-based body/header modifications work end-to-end.
- Preferences/rules/cert metadata persist in JSON DataStore.
