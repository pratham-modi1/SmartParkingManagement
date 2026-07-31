# Day 1 Report — Database Schema Design

**Project:** Smart Parking Management System
**Scope of Day 1:** Entity definition, relationship mapping, normalization, and final DDL — no code, no database created yet.

---

## Block 1 — Finalized Entities & Attributes

### 1. User
- User ID
- Name
- Email (unique, used for login)
- Password (stored as a hashed value, never plain text)
- Role (ADMIN / ATTENDANT / CUSTOMER)
- Created At

Email is kept because the system supports registration/login. Password is always stored as a hash.

### 2. Vehicle
- Vehicle ID
- Plate Number (unique)
- Vehicle Type (2W / 4W / EV)
- Owner (nullable FK — walk-ins have no account)

Vehicle details live here, not on User — one user can own multiple vehicles.

### 3. ParkingLot
- Parking Lot ID
- Name
- Address / Location
- Status (ENABLED / DISABLED)
- Created At

No `number_of_floors` in V1 — a parking lot simply represents one facility.

### 4. Slot
- Slot ID
- Slot Label (A-12, B-05, etc.)
- Supported Vehicle Type (2W / 4W / EV)
- Status (AVAILABLE / OCCUPIED / OUT_OF_SERVICE)
- Parking Lot ID (FK)

Slot Label is for humans (attendant/customer facing); Slot ID is for the database. Slot Label is only unique **within a lot**, not globally — two different lots can both have a slot labeled "A-01".

### 5. Ticket
- Ticket ID
- Vehicle (FK)
- Slot (FK) — Parking Lot is *not* stored directly here; it's derived through Slot (see Normalization)
- Entry Time
- Exit Time
- Status (ACTIVE / COMPLETED / CANCELLED)
- Created By (FK to User — the attendant or self-service customer who processed it)
- Overstayed Flag
- Lost Ticket Flag

Duration and amount are never stored — both are derived when needed.

### 6. Payment
- Payment ID
- Ticket (FK)
- Amount
- Status (PENDING / SUCCESS / FAILED)
- Paid At

`paid_at` cannot be derived from amount alone — needed for reports, audits, and daily revenue tracking. One ticket can have multiple payment attempts (failed retries are preserved as history, not overwritten).

### 7. PricingConfig
- Config ID
- Base Fee
- Base Hours
- Extra Hour Fee
- Lost Ticket Penalty

Single mutable row in V1. Stores current pricing rules so the admin can change them without modifying code.

---

## Block 2 — Cardinality Map

| From ↓ / To → | User | Vehicle | ParkingLot | Slot | Ticket | Payment | PricingConfig |
|---|---|---|---|---|---|---|---|
| **User** | — | 1:N | — | — | 1:N | — | — |
| **Vehicle** | N:1 | — | — | — | 1:N | — | — |
| **ParkingLot** | — | — | — | 1:N | — | — | — |
| **Slot** | — | — | N:1 | — | 1:N | — | — |
| **Ticket** | N:1 | N:1 | — | N:1 | — | 1:N | — |
| **Payment** | — | — | — | — | N:1 | — | — |
| **PricingConfig** | — | — | — | — | — | — | — |

Notes:
- Cardinality represents the relationship over the entire lifetime of the database, not just the current moment.
- A direct relationship exists only when one entity permanently references another via a foreign key. ParkingLot has no direct FK relationship to Ticket — it's reached through Slot.
- Tables may use data from another table without a direct relationship — e.g. Ticket uses PricingConfig during fee calculation but does not store a `pricing_config_id`.
- Ticket is the central entity connecting users, vehicles, slots, and payments.
- Business rules are distinct from ER relationships — e.g. a slot can have many tickets over time (1:N), but only one ACTIVE ticket at any given moment. This is an application-level rule, not something the schema itself enforces.

---

## Block 3 — Normalization (3NF) Summary

| Table | 3NF Status | Reason |
|---|---|---|
| User | ✅ Yes | All non-key attributes depend directly on `user_id`. No partial or transitive dependencies. |
| Vehicle | ✅ Yes | All attributes depend only on `vehicle_id`. No redundant data. |
| ParkingLot | ✅ Yes | `name`, `location`, `status` etc. depend only on `parking_lot_id`. |
| Slot | ✅ Yes | All attributes depend directly on `slot_id`. `parking_lot_id` is a direct attribute of Slot, not a transitive dependency. |
| Ticket | ✅ Yes | `parking_lot_id` deliberately **not** stored — storing both `slot_id` and `lot_id` would create a transitive dependency (`ticket_id → slot_id → lot_id`). Lot is always derived through Slot. |
| Payment | ✅ Yes | `amount` is intentionally stored as the historical amount actually charged, even though it's technically derivable from Ticket + PricingConfig at calculation time. This preserves financial history even if pricing rules change later — an intentional design decision, not a normalization miss. |
| PricingConfig | ✅ Yes | Stores only pricing configuration values; all attributes depend directly on the primary key. |

All tables are in clean 3NF.

---

## Block 4 — Constraints, Delete Policy & Indexing Strategy

### Composite Uniqueness (Slot)
Slot labels are only unique per lot, not globally — two lots can each have an "A-01". Enforced via:
```sql
UNIQUE (parking_lot_id, slot_label)
```

