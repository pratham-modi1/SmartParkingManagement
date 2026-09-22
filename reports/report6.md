# Day 6 Report — Entry/Exit Endpoints, Admin & Ticket APIs, Full Dry-Run Traces

**Project:** Smart Parking Management System
**Scope of Day 6:** Connect the REST layer to real business logic — vehicle entry/exit through the Service layer (with transactions and locking), admin write endpoints, ticket lookup, and a full understanding of path vs. query parameters and DTOs. Closes out the REST layer before auth begins.

---

## 1. Where This Picks Up

Day 5 built the infrastructure (`HttpServer`, `Router`, `ApiResponse`/`ResponseUtil`, Gson) and proved it worked with two simple **read-only** endpoints that went straight from Handler to DAO. Day 6's job was different in kind, not just in count: the endpoints built today (`POST /vehicles/entry`, `POST /vehicles/exit/{ticketId}`) involve real business rules and multi-step transactions — so the Handler can no longer talk to the DAO directly. It must go through the **Service layer** (`ParkingEntryService` / `ParkingExitService`) built on Day 4, so that the transaction boundaries, locking, and business rules built then aren't duplicated or bypassed at the HTTP layer.

```
Day 5 (reads):  Handler → DAO → MySQL
Day 6 (writes): Handler → Service → DAO (shared connection, one transaction) → MySQL
```

---

## 2. DAO Refresher — the Two-Direction Pattern (Quick Recap)

Since a few DAO details resurfaced today (`ParkingLotDaoImpl.create()`, `mapRow()`), the core pattern worth keeping in mind: every DAO handles data flowing in **two directions**, and each direction has its own dedicated mechanism.

**Java → DB** (inside `create()`/`update()`): SQL written with `?` placeholders (`INSERT INTO ParkingLot (name, location, status) VALUES (?, ?, ?)`), filled in via `statement.setString(1, ...)`, `statement.setInt(2, ...)`, etc. — never raw string concatenation.

**DB → Java** (inside `mapRow()`): a `ResultSet` is a raw, column-by-column view of a row — not directly usable as a domain object — so `mapRow()` is the one place that converts it into a real `ParkingLot`/`Slot`/`Ticket` object, doing the enum conversions (`LotStatus.valueOf(rs.getString("status"))`) and type conversions along the way.

**Generated keys**, used in every `create()`: a new Java object starts with `id = 0` because it doesn't have a real database identity yet — `Statement.RETURN_GENERATED_KEYS` + `statement.getGeneratedKeys()` retrieves the ID MySQL actually assigned, and `parkingLot.setParkingLotId(rs.getInt(1))` writes it back into the *same* Java object, so nothing needs to be re-fetched afterward.

---

## 3. `FOR UPDATE` — Why `setAutoCommit(false)` Alone Isn't Enough

This came up again today and is worth restating precisely, since it's easy to conflate the two mechanisms:

- **`conn.setAutoCommit(false)`** controls *when changes become permanent* — it groups several statements so they all commit together or all roll back together. On its own, it does **not** stop two separate transactions from both reading the same row as "available" before either one commits.
- **`SELECT ... FOR UPDATE`** controls *row access* — it locks the matching row(s) for the current transaction specifically, so a second transaction trying to select that same row has to wait until the first one commits or rolls back.

```
Request A → SELECT available slot FOR UPDATE → slot locked
Request B → tries the same slot → must wait, cannot simultaneously grab it
```
Both mechanisms are needed together: the transaction groups the *writes*, the lock protects the *read* that decides what to write.

---

## 4. `ParkingEntryService.parkVehicle()` — Full Dry Run

Rather than re-explain the code line by line, here's a concrete **dry run** — tracing exactly what happens for one specific request, with real example values, from the moment it hits the Router to the moment a response goes back.

**Request:**
```http
POST /vehicles/entry
{ "plateNumber": "MH12AB1234", "vehicleType": "FOUR_WHEELER", "lotId": 1 }
```

**Trace:**

