# Day 2 Report — MySQL Setup, Maven Configuration & Connection Layer

**Project:** Smart Parking Management System
**Scope of Day 2:** Turn the Day 1 schema into a real, running MySQL database, and build a Maven project with a working, pooled connection to it — no business logic yet.
**How to read this doc:** Each section below is written so it can be replicated from scratch — what was done, in what order, why each piece exists, and what each file/method is actually doing internally. This isn't just a summary; it's a rebuild guide.

---

## PART A — Database Setup

### A.1 — Access Control First (DCL)

SQL work generally falls into four categories. Day 2 touched three of them, starting with the one people usually skip thinking about:

| Category | Full Form | Purpose | Where it showed up |
|---|---|---|---|
| **DDL** | Data Definition Language | Defines structure | `CREATE TABLE`, `CREATE INDEX` |
| **DML** | Data Manipulation Language | Manipulates data | `INSERT` (seed data) |
| **DCL** | Data Control Language | Controls access/permissions | `CREATE USER`, `GRANT` |
| **TCL** | Transaction Control Language | Manages commit/rollback | Not yet used — Day 4 |

**Step-by-step, exactly what was run:**

```sql
CREATE DATABASE smart_parking_db;

CREATE USER 'parking_app'@'localhost'
IDENTIFIED BY 'your_chosen_password';

GRANT ALL PRIVILEGES
ON smart_parking_db.*
TO 'parking_app'@'localhost';

FLUSH PRIVILEGES;

SHOW GRANTS FOR 'parking_app'@'localhost';

USE smart_parking_db;
```

**Line-by-line reasoning:**
- `CREATE DATABASE` — makes an empty container to hold this project's tables, separate from anything else that might exist on the same MySQL server.
- `CREATE USER` — creates a brand-new MySQL account, `parking_app`, that exists **only inside MySQL** — it has nothing to do with your OS login or any Java-level user. `'parking_app'@'localhost'` means this account is only allowed to connect from `localhost` — an extra restriction worth noting.
- `GRANT ALL PRIVILEGES ON smart_parking_db.*` — the critical part is `smart_parking_db.*`. This scopes every privilege to *only* this one database (`*` = all tables/objects within it), not the whole server. This is the **Principle of Least Privilege**: give an account exactly the access it needs and nothing more.
- `FLUSH PRIVILEGES` — reloads MySQL's internal privilege tables so the grant takes effect immediately rather than waiting for the next server restart.
- `SHOW GRANTS` — a verification step, not a required one — confirms the permissions actually landed as expected, rather than assuming the `GRANT` worked.

**Why not just use `root` for the app?** `root` can create/drop any database, create/drop any user, and change server-wide settings. None of that is something the application layer should ever be capable of — if the app's credentials ever leaked or a query had a bug, the blast radius with `root` is the entire server; with `parking_app`, it's limited to one database. This is exactly the same reasoning production systems use.

### A.2 — Executing the Schema (DDL)

The full DDL script from the Day 1 report was run against `smart_parking_db`, creating all 7 tables (`User`, `Vehicle`, `ParkingLot`, `Slot`, `Ticket`, `Payment`, `PricingConfig`) plus the `idx_slot_lot_status` composite index.

**Verification steps actually performed (not assumed):**
```sql
SHOW TABLES;
```
confirmed all 7 tables exist.

Then, constraints were deliberately **violated on purpose** to prove they're real:
- Inserted two slots with the same `(parking_lot_id, slot_label)` pair → rejected by the `UNIQUE` constraint, as expected.
- Inserted a `User` row with an invalid `role` value (something outside `ADMIN`/`ATTENDANT`/`CUSTOMER`) → rejected by the `CHECK` constraint, as expected.

This step matters because a schema that merely *exists* isn't the same as a schema that's *enforcing* the rules it was designed around — the only way to know for sure is to try to break it and watch it correctly refuse.

### A.3 — Seed Data (DML)

Minimum data needed for the app to be usable from day one, inserted via plain `INSERT` statements:

