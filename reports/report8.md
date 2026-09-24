# Day 8 Report — RBAC, Auth Enforcement & Flaw Fixes

**Project:** Smart Parking Management System
**Scope of Day 8:** Make the JWT built on Day 7 actually matter — enforce it on real endpoints, add role-based restrictions, fix the two concurrency race conditions flagged earlier, and harden constraint/input handling. Block 6 (full Postman regression) deliberately left for a dedicated final pass.

---

## 1. Where Day 8 Picks Up

Day 7 closed with an explicit, stated gap: JWT generation and validation both worked, but **nothing enforced them** — every endpoint was still completely open regardless of whether a token was sent. Day 8's entire purpose was closing that gap, plus fixing the two real concurrency flaws identified during the Day 4/6 review.

```
Day 7: JWT can be generated ✅ / validated ✅ — but existing endpoints are still open ❌
Day 8: JWT is now actually required, checked, and role-restricted where needed ✅
```

---

## 2. What an `HttpHandler` Actually Is in This Project — Worth Locking In

This caused real confusion today, so it's worth stating precisely: this project has **no separate handler classes** like `ParkingLotHandler.java`. Every route's logic is a **lambda expression**, written directly where the route is registered in `Main.java`:
```java
exchange -> { ... }
```
That lambda satisfies Java's `HttpHandler` functional interface, which is why it can be passed straight into `router.addRoute(...)`. The Router stores it inside its internal `Route` object, and later, when a matching request arrives, calls `route.handler.handle(exchange)` — this simply means *"execute whichever lambda was registered for this route,"* it does not, by itself, imply anything about calling a Service — that only happens because the specific lambda's body happens to call one.

---

## 3. `AuthFilter` — What It Is and How It Wraps a Handler

**File:** `security/AuthFilter.java`

Constructed with a `JwtUtil` (not a `UserDao` — an important early clarification, since validating a token requires the signing secret and claim logic, not database access).

**The core idea: `AuthFilter.requireAuth(originalHandler)` takes a handler and returns a *new*, wrapping handler — it does not replace or modify the original.**
```
original lambda → requireAuth(...) → new "protected" HttpHandler
```
The original handler isn't discarded — it's placed *inside* the wrapper, to be called only if the wrapper's own checks pass.

**Startup vs. request-time — this distinction matters:**
- **At startup** (inside `Main`), `requireAuth(originalLambda)` runs *once*, simply constructing and returning the wrapper object. **No authentication happens at this point** — nothing has decided anything yet, a wrapper object has just been built and handed to the Router to store.
- **At request time**, when an actual `GET /parking-lots` request arrives with an `Authorization` header, *that's* when the wrapper's logic actually executes — extract the header, validate the token, and only then decide whether to proceed to the original handler.

This mirrors the exact same startup-vs-request-time distinction from Day 5's `addRoute()` vs `handle()` — registering a route (or wrapping a handler) is a one-time setup action; the logic inside only runs per-request.

**`AuthFilter`'s actual logic, precisely:**
```
Authorization header present, and shaped like "Bearer <token>"?
   → NO → 401, and RETURN (do not proceed further)
   → YES → extract token → jwtUtil.validateToken(token)
        → invalid/expired → 401, RETURN
        → valid → extract userId, role from claims
              → exchange.setAttribute("userId", ...)
              → exchange.setAttribute("role", ...)
              → call the original handler
```
**The `return` after writing a 401 is not optional or stylistic — it's the entire mechanism that stops an unauthenticated request from ever reaching the real endpoint logic.** Omitting it would still send the 401 response, but execution would fall through and the original handler would run anyway — a real, easy-to-introduce bug if this pattern is ever copied carelessly.

---

## 4. `exchange.setAttribute()` as a Handoff Mechanism Between Layers

This is the same `HttpExchange`-carries-everything model from Day 5's Router (which used `exchange.setAttribute("id", ...)` for path parameters), now reused for a different purpose: **passing identity information discovered by one layer (`AuthFilter`) forward to a layer that runs later (the endpoint lambda, or a role-check wrapper)** — without needing any other shared state or parameter passing mechanism. The Router, the AuthFilter, and the final handler are all operating on the exact same object throughout one request's lifetime, each contributing information the next stage might need.

