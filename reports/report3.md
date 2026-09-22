# Day 3 Report — Model Classes & DAO Layer

**Project:** Smart Parking Management System
**Scope of Day 3:** All 7 entity classes, all 6 enums, and all 7 DAOs (interface + implementation), tested against the real database. No business logic yet — this is pure data-access plumbing.

---

## 1. Why Enums Exist — the Core Design Reason

Several DB columns (`role`, `status`, `vehicle_type`, etc.) are constrained to a fixed, closed set of values — enforced on the DB side with `CHECK (... IN (...))`. The corresponding Java field needs the same guarantee, but a plain `String` field gives you none of that: nothing stops `"admn"` or `"Admin"` from compiling and sitting silently wrong until it hits the DB (or worse, matches nothing and fails a comparison silently).

An `enum` is Java's mechanism for a **fixed type with a closed set of legal values** — `Role.ADMIN`, `Role.ATTENDANT`, `Role.CUSTOMER` and nothing else can exist. This converts what would be a runtime bug into a **compile-time error**, and lets the compiler verify `switch` statements are exhaustive. In short: **the enum exists so that one property of an entity can only ever hold one of a fixed set of valid values — it's the Java-side mirror of the DB's `CHECK` constraint, but enforced earlier and more strongly.**

One nuance handled today: most enums (`Role`, `LotStatus`, `SlotStatus`, `TicketStatus`, `PaymentStatus`) map 1:1 to their DB string values, so `.name()` / `.valueOf()` is enough. `VehicleType` is the exception — the DB stores short codes (`'2W'`, `'4W'`, `'EV'`) while the Java enum uses descriptive names (`TWO_WHEELER`), so it carries its own `dbCode` field plus a `fromDbCode()` lookup method to convert both directions explicitly, rather than assuming the strings match.

---

## 2. Entities — What They Are, Conceptually

Each entity class is the Java-side mirror of one DB table — one class, one row shape. Their only job is to **hold data**: private fields matching the table's columns, a no-arg constructor (needed by some frameworks/patterns even though this project doesn't use one directly, and useful for building objects incrementally), a full constructor, and getters/setters. No SQL, no business logic lives here — that separation is deliberate, and matters more once the service layer (Day 4) starts making decisions using this data.

Two type-choices worth calling out explicitly:
- **`LocalDateTime`, not `java.util.Date`**, for every timestamp — the modern, correctly-designed Java time API.
- **`BigDecimal`, not `double`**, for every money field (`amount`, `baseFee`, etc.) — `double` uses binary floating-point internally, which cannot represent many decimal values exactly, leading to classic rounding errors in financial calculations. `BigDecimal` represents decimal values exactly, which is why the DB column is `DECIMAL(10,2)` and the Java type must match that guarantee, not silently degrade it.

One nullable field required special handling: `Vehicle.ownerId` is `Integer` (the boxed wrapper type), not primitive `int` — because primitives can't represent `null`, and this field genuinely needs to represent "no owner" for walk-in vehicles.

---

## 3. The DAO Layer — Core JDBC Concepts, Explained Once (Applies Everywhere)

Every DAO implementation follows the exact same shape. Rather than re-explain it seven times, here's the pattern once, since it's genuinely identical everywhere it appears.

### `Connection`
Represents one live link to the database, borrowed from the HikariCP pool via `DBConfig.getConnection()`. All SQL for a given operation happens through this object. When `.close()` is called on it (via try-with-resources), it doesn't actually disconnect — it returns to the pool (see the Day 2 report for the full reasoning).

### `PreparedStatement`
A precompiled SQL statement with `?` placeholders instead of raw concatenated values. Two reasons this is used everywhere instead of plain `Statement`:
1. **SQL injection prevention** — user-supplied values are never directly concatenated into the SQL string; they're bound safely via `.setString()`, `.setInt()`, etc.
2. **Type-safe binding** — `statement.setInt(1, vehicleId)` handles the correct SQL type conversion, rather than manually formatting values into a string.

The number passed to each `setXxx()` call (`1`, `2`, `3`...) refers to the position of the `?` in the SQL string, left to right, **starting at 1, not 0** — a common source of off-by-one bugs if you're used to array indexing.

### `.executeQuery()` vs `.executeUpdate()`
- `.executeQuery()` — used for `SELECT` statements; returns a `ResultSet` (rows coming back).
- `.executeUpdate()` — used for `INSERT`/`UPDATE`/`DELETE`; returns an `int` (number of rows affected), not a `ResultSet`.

### `ResultSet` — the part that trips people up first
A `ResultSet` represents the rows returned by a query, but it does **not** start positioned on the first row. Think of it as a **cursor that starts *before* the first row.**

```java
try (ResultSet rs = statement.executeQuery()) {
    while (rs.next()) {
        // now positioned on a real row
        int id = rs.getInt("column_name");
    }
}
```

- **`rs.next()`** moves the cursor forward by one row and returns `true` if there was a row to move to, `false` if there are no more rows. This is why every read starts with either `if (rs.next())` (expecting at most one row — `findById`) or `while (rs.next())` (expecting zero or more rows — `findAll`). Calling `rs.getXxx()` *before* calling `rs.next()` at least once is a bug — the cursor isn't on a row yet.
- **`rs.getInt("column_name")`** (or `getString`, `getTimestamp`, `getBigDecimal`, `getBoolean`, etc.) reads a column's value *from the row the cursor is currently sitting on*. Columns can be read either by name (`rs.getInt("user_id")`, used throughout this project for readability) or by position (`rs.getInt(1)`, used specifically when reading `getGeneratedKeys()`, since that result set only ever has one column).

