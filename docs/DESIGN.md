Design Document — Dispatch Matching Service
Day 1: Initial Architecture Submission
1. Problem Interpretation
This is a concurrent resource-allocation problem, not simply a "find the nearest driver" lookup.
Drivers are scarce and mutable resources. Requests can arrive concurrently, driver availability can change between matching and assignment, and driver location is a potentially stale snapshot rather than ground truth.
The system therefore has three priorities:
1.	Correctness: never double-allocate a driver.
2.	Liveness: a request must not remain stuck indefinitely.
3.	Matching quality: select a driver using ETA, traffic, freshness, and other useful signals.
Matching optimization is deliberately layered on top of the correctness guarantees.
________________________________________
2. Architecture
The proposed architecture is a modular monolith consisting of one Spring Boot application and one PostgreSQL database.
                    Driver Simulator
                          │
                          ▼
                     Driver API
                          │
                          ▼
┌──────────────┐    ┌─────────────────┐
│ Request API  │───►│ Dispatch Service│
└──────────────┘    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
       Candidate Finder  Routing Service  Assignment
              │              │            Manager
              └──────────────┼──────────────┘
                             ▼
                       PostgreSQL
Module structure
controller/  → REST endpoints and DTO handling
service/     → DispatchService, DriverService, AssignmentService,
               RoutingService, ReassignmentService
repository/  → persistence operations
entity/      → Driver, Vehicle, RideRequest, Assignment
dto/         → API request/response models
exception/   → domain exceptions and global error handling
config/      → configurable thresholds and scoring weights
simulation/  → standalone driver/request simulation
Why a modular monolith?
The project does not require Kafka, Kubernetes, or independently deployed services.
More importantly, keeping allocation state in one transactional PostgreSQL database makes the core concurrency guarantee easier to reason about, implement, and test.
A distributed architecture would introduce additional coordination problems without providing a necessary benefit for this project scale.
________________________________________
3. Core Entities and State Machines
Driver
Possible states:
AVAILABLE
RESERVED
BUSY
OFFLINE
DISCONNECTED
RideRequest
REQUESTED
   ↓
SEARCHING
   ↓
DRIVER_RESERVED
   ↓
ASSIGNMENT_SENT
   ↓
DRIVER_CONFIRMED
   ↓
COMPLETED
Failure paths include:
ASSIGNMENT_SENT
      ↓
   TIMEOUT
      ↓
 REMATCHING
and:
SEARCHING
    ↓
NO_DRIVER_AVAILABLE
Assignment
The assignment is a first-class entity rather than simply a driverId attached to a request.
RESERVED
   ↓
ASSIGNMENT_SENT
   ↓
CONFIRMED

or

ASSIGNMENT_SENT
   ↓
TIMED_OUT
This provides a natural place to store:
•	reservedAt
•	expiresAt
•	confirmation state
•	failure reason
•	reassignment information
________________________________________
4. Concurrency-Safe Assignment
Race condition
Two requests can simultaneously select the same driver:
Request A ──┐
            ├── Driver D1
Request B ──┘
If matching uses separate read and write operations:
Request A → SELECT D1 → AVAILABLE
Request B → SELECT D1 → AVAILABLE

