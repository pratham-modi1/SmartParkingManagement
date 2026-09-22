# Day 7 Report — JWT Authentication Foundation

**Project:** Smart Parking Management System
**Scope of Day 7:** Add the first real authentication layer — password hashing, user registration, login, and JWT generation/validation. Route protection (RBAC) is explicitly **not** part of this day — that's Day 8.

---

## 1. What Day 7 Actually Changed

Before today, the system could store a `User` row, but had no way to *prove* who was making a request — every endpoint was equally open to anyone. Day 7 added the missing piece: a way for a person to **register**, **prove their password** on login, and receive a **signed token** representing that proof, which future requests can present instead of re-sending credentials every time.

```
REGISTER: raw password → BCrypt → hash → stored in MySQL
LOGIN:    email + password → lookup → BCrypt verify → JWT issued
```

Nothing about *enforcing* auth on existing endpoints was touched today — `POST /vehicles/entry`, `GET /parking-lots`, etc. are still wide open. That enforcement is explicitly Day 8's job.

---

## 2. Why BCrypt, and What It Actually Guarantees

**The problem:** storing a raw password (`"hello123"`) directly means anyone with database access can read every user's password immediately. BCrypt is a **one-way hashing function** — there is no operation that takes a BCrypt hash and recovers the original password. If a database is ever compromised, an attacker can't "decrypt" the hashes; the only path is repeatedly guessing candidate passwords and hashing each guess to see if it matches — and BCrypt is deliberately designed to make that computationally expensive (it incorporates a salt and a tunable work factor), which is the entire point of using it over something like plain SHA-256.

**The two operations, and why they're separate methods, used in different places:**
- **`BCrypt.hashpw(rawPassword, salt)`** — used only at **registration**. One direction: password → hash. This is what gets stored.
- **`BCrypt.checkpw(rawPassword, storedHash)`** — used only at **login**. Takes what the user just typed plus the stored hash, and returns `true`/`false`. This is **verification, not decryption** — it does not recover or expose the original password at any point; it just confirms the entered password *would* produce that same hash.

```
REGISTER: password → hashpw() → store hash
LOGIN:    password + stored hash → checkpw() → true/false
```
Keeping these as two distinctly-named operations (rather than one generic "encrypt/decrypt" pair) is itself worth remembering — it reflects that hashing and encryption are fundamentally different operations with different guarantees, and BCrypt is a hashing scheme, not an encryption scheme.

---

## 3. What Already Existed and Needed No Changes

A genuinely good sign for how Day 2–3's design held up: several pieces needed by auth were already correctly in place before today —
- **`User` entity** already had `passwordHash` (not a plaintext password field) — no changes needed.
- **`UserDao.findByEmail(String)` → `Optional<User>`** already existed from Day 3, and turned out to be exactly the lookup both registration (duplicate check) and login (credential lookup) needed — no DAO changes required at all.

This is worth noting explicitly: good separation of concerns on Day 3 (DAOs built around real anticipated query needs, not just generic CRUD) paid off directly here.

---

## 4. Dependencies Added

- **`org.mindrot:jbcrypt`** — password hashing/verification.
- **`com.auth0:java-jwt`** — JWT creation, signing, verification, and claim extraction.

**Known cleanup item, not a functional problem:** the project's `pom.xml` already had a JJWT dependency added earlier (back on Day 2, added preemptively before it was needed). Today's actual implementation uses Auth0's `java-jwt` instead. So there are currently **two JWT library families present** in the build, only one of which is actually used. This should be cleaned up (remove the unused JJWT dependency) during the Day 9 cleanup pass — it doesn't break anything today, but an unused dependency is exactly the kind of loose end worth tidying before final submission.

---

## 5. JWT Configuration

Two new config values, following the exact same externalization pattern established on Day 2 (`application.properties` → `DatabaseProperties` → consumer class):
```
jwt.secret=<real secret, in the git-ignored real file>
jwt.expiryMinutes=60
```
`DatabaseProperties` was extended with `getJwtSecret()` / `getJwtExpiryMinutes()`, and `Main` reads these and passes them into `JwtUtil`'s constructor — so the secret is never hardcoded inside `JwtUtil` itself, consistent with the "never hardcode credentials/secrets" principle from Day 2.