### `Statement.RETURN_GENERATED_KEYS` + `getGeneratedKeys()`
Used on every `create()` method. When inserting a row with an `AUTO_INCREMENT` primary key, the Java object doesn't know its own ID yet — the DB assigns it at insert time. Passing `Statement.RETURN_GENERATED_KEYS` when preparing the statement tells JDBC to capture that generated ID, retrievable afterward via `statement.getGeneratedKeys()` — itself a small one-column `ResultSet`, read the same way (`rs.next()` then `rs.getInt(1)`).

### mapRow() — expanded

Every table your app touches needs to travel in two directions: Java → DB (writing — handled directly in create/update via statement.setX()) and DB → Java (reading — handled by mapRow()). The read direction is the one that needs a dedicated helper, because a ResultSet is a raw, column-by-column, loosely-typed view of a row — it is not usable as a domain object on its own. Nothing in your ParkingService (tomorrow) should ever touch a ResultSet directly; the service layer should only ever work with clean Ticket, Slot, Vehicle objects. mapRow() is the single conversion boundary that makes that possible — it's the one place where "a row of raw columns" becomes "a real Java object with proper types" (enums instead of strings, LocalDateTime instead of Timestamp, BigDecimal instead of raw decimal strings, Optional/null handled correctly for nullable columns).

Where it's actually used: every method in a DAO that reads one or more rows back — findById, findAll, findByPlateNumber, findActiveByVehicle, findFirstAvailableForUpdate, and so on — all end the same way: position the cursor on a row with rs.next(), then call mapRow(rs) to build the object. Without this helper, that same 6-10 line block of rs.getInt(...), rs.getString(...), enum conversion, null-timestamp-checking logic would be copy-pasted into every one of those methods separately. The moment your schema changes (say you add a column to Ticket), you'd have to find and fix that mapping logic in five or six different places instead of one. mapRow() exists purely to centralize that risk into a single spot.

### rs.wasNull() / Optional — expanded, with "where it matters"

These solve two different problems that look similar but aren't:

rs.wasNull() solves a type problem — Java's primitive int literally cannot represent the concept of "no value," so rs.getInt("owner_id") has no honest way to tell you the column was SQL NULL versus genuinely 0. It returns 0 either way. This isn't a hypothetical edge case in your project — it's guaranteed to happen constantly, because every walk-in vehicle (no linked account) has owner_id = NULL by design, per your Day 0 decision. If you skip the wasNull() check, every walk-in vehicle would silently look like it's owned by "user ID 0" — and if your seed admin or first real user ever happens to have user_id = 1 and something else assumes unset means 0... you'd get subtly wrong ownership data with no error, no exception, nothing — just quietly incorrect. wasNull() converts that silent wrong answer into an honest null, which then correctly becomes Optional.empty() or a null field, something calling code is forced to consider.

### Optional solves a contract problem, one level up — it's about what a method promises to its caller. 
findByPlateNumber might legitimately find nothing (new plate, never seen before) — that's not an error, it's an expected, normal outcome you'll hit on basically every walk-in entry. Returning Optional<Vehicle> instead of a nullable Vehicle forces whoever calls it (tomorrow, that's ParkingService.parkVehicle()) to explicitly branch: .isPresent() → reuse the vehicle, or .isEmpty() → create a new one. If it returned a plain possibly-null Vehicle instead, it's entirely possible to forget the null-check and call .getVehicleId() on a null, and you'd get a NullPointerException deep inside a transaction — exactly the kind of bug that's easy to miss in testing and shows up for the first time in front of someone during a demo. Optional makes "this might not exist" part of the method's type signature, not just a comment or a hope that the caller remembers.

### Transactional variants — expanded, with a concrete walk-through

The core problem: a database transaction is defined by one single Connection object — everything you want to succeed or fail together must run through that same connection, between one setAutoCommit(false) and one final commit()/rollback(). If any DAO method quietly calls DBConfig.getConnection() internally (as all your simple no-arg methods do), it's grabbing a different connection from the pool — which means it's running in its own separate, independent transaction that MySQL treats as completely unrelated to anything else happening at the same time.

Concrete walk-through of why this actually matters, using parkVehicle tomorrow:

1. Check findActiveByVehicle — no active ticket, good, continue
2. Lock and fetch an available slot (findFirstAvailableForUpdate)
3. Insert the new ticket
4. Update the slot to OCCUPIED
Suppose step 4 fails (some constraint issue, connection blip, anything) — you now need step 3's insert to also be undone. If step 3 used its own self-contained connection and had already committed independently, there's no way to undo it — you'd be left with an inserted ACTIVE ticket pointing at a slot that's still marked AVAILABLE, an inconsistent, broken state with no way to automatically recover.

The Connection-accepting overloads exist so the service layer holds one connection for the entire operation and passes that same connection into every DAO call it makes along the way — meaning all four steps above either all commit together or all roll back together, with no in-between broken state possible.

This is also exactly why findFirstAvailableForUpdate specifically needs the Connection parameter, not just for consistency but out of necessity: SELECT ... FOR UPDATE locks the row only until that transaction ends. If it ran on its own throwaway connection with autoCommit(true) (the default), the lock would be released the instant the query finished — before your code even gets to the ticket-insert step — which defeats the entire purpose of taking the lock in the first place. The lock is only meaningful because it's held across every step of parkVehicle, using one shared connection, until the final commit.

## Status
✅ Day 3 complete. Proceeding to Day 4 (Service layer, JDBC transactions, and thread-safe concurrent slot allocation).