Request A → UPDATE → RESERVED
Request B → UPDATE → RESERVED
both requests may believe they successfully claimed D1.
Chosen solution
Use a single atomic conditional update:
UPDATE driver
SET status = 'RESERVED'
WHERE id = :id
AND status = 'AVAILABLE';
The service checks the affected-row count:
1 row → claim succeeded
0 rows → driver was already claimed or changed state
If the claim fails, the service immediately attempts the next-ranked candidate.
The important point is that the ranking algorithm does not provide the concurrency guarantee. The database state transition does.
Alternative: SELECT FOR UPDATE
SELECT FOR UPDATE can also solve the problem, but locking every driver in a shortlist of 5–8 candidates can introduce unnecessary lock contention.
The conditional update locks only the driver actually being claimed.
Alternative: optimistic locking
JPA optimistic locking with @Version could detect a conflicting update, but the conflict would be discovered after the matching attempt has already proceeded.
The conditional update provides a simple claim operation and naturally supports the "try the next candidate" fallback.
________________________________________
5. Matching Algorithm
Matching uses two stages.
Stage 1: Geographic filtering
The system first filters:
•	Driver status
•	Vehicle eligibility
•	Location freshness
•	Geographic proximity
A bounding-box filter and Haversine distance can be used to cheaply identify nearby drivers.
Approximately 5–8 candidates are shortlisted.
Stage 2: ETA-aware scoring
Only shortlisted candidates proceed to routing/ETA evaluation.
The score considers:
ETA
Traffic
Distance
Location freshness
Vehicle compatibility
Request scarcity
Waiting time
This avoids calculating expensive routing information for every available driver.
Matching quality vs speed
Evaluating every driver may increase matching quality because a geographically distant driver could potentially have a much better route.
However, evaluating every driver also increases latency and routing-service load.
The two-stage approach deliberately trades a small possibility of missing an ETA-favorable outlier for lower matching latency.
The shortlist size is configurable.
________________________________________
6. Traffic-Aware ETA
The matching engine uses a RoutingService abstraction.
RoutingService
      │
      ├── MockRoutingService
      │       └── deterministic simulated traffic
      │
      └── RealRoutingService
              └── future external provider
The initial implementation uses simulated traffic rather than an external routing API.
Example traffic factors:
LOW       → 1.0×
MODERATE  → 1.25×
HIGH      → 1.6×
The system does not scrape Google Maps traffic visualization.
A future routing provider can supply route duration and traffic-aware ETA through the same abstraction.
Example:
Driver A
2 km
HIGH traffic
ETA = 14 min

Driver B
3.5 km
LOW traffic
ETA = 8 min
Driver B can therefore be selected even though Driver A is geographically closer.
________________________________________
7. Request Scarcity and Waiting-Time Prioritization
Request scarcity
The system considers how many viable alternative drivers are available to each request.
Example:
Request A → 5 viable alternatives
Request B → 0 viable alternatives

             Driver D1
              /     \
             /       \
            A         B
If both requests compete for D1, Request B can receive higher priority because it has fewer practical alternatives.
This is intended to improve overall service quality rather than simply rewarding the request that happens to reach the matching engine first.
Waiting-time protection
A request that has been waiting longer can receive a configurable priority boost.
Waiting time ↑
     ↓
Priority ↑
This reduces the possibility of starvation.
Important separation
The scoring system decides which request should receive priority.
The atomic reservation mechanism decides whether a driver can actually be assigned.
Request scarcity therefore never overrides the concurrency guarantee.
________________________________________
8. Location Staleness
Driver locations are evaluated using their location_updated_at timestamp.
The initial configurable thresholds are:
< 10 seconds      FRESH
10–30 seconds     SLIGHTLY_STALE
> 30 seconds      TOO_STALE
Fresh locations receive normal ranking.
Slightly stale locations remain eligible but receive a ranking penalty.
Too-stale locations are normally excluded.
This allows the system to tolerate minor communication delays without treating every stale update as a failure.
These thresholds are project assumptions and can be changed through configuration.
________________________________________
9. Assignment Confirmation and Reassignment
Assignment lifecycle:
AVAILABLE
    ↓
RESERVED
    ↓
ASSIGNMENT_SENT
    ↓
CONFIRMED
    ↓
BUSY
If confirmation does not arrive within the configured 15-second timeout:
ASSIGNMENT_SENT
       ↓
     TIMEOUT
       ↓
Release driver
       ↓
Requeue request
       ↓
Run matching again
The timeout is intentionally short for demonstration purposes and will be configurable.
The timeout/reassignment process must also handle a race between confirmation and timeout processing.
Both transitions use conditional state changes so that once one valid transition commits, the competing operation becomes a no-op rather than producing an invalid state.
A bounded retry policy prevents a request from remaining in an endless reassignment loop.
________________________________________
10. API Contract
The initial REST interface is:
POST /drivers/{id}/location
PATCH /drivers/{id}/status
POST /requests
GET /requests/{id}
POST /assignments/{id}/confirm
DTOs will be used at the API boundary.
Persistence entities will not be serialized directly, allowing internal schema changes without unnecessarily changing the external API.
Detailed request/response schemas will be implemented and documented during the implementation phase.
________________________________________
11. Testing Strategy
Testing will use PostgreSQL through Testcontainers for integration scenarios where database transaction behavior matters.
The most important test is concurrent matching:
Request A ──┐
            ├── same candidate pool
