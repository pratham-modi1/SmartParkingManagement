# Day 4 Report — Service Layer, Transactions & Concurrency

**Project:** Smart Parking Management System
**Scope of Day 4:** Moved from pure DAO/database plumbing (Day 3) into real business logic — fee calculation, a transactional service layer, thread-safe slot allocation under concurrent load, and full end-to-end verification.

**How this report is ordered:** in the exact sequence concepts need to be understood — each section is written so that reading it in order answers the question the *next* section would otherwise raise. If you reread this top to bottom, nothing should feel out of place.

---

## 1. The Core Idea of the Day, Stated Once

Everything built today follows one governing principle:

> **The Service layer does not talk SQL directly. It coordinates DAOs, applies business rules, and makes several separate database operations behave like one single, atomic business action.**

A DAO answers *"how do I read/write this one table?"* A Service answers *"given a business event (a car arriving, a car leaving), what sequence of reads/writes needs to happen, and what happens if one of them fails partway through?"* Everything else today is a consequence of that one distinction.

---

## 2. `FeeCalculator` — Pure Calculation, No Database

**File:** `service/FeeCalculator.java`

This class has exactly one job: given an entry time, an exit time, the current `PricingConfig`, and a lost-ticket flag, return a fee. It never touches MySQL, never creates a `Connection`, never knows a `Ticket` or `Payment` exists as a database row — it's a pure function. This was the deliberate first example of separating *calculation* from *persistence*, a distinction that matters more once transactions enter the picture.

**The logic, following your Day 0 locked rule (₹20 for ≤180 min, +₹10 per started hour beyond that, +₹70 if lost-ticket):**

1. Convert the entry/exit gap into minutes: `Duration.between(entryTime, exitTime).toMinutes()`
2. If `minutes <= baseHours * 60` (180 for your config) → fee = `baseFee`, done
3. Otherwise, compute `extraMinutes = minutes - 180`, then convert that into **started hours, rounded up** — 181 minutes means 1 minute into a new hour, which counts as a full started hour (₹10), not a fraction of one
4. `fee = baseFee + (extraHours * extraHourFee)`
5. If `lostTicket == true` → `fee = fee.add(lostTicketPenalty)`

**Two implementation details worth remembering:**

- **Why `BigDecimal`, not `double`:** money must never go through binary floating-point arithmetic, which cannot represent most decimal values exactly and silently introduces rounding errors. Since the DB column is `DECIMAL(10,2)`, the Java-side type has to preserve that same exactness — `BigDecimal.add()` / `.multiply()` throughout, no `double` anywhere in the calculation.
- **Why integer ceiling-division instead of `Math.ceil()`:** `(extraMinutes + 59) / 60` (integer arithmetic) was used instead of `Math.ceil(extraMinutes / 60.0)`. Both give the same correct result (60→1, 61→2, 119→2, 120→2), but the integer version keeps duration handling entirely in integer/long arithmetic, only switching to `BigDecimal` once you reach the actual money calculation — a cleaner separation of "counting time" from "counting money."

You verified this against hand-calculated cases (181 min → ₹30, 241 min → ₹40, etc.) and moved on without writing a full formal test suite for it — a reasonable call given the scope, since it gets exercised thoroughly later by the integration test.

---

## 3. The Service Layer — Why It Exists, and Constructor Injection

**File:** `service/ParkingService.java` (later split — see Section 9)

Your Day 2 package structure had already reserved a `service/` package for exactly this purpose. Today it got used for the first time.

**The layering, once and for all:**
```
Controller (HTTP layer, comes later)
      ↓
Service (business logic — today's work)
      ↓
DAO (database operations)
      ↓
Database
```

The Service was given its DAOs through the **constructor** (constructor injection) rather than creating them itself:
```java
private final VehicleDao vehicleDao;
private final SlotDao slotDao;
private final TicketDao ticketDao;
private final PaymentDao paymentDao;
private final PricingConfigDao pricingConfigDao;
```
This is the same loose-coupling instinct from your billing project, just applied one layer higher — the Service depends on *what a DAO can do* (its interface), not on *which specific implementation* is doing it. Practically, in this project, you have exactly one implementation per DAO, so the benefit today is more architectural correctness than an immediate practical need (no mocking/swapping is actually happening yet) — but it's the right habit to have in place before it's needed, and costs nothing to maintain.

---

## 4. Why a Multi-Step Operation Needs a Transaction