| Step | What happens | State after |
|---|---|---|
| 1 | `HttpServer` receives the request, creates `HttpExchange` | — |
| 2 | `Router` matches `POST /vehicles/entry`, calls the Handler | — |
| 3 | Handler reads the body via `exchange.getRequestBody().readAllBytes()`, converts to string | raw JSON in hand |
| 4 | `gson.fromJson(body, EntryRequest.class)` | `EntryRequest{plateNumber="MH12AB1234", vehicleType="FOUR_WHEELER", lotId=1}` |
| 5 | Handler converts the string `"FOUR_WHEELER"` to the enum: `VehicleType.valueOf("FOUR_WHEELER")` | `VehicleType.FOUR_WHEELER` |
| 6 | Handler calls `parkingEntryService.parkVehicle("MH12AB1234", VehicleType.FOUR_WHEELER, 1, actingUserId)` | control enters the Service |
| 7 | `conn = DBConfig.getConnection()` — one connection borrowed from the pool for this whole operation | `conn` = Connection #7 (say) |
| 8 | `conn.setAutoCommit(false)` — transaction begins | nothing committed yet |
| 9 | `vehicleDao.findByPlateNumber("MH12AB1234")` → suppose this plate has never been seen before → returns `Optional.empty()` | — |
| 10 | `.orElseGet(() -> { ... vehicleDao.create(v); return v; })` runs, since step 9 was empty — creates a new `Vehicle` with `ownerId = null` (walk-in) | Vehicle row inserted, `vehicleId = 42` |
| 11 | `ticketDao.findActiveByVehicle(42)` → no active ticket exists for a brand-new vehicle → `Optional.empty()` → no rejection, proceed | — |
| 12 | `slotDao.findFirstAvailableForUpdate(conn, 1, FOUR_WHEELER)` — runs `SELECT ... FOR UPDATE`, locking whatever row it returns, using `conn` specifically (not a new connection) | suppose it returns `Slot{slotId=9, status=AVAILABLE}` — **this row is now locked until commit/rollback** |
| 13 | Slot isn't null, so no "lot full" rejection — proceed | — |
| 14 | `new Ticket(0, 42, 9, null, null, ACTIVE, actingUserId, false, false)` built in memory | ticket object exists, `ticketId = 0` (placeholder) |
| 15 | `ticketDao.create(conn, ticket)` — INSERT runs on `conn`, generated key comes back | `ticket.setTicketId(15)` — same Java object now has the real ID |
| 16 | `slot.setStatus(OCCUPIED)` — mutate the in-memory `Slot` object | — |
| 17 | `slotDao.update(conn, slot)` — UPDATE runs on `conn` | slot 9's status is now `OCCUPIED` in the DB (not yet permanent — still inside the transaction) |
| 18 | `conn.commit()` — everything from steps 9–17 becomes permanent together | vehicle 42, ticket 15 (ACTIVE), slot 9 (OCCUPIED) — all committed atomically |
| 19 | Method returns the `Ticket` object (id 15) back up to the Handler | — |
| 20 | Handler wraps it: `ApiResponse.success(ticket)` | — |
| 21 | `ResponseUtil.sendJson(exchange, 200, apiResponse)` — Gson serializes, headers/status set, body written | — |
| 22 | Response flows back through the same `HttpExchange` → `HttpServer` → Postman | `{ "success": true, "data": { "ticketId": 15, "status": "ACTIVE", ... } }` |

**If step 12 had returned `null` instead (no available slot):** the code rolls back (`conn.rollback()`) and throws — the vehicle *record* created in step 10 would still exist (that step ran on its own separate, non-transactional connection, per the known scope gap noted in the Day 4 report), but no ticket or slot change would be committed. The Handler catches this, returns a `409`/`400` with a clear error message instead of a raw exception.

**If step 11 had found an active ticket:** rollback happens immediately at that point, before even attempting to touch a slot — the vehicle already has an active session, so nothing about slots needs to be considered at all.

---

## 5. `ParkingExitService.exitVehicle()` — Full Dry Run

**Request:**
```http
POST /vehicles/exit/15
{ "lostTicket": true }
```

**Trace:**