**Why 60 minutes specifically, and the underlying principle:** the number itself is just a reasonable project/demo choice, not a rule — the actual principle is a trade-off: a shorter expiry limits how long a *stolen* token remains useful, while a longer expiry is more convenient (fewer re-logins) but extends that same window of risk. 60 minutes is a sensible middle ground for a project like this.

**A clarified doubt worth keeping precise:** expiry does not mean the token vanishes from the client. A client (Postman, a browser, whatever) can still physically hold and send an expired token in the `Authorization` header — the server is what rejects it, by checking the `exp` claim on every validation and returning `401` once that timestamp has passed. "Expired" means *the server will now refuse it*, not *the token no longer exists anywhere*.

---

## 6. Registration Flow — `POST /auth/register`

**New DTOs, and why they exist (same principle as `EntryRequest`/`ExitResponse` from Day 6 — entity shape and API shape aren't obligated to match):**

- **`RegisterRequest{name, email, password, role}`** — represents exactly what a client is allowed to send. Notably: `password` (raw), not `passwordHash` — the client never handles or sees a hash, only the plaintext they typed.
- **`UserResponse{userId, name, email, role, createdAt}`** — represents what's safe to send back. Built via `UserResponse.fromUser(user)`, which deliberately **excludes `passwordHash`**. Returning the raw `User` entity directly from the API would leak the hash to the client — not the plaintext password, but still information that should never leave the server. This is a real, meaningful security habit: never return password hashes through an API response, even hashed ones.

**`AuthService.register()` steps, in order:**
1. `userDao.findByEmail(email)` — if a user already exists, throw a clean "email already registered" exception **before** attempting any insert.
2. `BCrypt.hashpw(rawPassword, BCrypt.gensalt())` — produces the hash, entirely inside Java, inside the Service layer (not in the DAO, not in MySQL, not in the Handler).
3. Build the `User` object with `passwordHash` set (never the raw password).
4. `userDao.create(user)` — same generated-key pattern as every other entity: object starts with `userId = 0`, the DAO's `INSERT ... RETURN_GENERATED_KEYS` retrieves the real ID, and `user.setUserId(...)` writes it back into the same object.
5. `UserResponse.fromUser(user)` — strip the hash, return only safe fields.

**Why the application checks for a duplicate email itself, rather than just relying on the DB's `UNIQUE` constraint:** both layers exist deliberately, for different reasons. The DB constraint is the final, unbypassable integrity guarantee — no code path, however buggy, can ever actually insert a duplicate email. The application-level check exists purely to produce a **clean, specific business error** ("email already registered") *before* attempting the insert, rather than letting a raw `SQLIntegrityConstraintViolationException` bubble up and have to be caught and translated after the fact. Both layers are doing real, non-redundant work.

**Full trace:**
```
Postman → HttpServer → HttpExchange → Router matches POST /auth/register
   → Handler reads body → Gson → RegisterRequest
   → AuthService.register()
       → UserDao.findByEmail() → not found, proceed
       → BCrypt.hashpw() → passwordHash
       → User object built → UserDao.create() → INSERT → generated userId
       → UserResponse.fromUser()
   → ApiResponse.success(userResponse) → ResponseUtil → JSON → Postman
```
**Verified:** successful registration returns `201`-style success with `userId/name/email/role/createdAt` and **no password field anywhere in the response**; a duplicate email attempt returns a clean application-level error rather than a raw SQL exception surfacing to the client.

---

## 7. Login Flow — `POST /auth/login`

**New DTO:** `LoginRequest{email, password}` — intentionally minimal; the client never sends `userId` or `role`, since both of those are looked up server-side from the authoritative `User` record, not trusted from client input. This matters: if a client could just claim `"role": "ADMIN"` in a login request and have that honored, authorization would be meaningless — the role a token carries must always come from the database, never from what the caller asserts.

**`AuthService.login()` steps:**
1. `userDao.findByEmail(email)` — if no user exists, throw a **generic** "invalid credentials" exception.
2. `BCrypt.checkpw(rawPassword, user.getPasswordHash())` — if `false`, throw the **exact same generic exception** as step 1.
3. If both pass: `jwtUtil.generateToken(user)` → return the JWT.

**Why steps 1 and 2 deliberately produce an identical error message:** if a wrong email produced "no such user" while a wrong password produced "incorrect password," an attacker could use that difference to enumerate which emails have accounts on the system — a genuine, well-known security anti-pattern. Collapsing both cases into one generic "invalid credentials" response closes that information leak. This is a real security practice worth being able to explain, not an arbitrary choice.

**Full trace:**
```
Postman → HttpServer → HttpExchange → Router matches POST /auth/login
   → Handler → Gson → LoginRequest
   → AuthService.login()
       → UserDao.findByEmail() → User found
       → BCrypt.checkpw() → true
       → JwtUtil.generateToken(user) → signed JWT
   → ApiResponse.success({ token }) → ResponseUtil → JSON → Postman
```

---

## 8. `JwtUtil` — Token Generation, Validation, Claim Extraction

Kept as a single-responsibility class in `security/JwtUtil.java` — it does JWT operations only. No SQL, no password hashing, no HTTP handling lives here; those all stay in their own layers.

**`generateToken(User user)`:**
```
User → JWT.create()
        .withClaim("userId", user.getUserId())
        .withClaim("role", user.getRole().name())
        .withClaim("email", user.getEmail())
        .withExpiresAt(<now + expiryMinutes>)
       .sign(Algorithm.HMAC256(secretKey))
     → JWT string
```
**Deliberately excluded from the payload:** the password or password hash. The JWT's job is to represent *"this request comes from an already-authenticated user with this identity and role,"* not to carry credentials — the password's entire job was already done, once, at the login step. Putting a password (hashed or not) inside a JWT payload would be a real security mistake, since JWT payloads are not encrypted (see below).

**`validateToken(String token)`:** re-verifies the signature using the same secret, and checks the `exp` claim against the current time. Returns valid/invalid (or throws a clear exception on failure) — this is what a future request-protecting filter (Day 8) will call before trusting anything else about the token.

**`extractUserId()` / `extractRole()` / `extractEmail()`:** read specific claims back out of a token, for use once route protection exists.

**An important internal distinction clarified today: `decode` vs. `verify`.** Simply decoding a JWT means reading its payload — this does **not** prove the token is legitimate or unmodified; anyone can decode any JWT-shaped string without the secret. The secure order must always be: **validate first, extract claims only if validation passed.** Calling claim-extraction on an unvalidated token and trusting the result would completely defeat the purpose of signing it in the first place — this is a subtle but important trap to avoid when Day 8 builds the actual auth filter.

---

## 9. Understanding the Generated Token Itself

A JWT has three dot-separated parts: `HEADER.PAYLOAD.SIGNATURE`.

- **Header** — identifies the signing algorithm used (`HS256`, matching `Algorithm.HMAC256(...)` in the code) and that this is a JWT.
- **Payload** — the actual claims: `userId`, `role`, `email`, `exp`. For the seed admin specifically: `userId=1, role=ADMIN, email=admin@parking.com, exp=<future timestamp>`.
- **Signature** — computed from `header + payload + secret` via HMAC256. This is what lets the server detect tampering: if anyone modifies the payload (say, changing `role: CUSTOMER` to `role: ADMIN`) without knowing the secret, the signature will no longer match on validation, and the token is correctly rejected.

**Critical point, worth restating precisely:** the payload is **encoded, not encrypted**. Anyone holding a JWT can decode its payload and read the claims in plain text — Base64 decoding a JWT payload takes seconds and requires no secret at all. The signature does not hide the contents; it only proves *whether the contents have been altered since signing*. This is exactly why nothing sensitive (passwords, hashes, personal data beyond what's needed for authorization) belongs in a JWT payload — anyone who intercepts the token can read everything inside it.

**Known gap, worth closing before Monday:** the actual manual "paste a real token into jwt.io and visually confirm header/payload/signature" step was skipped today due to time pressure. The token generation and validation logic was tested and confirmed working through the actual login flow (Section 10 below), so functionally this isn't a gap — but doing that one manual decode-and-inspect pass is cheap (2 minutes) and directly rehearses the exact explanation you're most likely to be asked to give live in the interview. Worth doing once before Monday even outside the regular day plan.

---

## 10. Seed Admin — a Real Bug Found and Fixed

This was flagged as a risk back in the Day 2 report ("confirm you can actually recall the seed admin's plaintext password") — and today it turned out to be a real, live issue, not a hypothetical one.

**The problem:** the seed admin's `password_hash` column, inserted manually back on Day 2 before any Java hashing code existed, was not a valid BCrypt hash corresponding to the password being tested against it during login.

**How it was diagnosed — a good example of methodical debugging worth remembering:** rather than guessing, the actual `BCrypt.checkpw()` relationship between the test password and the stored hash was checked directly, which returned `false` — confirming the issue was specifically the hash/password pairing, not a bug in the login logic, the JWT code, or anything else in the auth flow. Isolating *which layer* actually has the problem (data vs. logic) before trying to fix anything is the right instinct, and it's exactly what happened here.

**The fix:** a proper BCrypt hash was generated using the real BCrypt library (not guessed or hand-written), and `User.password_hash` for the seed admin row was updated directly in MySQL to that known-correct hash.

**Result, confirmed:** `POST /auth/login` with the admin's credentials now succeeds end-to-end — `findByEmail()` → `checkpw()` returns `true` → `generateToken()` produces a real JWT with `role: ADMIN`. This closes out a loose end flagged two reports ago and confirms the seed admin — needed for Day 8's role-restricted endpoint testing — actually works.

---

## 11. Architecture After Day 7

```
                     Main
                      │
        ┌─────────────┴────────────┐
        │                          │
      Router                    AuthService
        │                          │
     Handler                  ┌────┴────┐
        │                     │         │
        ↓                  UserDao   JwtUtil
   ApiResponse                 │         │
        ↓                    MySQL      JWT
  ResponseUtil
```

New files today: `dto/RegisterRequest.java`, `dto/LoginRequest.java`, `dto/UserResponse.java`, `service/AuthService.java`, `security/JwtUtil.java`. `DatabaseProperties` extended (not replaced) to also expose JWT config. No existing entity, DAO, or previously-built endpoint required any changes.

---

## 12. Explicit Scope Boundary — What Day 7 Deliberately Does NOT Include

This is worth stating plainly, since it's the natural next question: the system can now **authenticate** (register, login, issue and validate tokens) — but nothing yet **enforces** authentication on any existing endpoint. `POST /vehicles/entry`, `GET /parking-lots`, `POST /parking-lots` (admin-only, intended) are all still completely open to unauthenticated requests. A valid JWT exists and can be validated, but nothing currently checks for one before running a handler. That enforcement — the auth filter, wiring it into every endpoint, and role-based restrictions — is entirely Day 8's scope, not a gap in today's work.

---

## 13. Status

- [x] BCrypt + Auth0 JWT dependencies added (known cleanup: remove now-unused JJWT dependency during Day 9)
- [x] JWT config (`jwt.secret`, `jwt.expiryMinutes`) externalized, following the existing Day 2 config pattern
- [x] `POST /auth/register` — duplicate email cleanly rejected pre-insert, password always hashed before storage, response never includes the hash
- [x] `POST /auth/login` — generic "invalid credentials" on both wrong-email and wrong-password (no user enumeration), real JWT issued on success
- [x] `JwtUtil` — generation, validation, and claim extraction implemented and understood, including the decode-vs-verify distinction
- [x] Seed admin login bug found, correctly diagnosed, and fixed — admin can now authenticate through the real flow
- [ ] Manual jwt.io decode-and-inspect pass — skipped today for time, flagged as a cheap pre-interview task, not a functional gap
- [ ] Remove unused JJWT dependency from `pom.xml` — cosmetic cleanup, deferred to Day 9

---

## Status
✅ Day 7 complete — authentication foundation fully working (register, login, JWT issue/validate). Proceeding to Day 8 (RBAC enforcement, wiring auth into every existing endpoint, and the planned flaw fixes).