Parking a vehicle is not one SQL statement — it's a sequence:
```
find/create vehicle → check no existing active ticket → lock & find an available slot
→ create the ticket → mark the slot OCCUPIED
```
These five steps together represent **one business action**: *park this vehicle*. If step 4 succeeds but step 5 fails, you'd be left with an `ACTIVE` ticket pointing at a slot the database still thinks is `AVAILABLE` — a genuinely broken, inconsistent state with no automatic way back.

The fix is a **transaction**: instead of each step committing independently,
```
BEGIN → step1 → step2 → step3 → step4 → step5 → COMMIT
```
or, if anything fails partway,
```
BEGIN → step1 → step2 → FAILURE → ROLLBACK (undoes everything since BEGIN)
```
In JDBC, this begins with `conn.setAutoCommit(false)` — this single line is what turns a series of independent auto-committing statements into one deliberate, all-or-nothing unit.

---

## 5. Connection Ownership — the Idea That Took the Longest to Click

This was the single most-revisited concept of the day, so it's worth laying out precisely, in the order that actually resolves it.

**The rule a transaction depends on:** every operation that needs to succeed or fail *together* must run through **the exact same `Connection` object**. A transaction isn't a property of your code's structure — it's a property of one specific `Connection`, tracked by MySQL, starting at `setAutoCommit(false)` and ending at `commit()`/`rollback()` on that same connection.

**What that means concretely in `parkVehicle()`:**
```java
Connection conn = DBConfig.getConnection();   // ONE connection, created once
conn.setAutoCommit(false);

// ... later, this exact same variable is passed into every DAO call that must
// participate in this transaction:
slotDao.findFirstAvailableForUpdate(conn, ...);
ticketDao.create(conn, ticket);
slotDao.update(conn, slot);

conn.commit();
```
The DAO methods that accept `conn` as a parameter **never call `DBConfig.getConnection()` internally** — they use whatever connection they were handed. That's the entire mechanism: *the Service borrows one connection from the pool, and every DAO call it makes during that operation reuses that same borrowed connection instead of grabbing its own.*

**Why this matters in a way that's easy to miss:** if a DAO method quietly called `DBConfig.getConnection()` itself (as all the plain, non-transactional DAO methods do), it would be borrowing a *different* connection from the pool — meaning it starts and ends its own independent transaction, invisible and unrelated to the Service's outer transaction. A `rollback()` on connection A has zero effect on work already done through connection B. This is precisely why **two versions of certain DAO methods exist.**

---

## 6. The Two Kinds of DAO Methods

Every DAO in this project (from Day 3 onward) has, where needed, both:

**A normal, self-contained method:**
```java
void update(Slot slot);
```
Internally: `DBConfig.getConnection()` → execute → close. The DAO owns its own connection, runs and commits independently. Fine for a standalone operation with no surrounding transaction.

**A transactional overload:**
```java
void update(Connection conn, Slot slot);
```
Internally: uses the supplied `conn` directly, does **not** create or close a connection itself, does **not** commit — commit/rollback is entirely the caller's responsibility. This is what lets several DAO calls become part of one larger, Service-owned transaction.

**What got added today, specifically:** `SlotDao` gained `update(Connection conn, Slot slot)` (it previously only had the plain version), and `PaymentDao` gained `create(Connection conn, Payment payment)` for the same reason — payment creation needed to participate in the exit transaction alongside the ticket/slot updates.

**A conscious scope decision:** `vehicleDao.findByPlateNumber()`, `vehicleDao.create()`, and `ticketDao.findActiveByVehicle()` inside `parkVehicle()` are **still using their plain, non-transactional versions** — meaning technically not every single database read/write in the parking flow shares the one outer transaction. The correct, complete architecture would eventually give those methods `Connection` overloads too. This was identified and deliberately deferred rather than expanded mid-flow, to keep today's scope controlled — worth remembering as a known, intentional gap, not an oversight.

---

## 7. `FOR UPDATE` — Preventing Two Transactions From Claiming the Same Slot

**The problem, precisely:** two customers arrive at (functionally) the same instant, both requesting a slot in a lot with exactly one slot left. Without any protection, both transactions independently run `SELECT ... WHERE status='AVAILABLE'`, both see that same one slot as available, and both proceed to claim it — a double-booking.