- **Seed admin** — one manually-created `User` row with `role = 'ADMIN'` and a properly bcrypt-hashed password (generated via an external bcrypt tool since the Java hashing code doesn't exist yet — this is fine, it's a one-time setup action, not application logic). This matches the Day 0 decision that the first admin is created outside the app; there is no "create first admin" API.
- **One ParkingLot** — "Westend Mall Parking", Aundh, Pune, `status = ENABLED`.
- **8 Slots** under that lot — `2W-01..03`, `4W-01..03`, `EV-01..02`, all seeded `AVAILABLE`.
- **One PricingConfig row** — Base Fee ₹20, Base Hours 3, Extra Hour Fee ₹10, Lost Ticket Penalty ₹70.

Without this seed step, the database would technically exist but be functionally useless — no admin could log in, no lot/slots to allocate against, and no pricing rule to calculate fees with.

---

## PART B — Maven Project Setup

### B.1 — What Maven Actually Does (Beyond Creating Folders)

It's easy to think Maven's job is generating the `model/`, `dao/`, `service/` folders — but that's a side effect, not the point. Maven is a **build automation and dependency management tool**. Concretely, it solves three problems:

**1. Dependency management.** Without Maven, using a library like the MySQL driver means: find it online → download the `.jar` → manually add it to your build path → repeat every time a version changes, and repeat again for every other library (HikariCP, JWT, JUnit, BCrypt...). With Maven, each dependency is a few lines inside `pom.xml`, and `mvn clean install` fetches everything — including *transitive* dependencies (libraries your libraries depend on) — automatically.

**2. Reproducibility.** If this repo is cloned onto another machine, that machine won't have any of these jars locally. Without Maven, the project simply fails to compile until someone manually gathers every jar. With Maven, `pom.xml` acts as a manifest — `mvn clean install` reads it and rebuilds an identical, fully-resolved environment.

**3. Build lifecycle.** Maven already knows the correct order of operations:
```
clean → compile → test → package → install
```
so there's no need to manually remember or chain build commands.

**One-line interview answer:** *"Maven is a build automation and dependency management tool that standardizes project structure, manages external libraries, compiles the project, runs tests, and packages the application."* Note that "creates folders" isn't even in that sentence — the package convention is incidental to Maven's actual job.

### B.2 — `pom.xml` — What's In It and Why

Think of `pom.xml` as the project's shopping list plus its build recipe. The relevant parts added today:

**Properties block** (centralizes versions and settings so they're not repeated/hardcoded in multiple places):
```xml
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```
- `maven.compiler.source/target` — tells Maven to compile using Java 17 language features and bytecode.
- `sourceEncoding` — forces UTF-8 everywhere, avoiding platform-dependent encoding bugs later (e.g. between Windows and other environments).

**Dependencies block:**
```xml
<dependencies>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>...</version>
    </dependency>

    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>...</version>
    </dependency>
</dependencies>
```
Each `<dependency>` block is a request: *"give me this specific library."* `groupId` identifies the organization/namespace that publishes it, `artifactId` is the library's name, `version` pins the exact release. Running `mvn clean compile` makes Maven read this list, download each jar (from Maven Central by default) into a local repository cache (`~/.m2`), add them to the project's classpath, and then compile.

**Why these two libraries specifically, today:**
- **`mysql-connector-j`** — the JDBC driver. Java's `DriverManager`/`Connection`/`PreparedStatement` classes are generic interfaces — they don't know how to actually speak MySQL's wire protocol. The connector is the translator between Java's generic JDBC calls and MySQL's specific protocol. Without it, `DriverManager.getConnection("jdbc:mysql://...")` fails outright — Java has no driver registered that understands that URL scheme.
- **`HikariCP`** — the connection pool (full reasoning in Part C below).

`mvn clean compile` was run after adding these — this isn't a formality, it's a checkpoint: confirms Maven successfully resolved and downloaded both dependencies and that the project still compiles with them present. Catching a missing/misconfigured dependency here is far cheaper than discovering it later while writing DAOs.

### B.3 — Package Structure

```
com.pratham.smartparkingmanagement
│
├── config      → DB configuration, HikariCP setup, app-wide settings
├── controller  → REST API endpoint handlers (comes Day 7+)
├── dao         → JDBC database operations (comes Day 3+)
├── dto         → Request/response objects (comes as needed)
├── exception   → Custom exception classes (comes Day 11)
├── model       → Entity/domain classes (comes Day 3)
├── security    → JWT auth logic (comes Day 9+)
├── service     → Business logic (comes Day 5)
├── util        → Shared helper classes
└── validator   → Input validation logic (comes Day 11)
```
This is a standard layered-architecture convention — each package has exactly one responsibility, which keeps the codebase navigable as it grows. Maven's default archetype-generated files (`App.java`, `AppTest.java`) were deleted since they're placeholder scaffolding, not part of the real project.

### B.4 — Resources Directory

`src/main/resources/` was created. Maven automatically includes anything in this folder on the project's **classpath** at build time — this is the standard location for configuration files (and later, anything else non-Java the app needs to ship with, like logging config).

---

## PART C — Why HikariCP (Connection Pooling)

`DriverManager.getConnection()` alone works — but every call to it opens a brand-new physical TCP connection to MySQL, which is a relatively expensive operation compared to actually running a query. Under load — many requests arriving close together, exactly the scenario this project's concurrency work simulates later — repeatedly creating and destroying connections becomes a real bottleneck.

**Without a pool:**
```
Request → DriverManager.getConnection() → new connection → run query → close (destroy) connection
```
repeated for every single request.

**With HikariCP:**
```
App starts → HikariCP creates a fixed pool of connections (e.g. 10), once
Request → borrow a connection from the pool → run query → return connection to pool (not destroyed)
```

The library/connection-reuse mental model: a library with a fixed set of chairs is far more efficient than a library that builds a brand-new chair for every visitor and destroys it when they leave. The chairs (connections) get reused across many visitors (requests).

**Everything downstream of "getting the connection" stays identical** — `PreparedStatement`, `ResultSet`, `executeQuery()`, `executeUpdate()` all work exactly the same whether the `Connection` object came from `DriverManager` directly or from a Hikari pool. Only the *acquisition* method changes.

Small but important detail: calling `connection.close()` on a pooled connection does **not** actually disconnect from MySQL — it returns the connection to the pool for the next borrower. This is the entire performance win, and it's why pooled code looks identical to non-pooled code but behaves very differently underneath.

---

## PART D — Code Files, Explained in Full

### D.1 — `application.properties` and `application.properties.example`

**`application.properties`** (git-ignored — contains real secrets):
```properties
db.url=jdbc:mysql://localhost:3306/smart_parking_db
db.username=parking_app
db.password=your_real_password
db.pool.size=10
```

**`application.properties.example`** (committed — placeholder values only):
```properties
db.url=jdbc:mysql://localhost:3306/smart_parking_db
db.username=parking_app
db.password=your_password
db.pool.size=10
```

Why two files: anyone cloning the repo needs to know *which* keys to fill in, without ever being able to see real credentials in git history. The `.example` file is the template; the real file is private and reconstructed locally by each developer.

**`.gitignore` update:**
```
src/main/resources/application.properties
```
Only the real file is excluded — the `.example` file is deliberately left trackable.

### D.2 — `DatabaseProperties.java`

**Purpose:** read `application.properties` once at startup and expose its values through simple static getters, so nothing else in the codebase touches file I/O directly.

**Conceptual walkthrough of what the class does, step by step:**

1. **Create an empty `Properties` object.**
   `Properties` is a built-in Java class (`java.util.Properties`) — functionally a specialized `HashMap<String,String>` purpose-built to parse `key=value` file formats. It is not somehow tied to the filename `application.properties`; that's purely a naming convention. Initially this object is empty: `{}`.

2. **Open the file as an `InputStream`.**
   ```java
   InputStream input = DatabaseProperties.class
       .getClassLoader()
       .getResourceAsStream("application.properties");
   ```
   `InputStream` is not a data container — it's a *pipe*. It doesn't store anything; it only allows Java to read raw bytes from a source, in this case the properties file sitting on the classpath.

   **Why `getResourceAsStream()` instead of something like `FileReader`?** `application.properties` lives under `src/main/resources`, which Maven treats as a **classpath resource**, not a plain file path. `FileReader` needs a literal file-system path and works fine while developing — but once the project is packaged into a `.jar` (`mvn package`), the `src/main/resources` folder no longer physically exists; its contents are bundled *inside* the jar. `getResourceAsStream()` correctly reads classpath resources whether the code is running from raw `.class` files during development or from inside a packaged jar. `FileReader` would throw `FileNotFoundException` after packaging. This is the actual reason, not a stylistic preference.

3. **Load the stream into the `Properties` object.**
   ```java
   properties.load(input);
   ```
   This is the moment the pipe's contents get read and stored. Before this line, `properties` is `{}`; after it, `properties` holds every `key=value` pair from the file:
   ```
   { db.url -> ..., db.username -> ..., db.password -> ..., db.pool.size -> ... }
   ```
   After this call, the `InputStream` has done its one job and is no longer needed — the `Properties` object is now the single source of truth in memory.

4. **Expose values via static getters:**
   ```java
   public static String getUrl() {
       return properties.getProperty("db.url");
   }
   public static String getUsername() {
       return properties.getProperty("db.username");
   }
   public static String getPassword() {
       return properties.getProperty("db.password");
   }
   public static int getPoolSize() {
       return Integer.parseInt(properties.getProperty("db.pool.size"));
   }
   ```
   Every caller (eventually just `DBConfig`) asks for a value by key rather than ever touching the file or the `InputStream` again.

5. **Fail Fast on load failure.**
   If the file can't be found or `properties.load()` throws, the class throws a runtime exception immediately rather than letting the app continue with missing/broken config and fail unpredictably somewhere unrelated, much later. This is done inside a **static initialization block** — code that runs exactly once, automatically, the first time this class is referenced by the JVM — which is the natural place to load configuration that should exist for the entire lifetime of the app.

**Mental model to remember this by:** *`InputStream` is the delivery boy; `Properties` is the cupboard.* The delivery boy brings the package (file contents) and hands it to the cupboard (`Properties`), which stores it. After delivery, the delivery boy leaves — from then on, everyone takes what they need directly from the cupboard.

### D.3 — `DBConfig.java`

**Purpose:** the single centralized place in the entire project that knows about the DB URL, credentials, and HikariCP setup. Every DAO will get its connections from here — nowhere else.

**Step-by-step what the class does:**

1. **Build a `HikariConfig` (a settings object, nothing active yet):**
   ```java
   HikariConfig config = new HikariConfig();
   config.setJdbcUrl(DatabaseProperties.getUrl());
   config.setUsername(DatabaseProperties.getUsername());
   config.setPassword(DatabaseProperties.getPassword());
   config.setMaximumPoolSize(DatabaseProperties.getPoolSize());
   ```
   At this point nothing has connected to MySQL yet — `HikariConfig` is purely a form being filled out (URL, username, password, pool size), analogous to knowing a hotel's address/login but not having booked a room yet.

2. **Create the actual pool from that config:**
   ```java
   private static final HikariDataSource dataSource = new HikariDataSource(config);
   ```
   This is the line where HikariCP actually does something — `HikariDataSource` is the object that owns and manages the live pool of real connections (the "parking garage" holding `Conn1, Conn2, Conn3...`). Constructing it with a filled-out `HikariConfig` is what triggers Hikari to actually open the initial set of connections.

3. **Why `private static final`?**
   This is the **singleton pattern** — exactly one pool exists for the entire application's lifetime.
   - `static` → belongs to the class itself, not to any instance; shared by everyone who references `DBConfig`.
   - `final` → the reference can't be reassigned once set.
   - `private` → nothing outside this class can reach the raw `HikariDataSource` directly; access only happens through the controlled method below.

   Without this, if every DAO independently created its own `HikariDataSource`, the app could end up with far more open connections than intended (e.g. `UserDao` creates 10, `VehicleDao` creates another 10, `TicketDao` another 10 → 30 idle connections for no benefit) — wasteful and against the entire point of pooling.

4. **Expose one public method:**
   ```java
   public static Connection getConnection() throws SQLException {
       return dataSource.getConnection();
   }
   ```
   No DAO should ever know the URL, password, or that HikariCP is even involved — it just calls `DBConfig.getConnection()`. Internally, this borrows one connection from the pool. When that connection's `.close()` is eventually called (in the DAO, via try-with-resources), it returns to the pool rather than being destroyed — this is the actual mechanism that makes pooling fast.

**High-level chain, start to finish:**
```
application.properties
      ↓
DatabaseProperties  (reads file → Properties object → exposes getters)
      ↓
DBConfig            (builds HikariConfig → creates HikariDataSource pool)
      ↓
Connection Pool      (live, reusable connections)
      ↓
DAO classes          (call DBConfig.getConnection(), never touch Hikari directly)
```

### D.4 — Proof-of-Life Test

A minimal, deliberately simple query was run through the full stack:
```java
try (Connection conn = DBConfig.getConnection();
     Statement stmt = conn.createStatement();
     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Slot")) {

    if (rs.next()) {
        System.out.println("Total Slots = " + rs.getInt(1));
    }
}
```
The query itself (`SELECT COUNT(*) FROM Slot`) wasn't the point — the point was proving every layer works together end-to-end: `Properties` loaded correctly → `DBConfig` built the pool correctly → Hikari connected to MySQL successfully → the query executed → the `ResultSet` was read correctly. Getting back `Total Slots = 8` (matching the seeded data exactly) confirmed the entire pipeline, not just one piece of it.

### D.5 — Fail-Fast Verification

The password in `application.properties` was deliberately changed to something wrong, and the test rerun.

**Result:**
```
Access denied for user 'parking_app'@'localhost'
```
This confirmed: HikariCP read the (now-wrong) credentials, attempted to build the pool, MySQL rejected the login, and the application stopped immediately with a clear, specific error — rather than hanging, timing out ambiguously, or failing silently somewhere unrelated later. The password was then restored and the test rerun successfully (`Total Slots = 8` again), proving both the failure path and the recovery path work correctly.

**Why this matters:** imagine deploying with a wrong password and *not* having fail-fast behavior — the app might start "successfully," some operations might work, others fail randomly, and debugging becomes a nightmare because the actual root cause (bad config) is disguised as scattered unrelated failures. Fail-fast turns that into an immediate, obvious, specific error at the exact moment it happens.

---

## Part E — Day 2 Status Summary

| Block | What was done | Status |
|---|---|---|
| 1 | MySQL installed, `smart_parking_db` created, `parking_app` user created with least-privilege grants | ✅ |
| 2 | Day 1 DDL executed for real; all 7 tables + composite index created; constraints verified by deliberately breaking them | ✅ |
| 3 | Seed data inserted: admin, 1 lot, 8 slots, 1 pricing config | ✅ |
| 4 | Maven project structured, `pom.xml` configured (Java 17, UTF-8), package layout created | ✅ |
| 5 | MySQL Connector/J and HikariCP added as dependencies, `mvn clean compile` verified | ✅ |
| 6 | `application.properties` + `.example` created, `.gitignore` updated, `DatabaseProperties.java` written | ✅ |
| 7 | `DBConfig.java` written — singleton HikariCP pool, centralized `getConnection()` | ✅ |
| 8 | Fail-fast behavior verified (wrong password → immediate clear failure; correct password → success) | ✅ |

**Outcome:** the project moved from a design document to a real, working foundation — live database with verified constraints and seed data, a properly structured Maven project with working dependency resolution, fully externalized and git-safe configuration, and a centralized, pooled, singleton connection layer proven to work correctly under both success and failure conditions. No business logic exists yet — this was entirely infrastructure — but it's the correct base to build the DAO and service layers on top of, starting Day 3.

---

## Status
✅ Day 2 complete. Proceeding to Day 3 (entity classes and core DAOs: `ParkingLot`, `Slot`, `Vehicle`).