---

## 5. `401` vs `403` — Locked Down Precisely

This distinction is fundamental enough to the whole day's architecture to state explicitly and remember cleanly:

- **`401 Unauthorized`** — *"I don't know who you are."* No `Authorization` header, malformed `Bearer` value, invalid signature, or expired token. This is squarely `AuthFilter`'s domain.
- **`403 Forbidden`** — *"I know exactly who you are, and you're not allowed to do this."* A valid, authenticated token belonging to a `CUSTOMER` trying to hit an `ADMIN`-only endpoint. This is the **role wrapper**'s domain, and it only makes sense to run *after* authentication has already succeeded — you can't evaluate "are they allowed" before you know "who they are."

**Why the wrappers nest in a specific order, not either order:**
```java
authFilter.requireAuth(
    authFilter.requireRole("ADMIN", originalLambda)
)
```
```
Request → AuthFilter (is the token valid? sets userId/role) → Role wrapper (is role == ADMIN?) → original lambda
```
The role wrapper reads `exchange.getAttribute("role")` — a value that only exists because `AuthFilter` ran first and set it. Reversing the nesting would mean the role check runs before any identity has been established, which is simply broken — there'd be nothing to check yet.

---

## 6. Final Authentication Map — What Ended Up Protected, and How

| Endpoint | Auth required | Role restriction |
|---|---|---|
| `POST /auth/register` | ❌ Public | — |
| `POST /auth/login` | ❌ Public | — |
| `GET /parking-lots` | ✅ | None (any authenticated role) |
| `GET /slots/available` | ✅ | None |
| `GET /tickets/{id}` | ✅ | None |
| `POST /vehicles/entry` | ✅ | None |
| `POST /vehicles/exit/{ticketId}` | ✅ | None |
| `POST /parking-lots` | ✅ | ADMIN only |
| `POST /parking-lots/{lotId}/slots` | ✅ | ADMIN only |
| `GET /vehicles/{vehicleId}/tickets` | ✅ | Own-data only for CUSTOMER (see below) |

`/auth/register` and `/auth/login` are correctly left unwrapped — they *are* the mechanism by which a token is obtained in the first place, so requiring a token to reach them would be a contradiction.

---

## 7. Own-Data Restriction — `GET /vehicles/{vehicleId}/tickets`

This endpoint needed something neither `requireAuth` nor `requireRole` alone could express: **being authenticated as *a* customer isn't enough — you must be *the specific customer who owns this vehicle*.**

**Logic, inside the endpoint's own lambda, after `AuthFilter` has already run:**
```
role = exchange.getAttribute("role")
if role == CUSTOMER:
    userId = exchange.getAttribute("userId")
    vehicle = vehicleDao.findById(vehicleId)   // vehicleId from the path
    if vehicle.getOwnerId() != userId:
        → 403, stop
// ADMIN and ATTENDANT bypass this check entirely — they're allowed to see any vehicle's tickets
→ proceed: ticketDao.findByVehicleId(vehicleId)
```
This is a good concrete example of a restriction that's neither pure authentication nor a simple fixed-role check — it's a **data-level authorization rule**, evaluated using both the token's claims and a database lookup together. Worth being able to explain this distinction clearly if asked how RBAC was implemented, since "role-based" alone doesn't fully describe what's happening here.

---

## 8. Connecting Auth to the Day 0 Audit Trail — a Real, Meaningful Fix

