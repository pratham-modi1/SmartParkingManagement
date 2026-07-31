# Day 0 Report — System Understanding & Design Decisions

**Project:** Smart Parking Management System
**Scope of Day 0:** No code, no schema — pure domain understanding and decision-locking before design begins.

---

## 1. Design Philosophy

The system follows **Role-Based Access Control (RBAC)**. Each actor has a clearly separated responsibility, even where a role could technically perform another's task in real life — separation improves security, maintainability, and auditability.

Three operational roles: **ADMIN**, **ATTENDANT**, **CUSTOMER**.

---

## 2. Actor Definitions

### ADMIN
Represents the organization managing the facility (e.g. mall parking manager, campus admin).

**Can:**
- Manage parking lots (create/update/enable-disable)
- Manage slots (add/remove, mark OUT_OF_SERVICE, view status) — **cannot modify an occupied slot until it's free**
- Manage pricing configuration
- View reports (occupancy, revenue, active tickets, historical data)
- Manage attendant accounts (create/disable/reset password); manage customer accounts if needed

**Cannot:**
- Perform vehicle entry/exit
- Use the attendant workflow
- Manually create/close tickets during normal operation

**Note:** Admin account creation itself is out of scope for the app — the first admin is a **seed admin inserted directly during setup** (DB script), documented in the README. The app does not provide a "create first admin" endpoint.

### ATTENDANT
Represents operational staff at the gate (booth operator, security guard).

**Can:**
- Register vehicle entry for walk-ins or assisted registered customers (system decides slot allocation, never the attendant)
- Register vehicle exit (confirm payment, close ticket, free slot)
- Handle lost-ticket cases (search by plate number, apply lost-ticket penalty, close ticket, release slot)
- View operational info (available slots, active tickets, currently parked vehicles)
- Cancel a ticket **only while it is still ACTIVE** (mistaken entry) — cannot touch completed records

**Cannot:**
- Manage lots, slots, pricing, reports, or user accounts
- Cancel completed tickets

### CUSTOMER
The driver. Account is **optional** — walk-ins without accounts are fully supported via the attendant workflow. A customer account does not reserve a slot; it only provides identity + self-service access.

**Can:**
- Register/login, register personal vehicles
- Request entry/exit for their own vehicle
- View their own active ticket, duration, and history
- View available slots before entering

**Cannot:**
- View or act on another customer's data or vehicles
- Manage lots, slots, pricing, users, or reports

**Note:** Registered customers may still choose to use the attendant instead of self-service — both paths use identical business logic underneath.

---

## 3. User Journeys

**Customer (self-service):**
Register → Login → Register Vehicle (one-time) → View Available Parking → Request Entry → System verifies vehicle has no active ticket → Slot allocated → Ticket created → Park → View active ticket/duration → Request Exit → Fee calculated → Payment → Ticket COMPLETED → Slot released → Exit

**Attendant-assisted:**
Customer arrives → Attendant enters vehicle details → System checks for existing vehicle by plate (reuse if found, else create new — **never duplicate**) → System verifies availability & no active ticket → Slot allocated → Ticket created → Customer parks → returns → Attendant retrieves ticket (or searches by plate if lost) → Fee calculated (+ ₹70 penalty if lost-ticket case) → Payment confirmed → Ticket COMPLETED → Slot released → Exit

**Admin:**
Login → Manage attendant accounts → Create parking lots → Add/configure slots → Configure pricing → Monitor occupancy → View reports → Mark slots OUT_OF_SERVICE / restore after maintenance

---

## 4. Pricing Rules

- **0–180 minutes (inclusive):** ₹20 flat fee
- **Beyond 180 minutes:** every started hour (rounded up) adds ₹10
- **No daily fee cap** in V1
- **Overstay (>24h):** vehicle flagged as overstayed; no automatic penalty — admin decides action
- **Lost-ticket penalty:** ₹70 flat, **additive** — added on top of the normal duration-based fee (not a replacement). Duration is still known from the ticket's stored `entry_time`, even without the physical ticket, so the base fee is always calculable.

---

## 5. Ticket Lifecycle

**States:** `ACTIVE` → `COMPLETED` (successful payment & exit) or `CANCELLED` (operational mistake, no payment)

**Rules:**
- Ticket is ACTIVE immediately on creation — no separate "created" state
- COMPLETED and CANCELLED are final — no backward transitions
- Cancelled tickets carry no payment (they represent errors, not real sessions)
- **Every ticket stores `created_by_user_id`** — the acting user (attendant or self-service customer) — for accountability/audit
- **Slot is released back to AVAILABLE the moment a ticket becomes COMPLETED or CANCELLED** — no exceptions

---

## 6. Slot Allocation Strategy

**First available slot matching the requested vehicle type**, within the requested lot. No nearest-slot logic, no preference weighting — kept simple deliberately for V1.

---

## 7. Payment Model

**Simulated — no external payment gateway.** A payment step (SUCCESS/FAILED, retryable) is modeled internally purely to demonstrate the failure/retry logic; no third-party integration is in scope. Failed attempts are preserved as history (a ticket can have multiple payment records over time, not one row overwritten on retry).

---

## 8. Edge Cases (Finalized List)

1. Duplicate entry request → reject (vehicle cannot hold more than one ACTIVE ticket)
2. No slot available for requested vehicle type → reject
3. Parking lot full → reject
4. Concurrent requests for the last slot → only one succeeds (handled via concurrency control)
5. Invalid/non-existent ticket at exit → reject; attendant resolves manually
6. Lost ticket → supported; attendant searches by plate number, ₹70 penalty applied additively
7. Admin modifies an occupied slot → not allowed until slot is free
8. Wrong vehicle plate entered → cancel ticket immediately, no payment
9. Exit attempted on a COMPLETED/CANCELLED ticket → reject
10. Payment failure → fee stays pending, barrier logically stays closed, retry allowed
11. Vehicle parked >24 hours → flagged as overstayed, admin notified
12. Customer tries to access another customer's data → reject
13. Unauthorized access to protected endpoints → reject
14. Duplicate vehicle registration → plate numbers must be unique
15. Database unavailable → reject requests (offline/manual mode is out of scope for V1)
16. **Attendant creates walk-in entry for a plate that already belongs to a registered customer** → system looks up the vehicle by plate; reuses the existing vehicle record instead of creating a duplicate

**Additional rule:** Vehicles may enter through any entry gate and exit through any exit gate within the same lot (no gate-specific binding).

---

## Status: ✅ Day 0 complete — all system design decisions locked. Proceeding to Day 1 (Database Schema Design).