| Step | What happens | State after |
|---|---|---|
| 1 | `Router` matches `POST /vehicles/exit/{ticketId}`, extracts `ticketId = "15"` via path matching, stores it: `exchange.setAttribute("ticketId", "15")` | — |
| 2 | Handler reads `exchange.getAttribute("ticketId")`, parses to `int ticketId = 15` | — |
| 3 | Handler reads the JSON body, extracts `lostTicket = true` (defaulting to `false` if the field is absent) | — |
| 4 | Handler calls `parkingExitService.exitVehicle(15, true)` | control enters the Service |
| 5 | `conn = DBConfig.getConnection()`, `conn.setAutoCommit(false)` | transaction begins |
| 6 | `ticketDao.findById(conn, 15)` → returns the `Ticket` from the entry dry run above: `status = ACTIVE, entryTime = <whenever it was created>, slotId = 9, vehicleId = 42` | — |
| 7 | Check: `ticket.getStatus() != ACTIVE`? No, it is `ACTIVE` — proceed (if it *had* already been `COMPLETED`, this is exactly where the rejection happens) | — |
| 8 | `slotDao.findById(9)` → the occupied slot from entry | — |
| 9 | `pricingConfigDao.getCurrentConfig()` — a plain read, **not** using `conn**, since this is just a configuration lookup, not a write that needs to share the transaction | `PricingConfig{baseFee=20, baseHours=3, extraHourFee=10, lostTicketPenalty=70}` |
| 10 | `LocalDateTime exitTime = LocalDateTime.now()` — captured **once**, reused for every subsequent step that needs "now" | e.g. `exitTime = 2026-09-20T16:42:00` |
| 11 | `FeeCalculator.calculateFee(ticket.getEntryTime(), exitTime, pricingConfig, true)` — suppose the duration works out to 45 minutes (under 180) → base fee `₹20`, then since `lostTicket = true` → `fee = 20 + 70 = ₹90` | `fee = 90.00` |
| 12 | `new Payment(0, 15, 90.00, SUCCESS, exitTime)` built | — |
| 13 | `paymentDao.create(conn, payment)` — INSERT on the shared connection, generated key retrieved | payment row inserted, `paymentId` set on the object |
| 14 | `ticket.setExitTime(exitTime); ticket.setStatus(COMPLETED); ticket.setLostTicketFlag(true);` — mutate the in-memory ticket | — |
| 15 | `ticketDao.update(conn, ticket)` — UPDATE on `conn` | ticket 15 now `COMPLETED` (not yet permanent) |
| 16 | `slot.setStatus(AVAILABLE)` | — |
| 17 | `slotDao.update(conn, slot)` — UPDATE on `conn` | slot 9 now `AVAILABLE` (not yet permanent) |
| 18 | `conn.commit()` — payment, ticket-completion, and slot-release all become permanent together | payment recorded, ticket 15 COMPLETED, slot 9 AVAILABLE — all committed atomically |
| 19 | Method builds an `ExitResponse { ticket, fee }` and returns it | — |
| 20 | Handler wraps: `ApiResponse.success(exitResponse)`, sends via `ResponseUtil` | `{ "success": true, "data": { "ticket": {...}, "fee": 90.00 } }` back to Postman |

**Why steps 13, 15, 17 all share `conn` but step 9 doesn't:** the payment, the ticket completion, and the slot release are three writes that must all succeed or all fail together — that's the actual write-transaction. Reading the current pricing configuration is not modifying anything, so there's no consistency risk in it running on its own separate connection outside that transaction.

**If step 7 had found `status = COMPLETED` already** (someone tries to exit the same ticket twice): rollback happens immediately, before touching the slot, payment, or fee calculation at all — a clear rejection returned to the Handler instead of silently re-processing a fee.

---

## 6. DTOs — Why `EntryRequest` and `ExitResponse` Exist

**`EntryRequest`** (a plain class with public fields: `plateNumber`, `vehicleType`, `lotId`) exists because the incoming JSON body needs *something* for Gson to deserialize into — you can't hand Gson a `Ticket` or `Vehicle` entity directly, because the request body's shape (three simple fields) doesn't match any single entity's shape. The flow: `JSON → Gson → EntryRequest → (manual conversion, e.g. string→enum) → passed into the Service method`.

**`ExitResponse`** exists for the opposite reason — realizing partway through that returning just the `Ticket` object wasn't enough, since the fee itself (a calculated value, not a database column on `Ticket`) also needed to go back to the client. Rather than awkwardly bolting a `fee` field onto the `Ticket` entity (which would misrepresent what's actually stored in the DB), a small wrapper DTO — `{ ticket, fee }` — packages exactly what the API needs to return, no more and no less.

**The general principle both illustrate:** *a database entity's shape and an API request/response's shape are not obligated to be the same.* DTOs exist specifically to decouple "what's stored in the DB" from "what a specific endpoint needs to receive or send" — entities model your data, DTOs model your API's contract.

---

## 7. Path Parameters vs. Query Parameters — Side by Side

Both were used today, in different endpoints, for a genuinely different reason each:

| | Path parameter | Query parameter |
|---|---|---|
| Example | `/parking-lots/5/slots`, `/vehicles/exit/15` | `/slots/available?lotId=1&vehicleType=FOUR_WHEELER` |
| Represents | Identifies *which specific resource* | Filters/modifies a request against a resource |
| Extracted via | Router's pattern matching (`{lotId}`) → stored on the exchange via `exchange.setAttribute(...)` → read with `exchange.getAttribute("lotId")` | `exchange.getRequestURI().getQuery()` → manually split on `&` then `=` into a `Map<String,String>` → read with `params.get("lotId")` |
| Who's responsible for parsing | The Router (automatically, as part of matching) | The Handler (manually — the raw `HttpServer` does not parse query strings for you) |

Both endpoint types built today used one or the other correctly: `POST /parking-lots/{lotId}/slots` and `POST /vehicles/exit/{ticketId}` use path params (identifying *which* lot/ticket); `GET /vehicles/{vehicleId}/tickets` also uses a path param (whose tickets); the earlier `GET /slots/available?lotId=&vehicleType=` from Day 5 uses query params (filtering criteria, not identifying one resource).

---

## 8. Admin & Ticket Lookup Endpoints — Quick Reference

- **`POST /parking-lots`** — `ParkingLotRequest{name, location}` → Gson → `ParkingLot` object → `parkingLotDao.create(...)` → generated ID returned in the response. No transaction needed (single insert), no role restriction yet (that's Day 7).
- **`POST /parking-lots/{lotId}/slots`** — path param for `lotId`, body `SlotRequest{slotLabel, vehicleType}` → `Slot` object built with `status = AVAILABLE` by default → `slotDao.create(...)`.
- **`GET /vehicles/{vehicleId}/tickets`** — path param for `vehicleId`, straight to `ticketDao.findByVehicleId(vehicleId)` — no DAO changes needed, this method already existed from Day 3. Returns a `List<Ticket>` wrapped in `ApiResponse.success(...)`.

None of these three needed a Service layer involvement — each is a single DAO operation with no multi-step consistency requirement, which is exactly the same reasoning that let Day 5's read endpoints skip the Service layer too.

---

## 9. Full Architecture at the End of Day 6

```
                    Main
                     │
          ┌──────────┴──────────┐
          ↓                     ↓
        Router                DAOs
          │            (ParkingLot, Slot, Vehicle,
          │             Ticket, Payment, PricingConfig)
          ↓
       Handlers
          │
   ┌──────┴──────┐
   ↓             ↓