Before today, `POST /vehicles/entry` hardcoded the acting user:
```java
parkingEntryService.parkVehicle(..., 1);   // always "user 1", regardless of who's actually calling
```
This directly undermined the Day 0/3 decision that every ticket records `created_by` for accountability — it was recording a fake, constant value instead of the real caller. Now that `AuthFilter` populates `exchange.getAttribute("userId")` with the *actual* authenticated user's ID from their token, the entry endpoint uses that instead:
```java
(int) exchange.getAttribute("userId")
```
So `Ticket.createdBy` now genuinely reflects who processed the entry — closing a real gap between "designed for" (Day 0's audit trail) and "actually implemented" (a hardcoded placeholder) that existed since Day 6.

---

## 9. Flaw #1 Fix — the Vehicle Creation Race

**Recap of the original problem:** two concurrent requests for a brand-new plate could both see "not found" via `findByPlateNumber`, both attempt `create()`, and the second insert would hit the DB's `UNIQUE(plate_number)` constraint — previously uncaught, surfacing as a raw, unhandled exception.

**The fix — insert, catch the constraint violation, re-fetch:**
```
find by plate → not found
  → try create()
      → duplicate constraint violation (meaning someone else just inserted it first)
          → re-fetch by plate → use that existing row instead
```
**A real implementation detail worth remembering, since it's a genuine Java gotcha:** `SQLIntegrityConstraintViolationException` is a **checked exception**, and the existing `VehicleDao` interface's `create()` method doesn't declare it. Rather than changing the interface's signature (which would ripple outward to every caller), the DAO catches it internally and **wraps it in a `RuntimeException`, preserving the original as the cause**:
```java
catch (SQLIntegrityConstraintViolationException e) {
    throw new RuntimeException("Vehicle already exists", e);
}
```
The calling Service can then check `e.getCause() instanceof SQLIntegrityConstraintViolationException` to detect this specific case and trigger the re-fetch-and-reuse logic, without the DAO's public interface needing to change at all. This is a legitimate, common pattern for adapting a checked exception to an unchecked-exception-based interface without breaking existing contracts.

**Result:** two concurrent requests for the same new plate now both correctly end up referring to the *same* `Vehicle` row — not two rows, and not an unhandled crash.

---

## 10. Flaw #2 Fix — the Duplicate Active-Ticket Race

**Recap:** even with the vehicle race fixed, a separate problem remained — two concurrent entry requests for the *same already-existing* vehicle could both pass `findActiveByVehicle()` (both seeing "no active ticket yet," since that check ran on its own independent connection) before either committed, and if the lot had 2+ available slots, both could succeed — leaving one vehicle with two simultaneously `ACTIVE` tickets.

**Why this needed a different fix than Flaw #1:** this isn't a uniqueness-constraint problem the database can catch automatically (there's no `UNIQUE` constraint on "one active ticket per vehicle" — that's a business rule, not a schema constraint). It needed the **same `FOR UPDATE` locking pattern used for slots, applied to the vehicle itself.**

**New method added:** `VehicleDao.findByIdForUpdate(Connection conn, int vehicleId)` — runs `SELECT * FROM Vehicle WHERE vehicle_id=? FOR UPDATE`, locking the vehicle row for the duration of the transaction. Also added: `TicketDao.findActiveByVehicle(Connection conn, int vehicleId)` — the existing check, but now taking the shared transaction connection instead of grabbing its own.

**The principle this establishes, stated generally:** *the vehicle row itself becomes the serialization point for "does this vehicle have an active ticket" decisions* — the same way the slot row was already the serialization point for "is this slot available." Anywhere two concurrent transactions could both act on stale information about the same entity, that entity's row needs to be locked before the decision is made, not after.

**Dry-run trace, two concurrent requests for the same vehicle (id 42):**
```
Request A                              Request B
─────────                              ─────────
get connection                         get connection
find Vehicle 42 FOR UPDATE             find Vehicle 42 FOR UPDATE
  → lock acquired                        → BLOCKED, waiting for A's lock
check active ticket (same conn)
  → none found
lock available slot, create ticket 101 (ACTIVE)
occupy slot
COMMIT → releases Vehicle 42's lock
                                        → lock acquired (now that A committed)
                                        check active ticket (same conn)
                                          → Ticket 101 found, ACTIVE
                                        → reject, rollback
```
**Result:** exactly one active ticket per vehicle, guaranteed, even under genuine concurrent pressure — not just "usually works." This closes the gap explicitly flagged as unresolved back when the flaws list was first written.

---

## 11. Constraint Violations — Cleaner Messages, Scope Deliberately Limited

A conscious scope decision was made today: **skip building the full custom-exception-hierarchy + central exception mapper** (`NotFoundException`, `ConflictException`, `ValidationException`, `UnauthorizedException`, and a single mapping layer) that was originally planned. Instead, the existing structure was kept, and the specific DAOs most likely to hit real constraint violations were updated to catch `SQLIntegrityConstraintViolationException` and rethrow with a clear, specific message:

- **Vehicle** — duplicate `plate_number` → *"Vehicle with plate number X already exists"*
- **User** — duplicate `email` → clear conflict message
- **Slot** — duplicate `(parking_lot_id, slot_label)` → clear conflict message

**This is a legitimate, honest scope trade-off, not a shortcut to hide** — a full exception hierarchy is the more "correct" long-term architecture, but catching and clarifying the specific constraint violations that can actually occur, in the specific DAOs where they're relevant, delivers most of the real-world benefit (no more raw SQL exceptions reaching a client) for meaningfully less work. Worth stating plainly as a conscious choice if it comes up, rather than something forgotten.

---

## 12. Input Validation — What Got Checked, and Why Two Different Rules for Strings vs. Ints

Validation logic was added directly in `Main.java`, at the point where incoming JSON is already being converted into DTOs — since that's where a bad/missing value first becomes visible, before it's passed any further into business logic.

**String fields** (`name`, `email`, `password`, `plateNumber`, `location`, `slotLabel`, etc.): checked for `null`/blank.

**`int` fields** (`lotId`): a fundamentally different check is needed, and the reason is a genuine Java language fact worth remembering — **`int` is a primitive and cannot be `null`.** If a client omits `lotId` from the JSON entirely, Gson doesn't leave the field "unset" the way it would for a `String` — it silently defaults to `0`. So the validation rule for `lotId` is `lotId <= 0` is invalid, rather than a null-check, which would never trigger since `int` can never actually be `null` in the first place.

**`vehicleType` gets a second layer of validation beyond blank-checking:** even a present, non-blank value like `"BIKEE"` would previously crash `VehicleType.valueOf("BIKEE")` with an unhandled `IllegalArgumentException`. This is now handled safely — an unrecognized enum value produces a clean `400`, not a server crash.

**Path and query parameters get the equivalent treatment, just via a different extraction mechanism:** `GET /tickets/{id}`, `POST /vehicles/exit/{ticketId}`, `GET /vehicles/{vehicleId}/tickets` all validate their path-extracted IDs are parseable and positive; `/slots/available?lotId=&vehicleType=` validates the same way after manual query-string parsing — the validation *rule* is identical to a DTO field's, just applied at a different point in the code since the value arrives differently.

**A real bug caught and fixed while doing this:** the login endpoint originally wrapped essentially everything in one broad `catch → 401`, which meant a **malformed request** (missing `email` field entirely, say) would incorrectly come back as `401 Unauthorized` — implying "wrong credentials" when the actual problem was "the request itself was invalid." These are meaningfully different failure categories and now correctly return different codes: **`400` for bad/missing input, `401` reserved specifically for actual wrong-credentials cases.**

**Also correctly identified and deliberately guarded against:** a public `/auth/register` endpoint accepting an arbitrary client-supplied `role` value, including `"ADMIN"`, would let anyone mint themselves an admin account on request — directly undermining the Day 0 decision that the first/only admin account is seeded outside the application. This was flagged explicitly as something to guard against (constrain what roles public registration is allowed to assign) — worth double-checking this guard is actually in place before the interview, since it's exactly the kind of security gap that's easy to describe in theory and forget to enforce in code.

---

## 13. Connection Leak Check — a Real, Subtle Bug Found and Fixed

Inspecting `ParkingEntryService`/`ParkingExitService`'s `finally` blocks revealed a genuinely subtle issue, not a false alarm:
```java
// before — risky
finally {
    conn.setAutoCommit(true);   // if this throws...
    conn.close();               // ...this is never reached
}
```
If `setAutoCommit(true)` itself throws (rare, but possible), the following `conn.close()` line is skipped entirely — meaning the connection is never returned to the Hikari pool, a genuine leak, one connection lost per occurrence. Given that Day 4's stress test already demonstrated firsthand how quickly a 10-connection pool can be exhausted under load, this wasn't a hypothetical concern.

**Fix — separate, independent try/catch for each cleanup step:**
```java
try {
    conn.setAutoCommit(true);
} catch (SQLException e) { /* log, don't rethrow */ }

try {
    conn.close();
} catch (SQLException e) { /* log, don't rethrow */ }
```
Now a failure in the first cleanup step can never prevent the second, more important one (returning the connection) from being attempted. This is a good general pattern worth remembering: **in a `finally` block with multiple cleanup steps, each step's own failure should never be able to skip the steps after it.**

---

## 14. Five Conceptual Lessons From the Day, Stated Once Each

1. **Authentication vs. authorization** — "who are you" (JWT validation) is a different question from "what are you allowed to do" (role checking), and the architecture deliberately keeps them as two separate wrapper layers rather than one combined check.
2. **A wrapper is a gate, not a replacement** — `requireAuth`/`requireRole` never discard the original handler; they conditionally decide whether to let execution reach it.
3. **One `HttpExchange` travels through everything** — Router, AuthFilter, role wrapper, and the final endpoint lambda all read from and write to the exact same object, which is precisely how `setAttribute`/`getAttribute` can hand information forward between layers with no other coupling needed.
4. **Service vs. DAO, reinforced by Flaw #2 specifically** — the Service decides *what business action should happen*; the DAO decides *how a specific database operation is performed*. Fixing Flaw #2 required both layers to cooperate correctly (Service orchestrating the lock-then-check-then-write sequence, DAO providing the connection-aware locking query) — neither layer alone could have fixed it.
5. **A transaction belongs to a `Connection`, not to a Service method** — restated one more time today because Flaw #2's fix depended on it directly: any DAO call using a *different* connection than the one your transaction is holding is, by definition, outside that transaction, no matter how logically related the operations look in your code.

---

## 15. Status

- [x] `AuthFilter` built — token extraction, validation, `401` on failure, identity stored on the exchange for downstream use
- [x] Role wrapper (`requireRole`) built — `403` on mismatch, correctly nested to run only after authentication
- [x] Every existing endpoint correctly categorized: public, authenticated-any-role, or ADMIN-only
- [x] Own-data restriction implemented on `GET /vehicles/{vehicleId}/tickets`
- [x] Hardcoded `actingUserId = 1` removed from vehicle entry, replaced with the real authenticated user's ID
- [x] Flaw #1 (vehicle creation race) fixed — insert/catch-duplicate/re-fetch pattern, checked exception adapted via cause-wrapping
- [x] Flaw #2 (duplicate active-ticket race) fixed — vehicle-row `FOR UPDATE` locking added as the serialization point
- [x] Cleaner constraint-violation messages for Vehicle/User/Slot duplicates (full exception hierarchy deliberately deferred/skipped — documented scope choice)
- [x] Input validation added across all request DTOs, path params, and query params — including the `int` vs `String` null-check distinction and safe enum parsing
- [x] Login's error handling corrected — bad input now `400`, wrong credentials now correctly stays `401`
- [x] Public registration's role assignment flagged as a risk — verify before the interview that arbitrary client-supplied `"ADMIN"` role is actually blocked, not just identified as a concern
- [x] Connection-leak edge case in `finally` blocks found and fixed
- [ ] Block 6 — full Postman regression pass (401/403/own-data/validation/duplicate/race scenarios, plus reconfirming all existing happy paths) — intentionally deferred to a dedicated pass

---

## Status
✅ Day 8 core work complete — auth is now enforced, RBAC is real, and both known concurrency flaws are fixed. Block 6 (full regression testing) remains before Day 8 can be considered fully closed — recommended as the very next step before moving to Day 9.