Request B ──┘
Expected result:
Driver D1 → at most one request
Planned test categories:
•	Basic matching
•	ETA-aware ranking
•	Traffic-aware ETA
•	Vehicle compatibility
•	Availability filtering
•	Location freshness
•	Request scarcity
•	Waiting-time prioritization
•	Concurrent assignment
•	Timeout
•	Automatic reassignment
•	Duplicate confirmation
•	Late confirmation after timeout
The concurrency test will use explicit thread coordination such as CountDownLatch to ensure that matching attempts genuinely overlap.
________________________________________
12. Key Trade-Offs
Decision	Chosen approach	Reason
Architecture	Modular monolith	Simple transactional model
Database	PostgreSQL	Strong transactional guarantees
Driver claim	Atomic conditional update	Simple, safe, low contention
Candidate selection	Two-stage	Lower matching latency
ETA	Routing abstraction	Traffic-aware without external dependency
Traffic	Deterministic mock initially	Reproducible demos and tests
Staleness	Penalty + threshold	Graceful handling of delayed data
Scarcity	Secondary ranking signal	Protect requests with few alternatives
Waiting time	Priority boost	Reduce starvation
Updates	REST + polling initially	Lower implementation complexity
Real-time push	Deferred WebSockets	Not required for core correctness
Distributed services	Deferred	No concrete requirement at current scale
________________________________________
13. Failure Scenarios
Driver becomes unavailable before claiming
Atomic reservation returns zero affected rows.
Action: try the next candidate.
Driver disappears after reservation
Assignment expires.
Action: release the driver and rematch the request.
Location becomes stale
The driver receives a freshness penalty or is excluded depending on age.
Action: continue matching with reliable candidates where possible.
Two requests compete for one driver
Only one atomic reservation succeeds.
Action: the other request continues with its next candidate or is rematched.
Confirmation arrives after timeout
The assignment has already transitioned out of its confirmable state.
Action: late confirmation is rejected/idempotently ignored.
No suitable driver exists
The request enters a controlled no-driver/retry state.
Action: retry according to the configured policy rather than hanging indefinitely.
________________________________________
14. Implementation Roadmap
Day 1
•	Architecture
•	README
•	Design document
•	GitHub repository
•	Project structure
Day 2
•	Spring Boot foundation
•	PostgreSQL
•	Flyway
•	Core entities
•	Driver APIs
•	Request API
Day 3
•	Candidate filtering
•	ETA
•	Traffic simulation
•	Freshness scoring
•	Scarcity scoring
•	Waiting-time scoring
•	Atomic reservation
•	Confirmation
Day 4
•	Timeout processing
•	Reassignment
•	Driver simulator
•	Concurrency tests
•	Failure scenario tests
Day 5
•	Demo dashboard if time permits
•	API documentation
•	Test results
•	README refinement
•	Demo preparation
•	Future improvements
________________________________________
15. Future Improvements
Potential future extensions include:
•	Real traffic-aware routing provider
•	WebSocket live updates
•	Redis/geospatial indexing for larger driver pools
•	Multi-region dispatch
•	Distributed event processing
•	More advanced global matching optimization
•	ML-based ETA prediction
•	Production observability
•	Authentication and authorization
•	Ratings and marketplace features
•	Dynamic pricing
These are intentionally deferred so that the initial implementation remains focused on concurrency correctness, reliable reassignment, and explainable matching.
________________________________________
16. Day-1 Architecture Baseline
This document represents the initial architecture baseline for the project.
Implementation will proceed from this design during the remaining training period. Design decisions may be refined when implementation and testing expose new constraints, with changes documented through Git history and updated project documentation.