### Foreign Key Delete Policy

| Relationship | ON DELETE | Reason |
|---|---|---|
| `Vehicle.owner_id → User.user_id` | `SET NULL` | Nullable by design (walk-ins have no owner). Deleting a user account should not delete the vehicle or its ticket history — the vehicle simply becomes ownerless. |
| `Slot.parking_lot_id → ParkingLot.parking_lot_id` | `RESTRICT` | A lot with existing slots cannot be deleted; slots must be removed/reassigned first. |
| `Ticket.vehicle_id → Vehicle.vehicle_id` | `RESTRICT` | Ticket history must never be lost because a vehicle record was deleted. |
| `Ticket.slot_id → Slot.slot_id` | `RESTRICT` | Same reasoning — ticket history must survive. |
| `Ticket.created_by → User.user_id` | `RESTRICT` | The audit trail of who processed a ticket must be preserved. |
| `Payment.ticket_id → Ticket.ticket_id` | `RESTRICT` | Financial records must never silently disappear. |

`SET NULL` is only valid where the column itself is nullable. All other relationships point to `NOT NULL` columns, so `RESTRICT` is both the correct and the only valid choice — it also matches the earlier design decision that lots/slots/tickets with history are disabled, never hard-deleted.

### Indexing Strategy
Indexes are added deliberately, based on real anticipated query patterns rather than pre-emptively on every column:

- `Slot(parking_lot_id, status)` — added now. This backs the core slot-allocation query ("find an available slot of type X in lot Y"), which is central enough to the system that its shape is already certain.
```sql
CREATE INDEX idx_slot_lot_status ON Slot(parking_lot_id, status);
```
- **Ticket indexing intentionally deferred** to Day 3/4, once actual DAO queries are written. The likely candidate is a composite index on `(vehicle_id, status)` to support the "does this vehicle already have an active ticket" check — a single composite index matching the real `WHERE` clause is preferred over separate single-column indexes, since it can serve the equality filter and lookup together more efficiently. This will be finalized once the real query is in front of us, not guessed at now.
- `vehicle.plate_number` and `user.email` are already covered by their `UNIQUE` constraints, which MySQL auto-indexes — no separate index needed.

---

## Block 5 — Final SQL DDL

```sql
CREATE TABLE User (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    email VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','ATTENDANT','CUSTOMER')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Vehicle (
    vehicle_id INT PRIMARY KEY AUTO_INCREMENT,
    plate_number VARCHAR(15) NOT NULL UNIQUE,
    vehicle_type VARCHAR(2) NOT NULL CHECK (vehicle_type IN ('2W','4W','EV')),
    owner_id INT,
    FOREIGN KEY (owner_id) REFERENCES User(user_id)
        ON DELETE SET NULL
);

CREATE TABLE ParkingLot (
    parking_lot_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    location VARCHAR(255) NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('ENABLED','DISABLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Slot (
    slot_id INT PRIMARY KEY AUTO_INCREMENT,
    slot_label VARCHAR(10) NOT NULL,
    vehicle_type VARCHAR(2) NOT NULL CHECK (vehicle_type IN ('2W','4W','EV')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE','OCCUPIED','OUT_OF_SERVICE')),
    parking_lot_id INT NOT NULL,
    UNIQUE (parking_lot_id, slot_label),
    FOREIGN KEY (parking_lot_id) REFERENCES ParkingLot(parking_lot_id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_slot_lot_status ON Slot(parking_lot_id, status);

CREATE TABLE Ticket (
    ticket_id INT PRIMARY KEY AUTO_INCREMENT,
    vehicle_id INT NOT NULL,
    slot_id INT NOT NULL,
    entry_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exit_time TIMESTAMP,
    status VARCHAR(15) NOT NULL CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    created_by INT NOT NULL,
    overstayed_flag BOOLEAN NOT NULL DEFAULT FALSE,
    lost_ticket_flag BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (slot_id) REFERENCES Slot(slot_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (created_by) REFERENCES User(user_id)
        ON DELETE RESTRICT
);

-- Ticket indexes intentionally deferred to Day 3/4 (DAO layer),
-- to be based on actual query patterns rather than assumptions.
-- Likely candidate: composite index on (vehicle_id, status).

CREATE TABLE Payment (
    payment_id INT PRIMARY KEY AUTO_INCREMENT,
    ticket_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    paid_at TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES Ticket(ticket_id)
        ON DELETE RESTRICT
);

CREATE TABLE PricingConfig (
    config_id INT PRIMARY KEY AUTO_INCREMENT,
    base_fee DECIMAL(10,2) NOT NULL,
    base_hours INT NOT NULL DEFAULT 3,
    extra_hour_fee DECIMAL(10,2) NOT NULL,
    lost_ticket_penalty DECIMAL(10,2) NOT NULL
);
```

---

## Status
✅ Day 1 complete — schema fully designed, normalized to 3NF, delete policy and slot indexing finalized. Ticket-level indexes deliberately deferred to Day 3/4 pending real DAO query patterns. Database not yet created — DDL written and reviewed only. Proceeding to Day 2 (project setup, connection pool, MySQL instance creation).