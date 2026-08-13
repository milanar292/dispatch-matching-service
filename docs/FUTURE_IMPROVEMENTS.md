# Future Improvements — Dispatch Matching Service

## Fixed since earlier drafts

- **Trip completion lifecycle**: earlier notes assumed a driver stays `BUSY`
  indefinitely after a trip with no way to free them. This is resolved —
  `AssignmentController` exposes both `PATCH /assignments/{id}/confirm` and
  `PATCH /assignments/{id}/complete`, and the full lifecycle (reserve →
  confirm → complete → driver auto-recycles to `AVAILABLE`) is verified
  working end-to-end.

## Known limitations / deferred

- **Driver-lookup scalability**: candidate matching uses
  `driverRepository.findByStatus(AVAILABLE)` — a full table scan, with
  haversine distance computed in application code. No spatial index or
  bounding-box pre-filter. Fine at current scale; would need a spatial
  index (PostGIS) or a geohash/H3 bucketing scheme to stay fast with a
  large driver pool.
- **Confirmation-vs-timeout race under sustained load**: the same-driver
  concurrent-claim race is covered by an automated integration test
  (`onlyOneThreadCanReserveTheSameAvailableDriver`), and the specific
  confirm-vs-timeout race was manually verified in an earlier session,
  but it isn't yet a dedicated automated test. Next step would be a test
  that fires a confirm and a timeout-reap at the same instant and asserts
  only one wins.
- **No structured error response body**: errors currently return a bare
  404/409 with no JSON error schema (`{ "error": ..., "message": ... }`).
  Noted in the API docs as a known gap.
- **Ephemeral test database**: integration tests run against the local
  Postgres instance rather than Testcontainers (no Docker available in
  this environment). Would move to Testcontainers for CI-safe, parallel,
  guaranteed-clean test runs.
- **Location staleness is binary, not tiered**: drivers past a single
  180s threshold are excluded outright rather than being penalized on a
  sliding scale (e.g. FRESH / SLIGHTLY_STALE / TOO_STALE).

## If given more time

- Real traffic-aware routing provider in place of the mock
- WebSocket-based live location/status updates instead of polling
- Spatial indexing (PostGIS or H3) for driver lookup at scale
- Structured error responses across all endpoints
- Testcontainers-based CI test suite