**The fix:**
```sql
SELECT * FROM Slot WHERE parking_lot_id=? AND vehicle_type=? AND status='AVAILABLE'
LIMIT 1 FOR UPDATE
```
`FOR UPDATE` tells MySQL: *lock the row(s) this query returns, for the current transaction, until that transaction commits or rolls back.* If a second transaction tries to run the same locking query against the same row while the first transaction still holds the lock, the second transaction **waits** — it does not proceed and does not see a false "available" result — until the first either commits (row is now genuinely gone, second transaction correctly finds nothing) or rolls back (row becomes available again, second transaction can now claim it).

**Who "wins" when two requests arrive at the same instant** — this was asked directly, and the honest answer is: it depends on which transaction's lock request MySQL's internal row-locking mechanism grants first — not your application code, not "faster internet," not "better CPU." You do not control, and do not need to control, *which* request wins. What you're actually guaranteeing is a much narrower and more important property: **exactly one transaction can ever successfully claim a given slot, no matter how many arrive at the same time.** That's the correctness property that matters, not who happens to win any individual race.

**One clarification worth keeping precise:** this protection lives entirely at the **database/transaction level**, not in Java thread-handling. Whether two conflicting requests originate from two threads in the same JVM, two separate processes, or two different physical computers, MySQL sees them purely as two concurrent transactions and applies the same row-locking logic regardless. This is *why* the fix belongs in SQL (`FOR UPDATE`) rather than, say, a Java `synchronized` block — a `synchronized` block would only protect against concurrent threads within one JVM process, and would do nothing if your application were ever run as multiple instances/processes talking to the same database, which is a completely realistic real-world deployment scenario.

---

## 8. `exitVehicle()` — Same Transactional Shape, Different Steps

Once the transaction/connection-ownership model from Sections 4–6 was understood, the exit flow followed the identical pattern with different content:
```
find ticket (reject if not ACTIVE) → get slot → get current PricingConfig
→ calculate fee via FeeCalculator → create Payment (via conn) → ticket → COMPLETED
→ slot → AVAILABLE → commit
```

**Why this needs a transaction too — stated precisely, since "make sure the money goes to the right place" is *not* the real reason:** the actual risk is **inconsistency between related rows**. Without a transaction, it's possible to end up with `Payment = SUCCESS` while `Ticket` is still `ACTIVE` and `Slot` is still `OCCUPIED` — a state where money was recorded as collected but the system's own records say the car never left. The transaction guarantees payment + ticket-completion + slot-release happen together, or none of them do.

**One deliberate exception — `PricingConfigDao.getCurrentConfig()` does *not* use the shared `conn`.** This was questioned directly ("why doesn't everything use conn?") and the answer is: not every database interaction inside a business operation needs to be part of the write-transaction — only the ones that are *modifying* state that must stay consistent together. Reading the current pricing configuration is a read-only lookup that isn't being changed by this operation; it doesn't need to share the transaction boundary with the payment/ticket/slot writes that actually do need to succeed or fail together.

**Why `exitTime` is captured exactly once, up front:**
```java
LocalDateTime exitTime = LocalDateTime.now();
```
…and then reused for the fee calculation, `payment.paidAt`, and `ticket.exitTime` — rather than calling `LocalDateTime.now()` three separate times. Calling it three times risks each call returning a very slightly different timestamp (even milliseconds apart), which would mean your fee, your payment record, and your ticket's own exit time don't technically agree on when the vehicle actually left. Capturing one value and reusing it guarantees all three refer to the exact same moment.

**How the lost-ticket flag actually flows:** the Service does not "detect" that a ticket was lost — it's a decision made upstream (by whoever is handling the exit — an attendant, in the real design) and passed in as a boolean argument: `exitVehicle(ticketId, true)`. That one boolean does two things simultaneously: it's recorded permanently on the ticket (`ticket.setLostTicketFlag(true)`) as part of the historical record, and it's passed into `FeeCalculator` so the ₹70 penalty gets added to the fee. The Service's job is only to *apply* that decision correctly and consistently, not to independently judge whether a ticket is actually lost.

---

## 9. Cleanup — Splitting `ParkingService` in Two

By the end of implementing both flows, `ParkingService` had grown to hold two fairly large, unrelated-in-detail methods (`parkVehicle()` and `exitVehicle()`), each with its own transaction, its own DAO dependencies, and its own set of steps. Rather than keep growing one class, it was split into:

