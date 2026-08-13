#!/usr/bin/env python3
"""
Driver/request simulator for the dispatch-matching-service.

Generates synthetic drivers with periodic location updates and synthetic
ride requests, hitting the real REST API exactly as a mobile client would.
Used to demonstrate — without real GPS/mobile clients, as permitted by the
project brief:
  - concurrency-safe assignment (many requests racing for few drivers)
  - staleness tolerance (a driver stops sending updates but stays AVAILABLE)
  - automatic reassignment when a driver disappears mid-assignment

Requires only the Python 3 standard library — no pip install needed.

Usage (PowerShell, with the Spring Boot app already running on :8080):
    python simulate.py normal
    python simulate.py concurrent
    python simulate.py disappearing-driver
    python simulate.py stale-location
    python simulate.py continuous      # runs until Ctrl+C — good behind the live dashboard
"""

import json
import random
import sys
import threading
import time
import urllib.error
import urllib.request

BASE_URL = "http://localhost:8080"

# Arbitrary service-area center — requests/drivers are scattered within ~3km of this point.
BASE_LAT = 13.0827
BASE_LNG = 80.2707


def _request(method, path, body=None):
    url = f"{BASE_URL}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                  headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else None)


def jitter(base, spread_km=3.0):
    # ~0.009 degrees latitude per km; good enough for simulated movement.
    deg = spread_km / 111.0
    return base + random.uniform(-deg, deg)


def create_vehicle():
    status, body = _request("POST", "/vehicles", {"type": "SEDAN", "capacity": 4})
    assert status == 201, f"create_vehicle failed: {status} {body}"
    return body["id"]


def create_driver(name, vehicle_id):
    status, body = _request("POST", "/drivers", {"name": name, "vehicleId": vehicle_id})
    assert status == 201, f"create_driver failed: {status} {body}"
    return body["id"]


def update_location(driver_id, lat, lng):
    status, body = _request("POST", f"/drivers/{driver_id}/location",
                             {"latitude": lat, "longitude": lng})
    assert status == 200, f"update_location failed: {status} {body}"
    return body


def update_status(driver_id, status_value):
    status, body = _request("PATCH", f"/drivers/{driver_id}/status", {"status": status_value})
    assert status == 200, f"update_status failed: {status} {body}"
    return body


def create_ride_request(rider_id):
    plat, plng = jitter(BASE_LAT), jitter(BASE_LNG)
    dlat, dlng = jitter(BASE_LAT), jitter(BASE_LNG)
    status, body = _request("POST", "/requests", {
        "riderId": rider_id,
        "pickupLat": plat, "pickupLng": plng,
        "dropoffLat": dlat, "dropoffLng": dlng,
    })
    assert status == 201, f"create_ride_request failed: {status} {body}"
    return body


def find_assignment_for_request(request_id):
    status, body = _request("GET", "/assignments")
    assert status == 200, f"list assignments failed: {status} {body}"
    for a in body:
        if a["requestId"] == request_id:
            return a
    return None


def confirm_assignment(assignment_id):
    return _request("PATCH", f"/assignments/{assignment_id}/confirm")


def make_driver_fleet(n, name_prefix="SimDriver"):
    """Create n AVAILABLE drivers with an initial location. Returns list of driver ids."""
    vehicle_id = create_vehicle()
    driver_ids = []
    for i in range(n):
        did = create_driver(f"{name_prefix}-{i + 1}", vehicle_id)
        update_location(did, jitter(BASE_LAT), jitter(BASE_LNG))
        update_status(did, "AVAILABLE")
        driver_ids.append(did)
    print(f"Created {n} drivers: {driver_ids}")
    return driver_ids


def start_heartbeat(driver_ids, interval_sec=5, stop_event=None, silent_ids=None):
    """Background thread: keep sending location updates for every driver
    except those in silent_ids (used to simulate staleness / disconnection)."""
    silent_ids = silent_ids or set()
    stop_event = stop_event or threading.Event()

    def loop():
        while not stop_event.is_set():
            for did in driver_ids:
                if did in silent_ids:
                    continue
                try:
                    update_location(did, jitter(BASE_LAT), jitter(BASE_LNG))
                except Exception as e:
                    print(f"heartbeat failed for {did}: {e}")
            time.sleep(interval_sec)

    t = threading.Thread(target=loop, daemon=True)
    t.start()
    return stop_event


# ---------------------------------------------------------------------------
# Scenarios
# ---------------------------------------------------------------------------

def scenario_normal():
    """One driver, one request — happy path end to end, including confirm."""
    driver_ids = make_driver_fleet(1)
    stop = start_heartbeat(driver_ids)
    time.sleep(1)

    req = create_ride_request("rider-normal-1")
    print(f"Request {req['id']} status: {req['status']}")

    assignment = find_assignment_for_request(req["id"])
    if not assignment:
        print("No assignment created — check driver availability/staleness.")
        return
    print(f"Matched driver {assignment['driverId']} -> assignment {assignment['id']}")

    status, body = confirm_assignment(assignment["id"])
    print(f"Confirm result: {status} {body}")
    stop.set()