ParkingEntry   ParkingExit
 Service        Service
   │             │
   └──────┬──────┘
          ↓
         DAO
          ↓
        MySQL
```

**Full round trip, both directions, one more time as the standing reference:**
```
Postman → HTTP → HttpServer → HttpExchange created → Router matches route
   → Handler (parses body/params) → Service (business logic + transaction)
   → DAO (shared connection) → MySQL
   ↑ result flows back ↑
MySQL → DAO → Service → Handler → ApiResponse → ResponseUtil (Gson + headers + status)
   → same HttpExchange → HttpServer → Postman
```

---

## 10. Status

| Method | Endpoint | Purpose | Example |
|---|---|---|---|
| `GET` | `/tickets/{id}` | Get/test a ticket by ID | `/tickets/15` |
| `GET` | `/parking-lots` | Get all parking lots | `/parking-lots` |
| `GET` | `/slots/available` | Get available slots for a lot + vehicle type | `/slots/available?lotId=1&vehicleType=FOUR_WHEELER` |
| `POST` | `/vehicles/entry` | Park a vehicle, allocate slot, create ticket | `/vehicles/entry` |
| `POST` | `/vehicles/exit` | Exit vehicle, calculate fee, create payment, free slot | `/vehicles/exit` |

- [x] `POST /vehicles/entry` — routed through `ParkingEntryService`, fully transactional, `FOR UPDATE` locking confirmed via the dry run above
- [x] `POST /vehicles/exit/{ticketId}` — routed through `ParkingExitService`, fully transactional, fee/payment/ticket/slot consistency confirmed via the dry run above
- [x] `EntryRequest` / `ExitResponse` DTOs — decoupling API shape from entity shape, understood and justified
- [x] Path parameter vs. query parameter distinction — mechanism, extraction method, and correct usage for each confirmed
- [x] `POST /parking-lots`, `POST /parking-lots/{lotId}/slots`, `GET /vehicles/{vehicleId}/tickets` — all functional, no role restriction yet (expected — Day 7)
- [x] Double-booking, full-lot, already-completed-ticket, and invalid-ticket rejections all verified reachable and cleanly handled via HTTP, not just in isolated Java tests
- [x] Lost-ticket fee flow verified end-to-end via Postman: ₹20 base + ₹70 penalty = ₹90, matching `FeeCalculator`'s logic exactly



## What you can do right now

1. Check parking lots
        
2. Check available slots
        
3. Enter a vehicle
        
4. System automatically assigns a slot
        
5. System creates an ACTIVE ticket
        
6. View/check the ticket
        
7. Exit the vehicle using ticket ID
        
8. Calculate parking fee
        
9. Create payment
        
10. Mark ticket COMPLETED
        
11. Free the slot again



## Status

✅ Day 6 complete — REST layer fully connected to business logic. Proceeding to Day 7 (JWT authentication: register/login, token generation and validation).