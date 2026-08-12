# Test Plan & Implementation — Dispatch Matching Service

## Approach

Testing focused on the properties the brief marked interview-critical: **concurrency-safe claiming**, **staleness tolerance**, **timeout + automatic reassignment**, and **rejection of stale confirmations**. Rather than isolated unit tests with mocked repositories, these are written as Spring Boot integration tests (`@SpringBootTest`) running against a real PostgreSQL instance with the actual Flyway-migrated schema — because the properties being tested (row-level locking, transaction boundaries, bulk-update visibility) are exactly the kind of behavior that mocks would hide or fake incorrectly.

**Test isolation tradeoff:** these tests run against the local PostgreSQL instance (same one used for development), not an ephemeral per-run database. No Docker Desktop was available in this environment to run Testcontainers. Each test cleans up its own data in `@AfterEach`. In a real CI pipeline this would instead use Testcontainers (or an equivalent ephemeral DB) so tests can run in parallel, on any machine, with a guaranteed clean slate — this is called out explicitly in Future Improvements.

**Location:** `src/test/java/com/innovinlabs/dispatch_service/service/DispatchMatchingIntegrationTest.java`

**Result:** 5/5 tests passing.

---

## Test 1 — Concurrency-safe claiming

**`onlyOneThreadCanReserveTheSameAvailableDriver`**

Two threads race to claim the same `AVAILABLE` driver simultaneously via `DriverRepository.tryReserve()`, using a `CountDownLatch` to force both threads to start their attempt at the same instant (rather than relying on incidental timing). Asserts that exactly one of the two attempts succeeds (`result1 + result2 == 1`) and that the driver ends up `RESERVED`, not double-assigned.

This test is deliberately **not** `@Transactional` at the test level — the two threads need genuinely separate transactions/connections to race for the same database row the way two concurrent HTTP requests would in production. A `@Transactional` test method would share one connection across both threads, which would not actually exercise the race condition.

**What this proves:** the atomic, conditional `UPDATE ... WHERE status = 'AVAILABLE'` in `tryReserve()` — not application-level locking — is what prevents double-assignment under concurrent load.

---

## Test 2 — Staleness tolerance

**`staleDriverIsExcludedFromMatchingEvenIfMarkedAvailable`**

Creates two `AVAILABLE` drivers: one with a fresh location (`now()`), one with a location timestamp 400 seconds old (past the 180-second `MAX_LOCATION_STALENESS_SECONDS` threshold in `MatchingService`). Submits a ride request and runs matching. Asserts only the fresh driver gets matched, and the stale driver — despite still being marked `AVAILABLE` — is left untouched.

**What this proves:** a driver's `status` field alone isn't trusted as "reachable now" — `locationUpdatedAt` is checked independently, so a driver whose app crashed or lost connectivity mid-shift doesn't get dispatched on a last-known position that's no longer reliable.

---

## Test 3 — Timeout & automatic reassignment

**`expiredAssignmentIsReapedAndDriverIsFreedAndRematched`**

Matches a request to a driver (creating a `RESERVED` assignment), then forces that assignment's `expiresAt` into the past to simulate a real 120-second timeout without waiting for it. Calls `reassignmentScheduler.reapExpiredAssignments()` directly. Asserts the original assignment flips to `TIMED_OUT` with a `failureReason` set, that a **second** fresh `RESERVED` assignment gets created for the same still-unfulfilled request, and that the driver ends up `RESERVED` again (re-matched, since it's still the nearest fresh candidate).

**What this proves:** the reap → free driver → re-trigger matching pipeline works end-to-end, matching the behavior verified live in manual testing (where a driver correctly cycled through repeated timeout/reassignment attempts before eventually being excluded once its own location went stale).

This test surfaced two real bugs during implementation — see **Bugs Found & Fixed** below.

---

## Test 4 — Rejecting an already-expired confirmation

**`confirmingAnAlreadyExpiredAssignmentThrows`**

Creates an assignment with `expiresAt` already in the past, then calls `assignmentService.confirm()` on it. Asserts an `IllegalStateException` is thrown (mapped to `409 Conflict` at the controller level).

**What this proves:** a driver can't confirm a reservation that's already timed out and potentially been reassigned to someone else — closing the race between "driver taps confirm" and "scheduler reaps the assignment" in the scheduler's favor once expiry has passed.

---

## Bugs Found & Fixed

Writing these tests surfaced three real bugs that manual/live testing hadn't caught, all related to the boundary between JPA's in-memory object model and the database:

### 1. Missing `@Transactional` on a bulk `@Modifying` query
`DriverRepository.tryReserve()` is a `@Modifying` JPQL bulk update. Called from a worker thread with no surrounding transaction (as in Test 1's two-thread setup), Spring had nothing to attach the update to, throwing `InvalidDataAccessApiUsageException: No active transaction for update or delete query`. **Fix:** added `@Transactional` directly on the repository method.

### 2. Stale reads after a bulk update (flush ordering)
Even after fixing (1), Test 3 initially failed: the reassignment scheduler's `driver.setStatus(AVAILABLE)` was pending in the persistence context but not yet flushed to the database when the bulk `UPDATE` inside `tryReserve()` ran — so the bulk update's own `WHERE status = 'AVAILABLE'` clause saw the driver's *old* database value and matched nothing. **Fix:** added `@Modifying(clearAutomatically = true, flushAutomatically = true)` so pending changes are flushed before the bulk update executes.

### 3. Stale detached entity after `clearAutomatically`
Fixing (2) introduced a new, subtler bug: `clearAutomatically = true` detaches every entity in the persistence context after the bulk update runs — including the `Driver` object already referenced by an in-flight `Assignment`. The scheduler was reading driver state via `assignment.getDriver()`, which returned that now-stale, detached object showing the driver's status *before* the reservation rather than the current database truth. **Fix:** the scheduler now re-fetches the driver fresh via `driverRepository.findById(...)` instead of trusting the association traversal.

This chain — fix a transaction bug, which surfaces a flush-ordering bug, which surfaces a stale-entity bug — is a good illustration of why these were written as real integration tests against a real database rather than mocked: none of the three would have been caught by a unit test with a mocked repository.

---

## Not covered by automated tests (manual/live verification only)

- Full end-to-end dashboard flow (create driver → update location → submit request → confirm) — verified manually via `dashboard.html`, not asserted in an automated test.
- The specific live scenario where a driver's `locationUpdatedAt` itself goes stale after two reassignment cycles (never refreshed while `RESERVED`/`BUSY`), correctly causing a third attempt to find zero candidates and mark the request `NO_DRIVER_AVAILABLE`. This was observed and understood during manual testing but not written as a dedicated automated test given time constraints — noted in Future Improvements.