def scenario_concurrent(num_requests=10, num_drivers=3):
    """Fire many requests at once with fewer drivers than requests, to prove
    the atomic claim prevents any driver being double-booked."""
    driver_ids = make_driver_fleet(num_drivers)
    stop = start_heartbeat(driver_ids)
    time.sleep(1)

    results = []
    lock = threading.Lock()

    def fire(i):
        req = create_ride_request(f"rider-concurrent-{i}")
        with lock:
            results.append(req)

    threads = [threading.Thread(target=fire, args=(i,)) for i in range(num_requests)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    time.sleep(1)
    status, assignments = _request("GET", "/assignments")
    driver_hits = {}
    for a in assignments:
        driver_hits.setdefault(a["driverId"], []).append(a["id"])

    print(f"{num_requests} concurrent requests, {num_drivers} drivers.")
    for did, assignment_ids in driver_hits.items():
        flag = "  <-- DOUBLE-BOOKED!" if len(assignment_ids) > 1 else ""
        print(f"  driver {did}: {len(assignment_ids)} assignment(s){flag}")
    stop.set()


def scenario_disappearing_driver():
    """Driver gets reserved, then goes silent and never confirms.
    Watch the request sit at DRIVER_RESERVED, then get reassigned once the
    60s timeout scheduler reaps it (polling every 5s)."""
    driver_ids = make_driver_fleet(2)
    stop = start_heartbeat(driver_ids)
    time.sleep(1)

    req = create_ride_request("rider-vanish-1")
    assignment = find_assignment_for_request(req["id"])
    if not assignment:
        print("No assignment created.")
        return
    vanished_driver = assignment["driverId"]
    print(f"Driver {vanished_driver} reserved for request {req['id']} — going silent now (never confirming).")

    stop.set()  # stop ALL heartbeats; simplest way to simulate the pool going quiet.
    survivors = [d for d in driver_ids if d != vanished_driver]
    start_heartbeat(survivors)  # everyone except the vanished driver keeps reporting in

    print("Waiting for the 60s assignment expiry + 5s scheduler poll to reap this...")
    print(f"Poll GET {BASE_URL}/requests/{req['id']} and GET {BASE_URL}/assignments to watch it happen live.")


def scenario_stale_location():
    """One driver whose location goes stale (no updates) past the 180s
    threshold, then a request is submitted — driver should be excluded."""
    driver_ids = make_driver_fleet(1)
    # Deliberately do NOT start a heartbeat — location is only set once, at creation.
    print(f"Driver {driver_ids[0]} location set once, then left stale (no heartbeat).")
    print("Waiting 185s past the 180s staleness threshold before submitting the request...")
    time.sleep(185)
    req = create_ride_request("rider-stale-1")
    print(f"Request {req['id']} status: {req['status']}  (expect NO_DRIVER_AVAILABLE)")


def scenario_match_comparison(num_drivers=5):
    """Creates several drivers at varying distances, submits one request,
    then calls GET /requests/{id}/match-comparison and prints both the
    'speed' (nearest-first) and 'quality' (ETA/traffic re-ranked) orderings
    side by side. Read-only — no driver is actually claimed by this call,
    only by the create_ride_request() match that already ran."""
    driver_ids = make_driver_fleet(num_drivers)
    stop = start_heartbeat(driver_ids)
    time.sleep(1)

    req = create_ride_request("rider-compare-1")
    status, comparison = _request("GET", f"/requests/{req['id']}/match-comparison")
    assert status == 200, f"match-comparison failed: {status} {comparison}"

    print(f"\nRequest {req['id']} — speed order (nearest first):")
    for c in comparison["speedOrder"]:
        print(f"  #{c['rank']}  driver {c['driverId']}  {c['distanceKm']:.2f} km")

    print(f"\nRequest {req['id']} — quality order (ETA/traffic re-ranked, "
          f"top {len(comparison['qualityOrder'])} shortlisted):")
    for c in comparison["qualityOrder"]:
        print(f"  #{c['rank']}  driver {c['driverId']}  {c['distanceKm']:.2f} km  "
              f"eta={c['etaMinutes']:.1f}min  traffic={c['trafficLevel']}")

    print(f"\nTop pick differs between strategies: {comparison['topPickDiffers']}")
    stop.set()


def scenario_continuous(num_drivers=6, request_interval_sec=4):
    """Keeps a driver fleet moving and a steady stream of requests flowing —
    meant to be left running behind the live dashboard during the demo."""
    driver_ids = make_driver_fleet(num_drivers)
    start_heartbeat(driver_ids, interval_sec=5)
    print("Continuous simulation running. Ctrl+C to stop.")
    i = 0
    try:
        while True:
            i += 1
            req = create_ride_request(f"rider-cont-{i}")
            print(f"[{i}] request {req['id']} -> {req['status']}")
            time.sleep(request_interval_sec)
    except KeyboardInterrupt:
        print("Stopped.")


SCENARIOS = {
    "normal": scenario_normal,
    "concurrent": scenario_concurrent,
    "disappearing-driver": scenario_disappearing_driver,
    "stale-location": scenario_stale_location,
    "match-comparison": scenario_match_comparison,
    "continuous": scenario_continuous,
}


if __name__ == "__main__":
    name = sys.argv[1] if len(sys.argv) > 1 else "normal"
    if name not in SCENARIOS:
        print(f"Unknown scenario '{name}'. Options: {', '.join(SCENARIOS)}")
        sys.exit(1)
    SCENARIOS[name]()
