# API / Interface Definitions — Dispatch Matching Service

Base URL (local): `http://localhost:8080`

All request/response bodies are JSON. All identifiers are UUIDs (string form in JSON). Timestamps are ISO-8601 (`LocalDateTime`, no timezone offset).

---

## Enums

**DriverStatus**: `AVAILABLE`, `RESERVED`, `BUSY`, `OFFLINE`, `DISCONNECTED`

**RequestStatus**: `REQUESTED`, `SEARCHING`, `DRIVER_RESERVED`, `ASSIGNMENT_SENT`, `DRIVER_CONFIRMED`, `COMPLETED`, `NO_DRIVER_AVAILABLE`, `REMATCHING`, `CANCELLED`

**AssignmentStatus**: `RESERVED`, `ASSIGNMENT_SENT`, `CONFIRMED`, `TIMED_OUT`

---

## Vehicles

### `POST /vehicles`
Create a vehicle.

**Request body**
```json
{
  "type": "SEDAN",
  "capacity": 4
}
```

**Response** — `201 Created`
```json
{
  "id": "uuid",
  "type": "SEDAN",
  "capacity": 4
}
```

### `GET /vehicles`
List all vehicles.

**Response** — `200 OK`
```json
[
  { "id": "uuid", "type": "SEDAN", "capacity": 4 }
]
```

---

## Drivers

### `POST /drivers`
Register a driver, linked to an existing vehicle.

**Request body**
```json
{
  "name": "Driver X",
  "vehicleId": "uuid"
}
```

**Response** — `201 Created`
```json
{
  "id": "uuid",
  "name": "Driver X",
  "status": "OFFLINE",
  "latitude": null,
  "longitude": null,
  "locationUpdatedAt": null
}
```
*(Initial `status`/location fields reflect default entity state at creation — no location has been posted yet.)*

### `GET /drivers/{id}`
Fetch a single driver.

**Response** — `200 OK`
```json
{
  "id": "uuid",
  "name": "Driver X",
  "status": "AVAILABLE",
  "latitude": 12.97,
  "longitude": 77.59,
  "locationUpdatedAt": "2026-08-12T12:00:00"
}
```

### `GET /drivers`
List all drivers.

**Response** — `200 OK` — array of the object shown above.

### `POST /drivers/{id}/location`
Push a location update for a driver. Refreshes `locationUpdatedAt` to now, which resets the staleness clock used by matching (drivers with a `locationUpdatedAt` older than 180s are excluded from candidate selection even if `AVAILABLE`).

**Request body**
```json
{
  "latitude": 12.97,
  "longitude": 77.59
}
```

**Response** — `200 OK` — updated `DriverResponse` (same shape as `GET /drivers/{id}`).

### `PATCH /drivers/{id}/status`
Manually set a driver's status (e.g. going `OFFLINE`, or an operator override).

**Request body**
```json
{
  "status": "OFFLINE"
}
```

**Response** — `200 OK` — updated `DriverResponse`.

---

## Ride Requests

### `POST /requests`
Submit a new ride request. Matching is triggered as part of request creation (see `MatchingService`).

**Request body**
```json
{
  "riderId": "rider-1",
  "pickupLat": 12.97,
  "pickupLng": 77.59,
  "dropoffLat": 13.00,
  "dropoffLng": 77.60
}
```

**Response** — `201 Created`
```json
{
  "id": "uuid",
  "riderId": "rider-1",
  "status": "DRIVER_RESERVED",
  "pickupLat": 12.97,
  "pickupLng": 77.59,
  "dropoffLat": 13.00,
  "dropoffLng": 77.60,
  "createdAt": "2026-08-12T12:00:00"
}
```
*(`status` will be `DRIVER_RESERVED` if a driver was successfully matched, or `NO_DRIVER_AVAILABLE` if no eligible candidate was found.)*

### `GET /requests/{id}`
Fetch a single ride request, including its current status.

**Response** — `200 OK` — same shape as above.

---

## Assignments

An assignment links one ride request to one driver, with a reservation expiry.

### `GET /assignments`
List all assignments.

**Response** — `200 OK`
```json
[
  {
    "id": "uuid",
    "requestId": "uuid",
    "driverId": "uuid",
    "status": "RESERVED",
    "reservedAt": "2026-08-12T12:00:00",
    "confirmedAt": null,
    "expiresAt": "2026-08-12T12:02:00",
    "failureReason": null
  }
]
```

### `GET /assignments/{id}`
Fetch a single assignment.

**Response** — `200 OK` — same shape as above.

### `PATCH /assignments/{id}/confirm`
Driver confirms the assignment before it expires.

**Response** — `200 OK`
```json
{
  "id": "uuid",
  "requestId": "uuid",
  "driverId": "uuid",
  "status": "CONFIRMED",
  "reservedAt": "2026-08-12T12:00:00",
  "confirmedAt": "2026-08-12T12:01:10",
  "expiresAt": "2026-08-12T12:02:00",
  "failureReason": null
}
```

**Response** — `409 Conflict` — if the assignment is already expired or not in a confirmable state (`IllegalStateException` mapped to 409). No response body schema is currently defined for the error case (see Future Improvements: no structured error DTO yet).

---

## Notes for the discussion round

- All "create" endpoints return `201 Created`; all "read"/"update" endpoints return `200 OK`.
- Error handling is currently minimal: `NotFoundException` and `IllegalStateException` are the two exception types thrown by services (mapped to `404`/`409` respectively at the framework level), but there is no unified error response body (e.g. `{ "error": "...", "message": "..." }`) — this is called out explicitly in Future Improvements.
- Matching is synchronous and embedded inside `POST /requests` — there is no separate "trigger matching" endpoint; a request is matched (or marked `NO_DRIVER_AVAILABLE`) as part of the same HTTP call that creates it.
- Reassignment after a timeout happens entirely server-side via the 5-second `@Scheduled` `ReassignmentScheduler` — there is no client-facing endpoint for it; it surfaces to clients only as a new `Assignment` appearing (visible via `GET /assignments`) or a request's status changing.