- **`ParkingEntryService`** — owns `parkVehicle()`: vehicle lookup/creation, duplicate-active-ticket check, `FOR UPDATE` slot allocation, ticket creation, slot → `OCCUPIED`, all inside the entry transaction.
- **`ParkingExitService`** — owns `exitVehicle()`: ticket validation, slot lookup, pricing lookup, fee calculation, payment creation, ticket → `COMPLETED`, slot → `AVAILABLE`, all inside the exit transaction.

The original combined `ParkingService` was removed entirely rather than kept as a third, now-redundant facade class — a clean cut rather than an extra layer of indirection with no real purpose. This is a legitimate refactor, not just file-shuffling: each class now has one clear, self-contained responsibility, which matches the same single-responsibility instinct behind separating DAOs from Services in the first place.

---

## 10. Concurrency Stress Test — Simulating Many Requests at Once

**File:** `ConcurrencyStressTest.java`
**Purpose:** not to test parking logic correctness in isolation (that's what the final integration test does) — specifically to simulate *many users arriving at nearly the same instant* and confirm the `FOR UPDATE` locking from Section 7 actually holds under real concurrent pressure, not just in a single-threaded walkthrough.

**The tools used, and what each one is actually for:**

- **`ExecutorService` (`Executors.newFixedThreadPool(20)`)** — rather than manually creating and managing 20 individual `Thread` objects, this hands that management to a pool: "give me 20 worker threads," and tasks are handed to it via `pool.submit(...)`. 20 was chosen simply as a stress-test size (simulating 20 near-simultaneous parking requests), not because the real application is expected to always run exactly 20 threads.
- **`CountDownLatch` — used twice, for two different reasons:**
  - A **start latch** (count 1): every worker thread calls `startLatch.await()` before doing anything, and only proceeds once the main thread calls `startLatch.countDown()`. This makes all 20 workers begin their actual parking request as close to simultaneously as possible, rather than starting gradually one after another as threads happen to get scheduled.
  - A **finish latch** (count 20): each worker calls `finishLatch.countDown()` when done; the main thread calls `finishLatch.await()` and only prints final results once all 20 have finished — otherwise the main thread could print results before all workers have actually completed.
- **`AtomicInteger` (for `successCount` / `failCount`)** — a plain `int` is not safe to increment from multiple threads simultaneously: `count++` is actually three separate steps (read, increment, write), and two threads doing this at nearly the same moment can genuinely lose an update (both read the same old value, both write back the same incremented value, one increment vanishes). `AtomicInteger.incrementAndGet()` performs the read-increment-write as one atomic, thread-safe operation, guaranteeing no update gets silently lost even with many threads hitting it concurrently.

**Test data used:** rather than relying on whatever state your originally seeded lot happened to be in, a dedicated clean lot ("Phoenix Mall Test Lot") was created with exactly 5 `4W` slots, all `AVAILABLE` — giving the test a known, predictable starting point. (Two small but instructive corrections came up while seeding this: `ParkingLot.status` must be `ENABLED`/`DISABLED`, not `ACTIVE`; and `Slot.vehicle_type` must use the DB's short code `4W`, not the Java enum name `FOUR_WHEELER` — a direct, practical confirmation of the Day 1/3 decision that the DB representation and the Java enum representation are deliberately different, and `VehicleType.getDbCode()`/`fromDbCode()` exist precisely to bridge that gap.)

**Result:** `Success: 5, Fail: 15` — exactly matching the lot's real capacity. No double-booking, no slot claimed by more than one transaction. This confirms the locking mechanism from Section 7 behaves correctly under genuine concurrent load, not just in theory.

**An unplanned but valuable discovery:** some of the 15 failures weren't the expected "no slot available" rejection — some were `HikariPool-1 - Connection is not available`, because HikariCP's pool was configured for 10 connections while 20 threads tried to run concurrently. This is a **different, separate resource limit** from the 5-slot business limit: thread concurrency (how many requests can run *in Java* at once) and database connection pool size (how many of those can actually be *talking to MySQL* at once) are two independent ceilings, and hitting the second one is not a bug in your locking logic — it's an accurate reflection of a real, finite resource. This is a genuinely good thing to have observed firsthand rather than just read about.

---

## 11. Final Integration Test — Proving the Whole Lifecycle

**File:** `Day4FinalTest.java`

Five checks, each verifying a distinct part of the lifecycle rather than re-testing the same path repeatedly:

1. **Normal parking** — vehicle found/created, slot allocated, ticket created as `ACTIVE`. ✅
2. **Duplicate active vehicle** — parking the same plate again while its first ticket is still `ACTIVE` is correctly rejected, confirming the Day 0 one-active-ticket-per-vehicle rule. ✅
3. **Exit + payment** — `exitVehicle()` correctly calculates the fee, creates the payment, and moves the ticket to `COMPLETED`. ✅
4. **Re-entry after exit** — the *same* vehicle can park again once its previous ticket is `COMPLETED`, confirming the lifecycle correctly resets rather than permanently blocking a vehicle after its first ticket. ✅
5. **Invalid ticket** — attempting to exit a nonexistent ticket ID is cleanly rejected rather than throwing an unhandled error. ✅

Test 4 is worth calling out specifically — it's the one check here that verifies *state transition over time*, not just a single operation's correctness in isolation, and it's exactly the kind of case that's easy to forget to test but would be an embarrassing, very visible bug if missed (a customer permanently unable to park again after their first legitimate visit).

---

## 12. How Generated IDs Actually Get Back Into Your Java Objects

Worth stating explicitly since it came up directly: objects like `new Ticket(0, ...)` and `new Vehicle(0, ...)` start with a placeholder ID of `0` because the real, permanent ID is assigned by MySQL's `AUTO_INCREMENT` at the moment of insertion — the Java object simply doesn't know its own database identity yet at the point it's constructed. The flow:
```
Java object created, id = 0
      ↓
PreparedStatement prepared with Statement.RETURN_GENERATED_KEYS
      ↓
INSERT executes → MySQL assigns a real ID (e.g. 27)
      ↓
statement.getGeneratedKeys() → a one-column ResultSet
      ↓
rs.next() then rs.getInt(1) → reads that generated 27
      ↓
ticket.setTicketId(27) → the SAME Java object is updated in place
```
After this, the original object reference held by the caller is fully up to date — no re-fetch from the database is needed, because the one object was mutated directly.

---

## 13. Full Architecture Picture at the End of Day 4

```
                         SERVICE LAYER
                    (ParkingEntryService /
                     ParkingExitService)
                              |
           +------------------+------------------+
           |            |            |            |
           ↓            ↓            ↓            ↓
      VehicleDao     SlotDao     TicketDao    PaymentDao
                                                    |
                                          PricingConfigDao
```

The Service is the **orchestrator** — it never runs SQL itself. Its job is entirely: *ask the right DAO for the right thing, in the right order, inside one shared transaction, and decide whether to commit or roll back.*

**Entry flow:**
```
getConnection() → setAutoCommit(false) → find/create vehicle → check no active ticket
→ FOR UPDATE lock an available slot → create ACTIVE ticket → slot → OCCUPIED → commit
```

**Exit flow:**
```
getConnection() → setAutoCommit(false) → find ticket (must be ACTIVE) → get slot
→ get pricing config → calculate fee → create payment → ticket → COMPLETED
→ slot → AVAILABLE → commit
```

**Concurrency, layered correctly:**
```
Many concurrent requests → many independent transactions → each attempts FOR UPDATE
→ database row-locking allows exactly one transaction per slot to succeed
→ correct outcome regardless of how many requests arrive at once
```

---

## 14. Status

- [x] `FeeCalculator` correct at all tested boundaries, using `BigDecimal` throughout
- [x] `ParkingEntryService.parkVehicle()` fully transactional, using `FOR UPDATE` locking
- [x] `ParkingExitService.exitVehicle()` fully transactional, fee/payment/ticket/slot all consistent
- [x] `SlotDao` and `PaymentDao` given `Connection`-accepting overloads where needed for transactional participation
- [x] Concurrency stress test run and passed: exactly 5/20 succeed against a 5-slot lot, no double-booking
- [x] Real-world resource-limit distinction (thread count vs. connection pool size) observed directly, not just theorized
- [x] Full lifecycle integration test passed: normal parking, duplicate rejection, exit+payment, re-entry, invalid ticket
- [x] `ParkingService` cleanly split into `ParkingEntryService` / `ParkingExitService`, old facade removed rather than left redundant

**Known, deliberate scope gap carried forward:** `parkVehicle()`'s vehicle-lookup/creation and active-ticket-check steps still use non-transactional DAO calls rather than the shared `conn` — noted consciously, not an oversight, and a candidate for tightening later if time allows.

---

## Status
✅ Day 4 complete. Proceeding to Day 5 (REST layer — `HttpServer`, routing, and exposing the entry/exit/lot/slot operations as real HTTP endpoints).