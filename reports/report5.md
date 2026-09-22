# Day 5 Report — REST Layer, Part 1 (Blocks 1–3)

**Project:** Smart Parking Management System
**Scope of Day 5 Part 1:** Build the REST infrastructure from nothing — HTTP server, router, response formatting — and connect the first two real read endpoints to the existing DAO layer.
**Purpose of this doc:** this is your first real exposure to REST API concepts, so this report is written to be a standalone reference you can come back to later, not just a log of what happened today. Concepts are explained from first principles, in the order they need to be understood.

---

## 1. The Problem Day 5 Actually Solves

Up through Day 4, your application worked, but only you could use it — literally, by writing Java code that calls your own service methods directly:
```java
parkingEntryService.parkVehicle(...);
```
That's you, the programmer, sitting *inside* the application and invoking it directly. A real application needs to be reachable by something *outside* the Java process — a website, a mobile app, another service. Since you have no frontend, **Postman** plays that role for now: it's a tool that lets you manually construct and send HTTP requests, acting as a stand-in for whatever real client would eventually talk to your API.

```
Real world:      Frontend/App  → HTTP request → Your application
Your dev setup:  Postman       → HTTP request → Your application
```
Postman is not part of your Java project — it's an external tool sitting completely outside it, sending requests the same way a real client eventually would.

**`localhost`** simply means "this same computer" — since your server and Postman are both running on your own machine right now, requests go to `http://localhost:8080` instead of a real public domain.

**HTTP** is the protocol — the agreed-upon rules — governing how a client and server talk to each other: a request goes out (method + path + optional body), a response comes back (status code + body). This exchange is fundamentally **bidirectional**: something travels down through your application's layers, work happens, and a result travels back up through those same layers to the original caller.

---

## 2. Core Building Blocks — What Each Piece Actually Is

### `HttpServer`
Java's built-in class (`com.sun.net.httpserver.HttpServer`) that does exactly one thing at the infrastructure level: **listens on a port for incoming HTTP connections.** You configure it to listen on `localhost:8080`. Think of it as the literal front gate of your application — nothing gets in without passing through it first.

### `HttpExchange`
This is the single most important object in the whole system to understand correctly, and it was the source of the most confusion today, so the mental model matters:

**`HttpExchange` represents one specific HTTP request *and* the response you're going to build for it, bundled together as one object.**
```
HttpExchange
├── Request  → method, path, headers, body
└── Response → status, headers, body (you fill this in)
```
**The critical fact:** the `HttpServer` creates and hands you this object — nothing else does. Your Router does not create it, your Handler does not create it. It arrives already populated with the incoming request, and by the end of processing, you've written a response into that same object, and the server sends it back out. One `HttpExchange` object, one full request/response cycle, always flowing through the exact same instance from start to finish.

### Router
With 10+ possible endpoints (`GET /parking-lots`, `POST /vehicles/entry`, `GET /tickets/{id}`...), someone has to answer: *given this specific request, which piece of code should actually handle it?* That's the Router's entire job — matching an incoming request's method + path against a table of registered routes, and handing off to the correct handler. Think of it as a traffic controller, not a worker — it decides where to send the request, it doesn't do the actual work itself.

### Handler
Because this project deliberately avoids Spring (and therefore Spring's `@Controller`/`@RequestMapping` machinery), you built the equivalent by hand. A Handler is your project's version of a Controller — it deals specifically with *HTTP concerns*: reading the request, extracting parameters, calling into the Service (or DAO, for simple reads) layer, and writing a response. It should **not** contain business logic itself — that stays in the Service layer, exactly as before.

```
Handler ≈ your lightweight Controller
DAO     ≈ Repository (same terminology you may see in Spring projects)
```

### The full layered picture
```
Postman → HTTP → HttpServer → Router → Handler → Service/DAO → MySQL
```
Request travels down through these layers; the result travels back up through the same layers to Postman.

---

## 3. The `/hello` Experiment — Why It Existed

Before building the real Router, a throwaway `/hello` endpoint was created purely to prove the full mechanical chain worked: browser/Postman → HttpServer → some Java code runs → a response comes back. Typing `http://localhost:8080/hello` directly into a browser sends a `GET` request automatically — browsers always issue `GET` for a plain URL visit; you don't see or choose the method because there's nothing to choose, it's implicit. Postman, by contrast, lets you explicitly pick `GET`/`POST`/`PUT`/`DELETE`, which is why real endpoint testing happens there, not in a browser address bar. `/hello` was removed once the real Router existed — its only purpose was proving the pipe works before building anything real on top of it.

---

## 4. Building the Router

**File:** `api/Router.java`

The core idea: store a list of registered routes, each one pairing a method + path pattern with a handler:
```
GET  /hello           → handler
GET  /parking-lots     → handler
POST /vehicles/entry   → handler
GET  /tickets/{id}     → handler
```
Internally, this is a `Route` object holding `method`, `path`, and `handler`, collected into a list the Router searches through on each incoming request.

### `addRoute()` vs `handle()` — a distinction worth locking in permanently

**`addRoute()`** runs once, at **application startup**, inside `Main`. Each call is you telling the Router: *"remember this rule for later."* By the time the server actually starts, the Router has a complete table of every known route.

**`handle()`** is never called manually by you. Instead, `Main` does:
```java
server.createContext("/", router);
```
which tells the `HttpServer`: *"whenever a request comes in, hand it to this Router."* Once `server.start()` runs, the application just waits. When an actual request arrives, **the `HttpServer` itself automatically calls `router.handle(exchange)`** — this is the Java `HttpServer`'s own machinery invoking your code, not you invoking it. This is the single most important mental shift of the day: after startup, you are not driving execution — incoming requests are.

---

## 5. The Complete Request Lifecycle — Traced Step by Step

Using `GET http://localhost:8080/tickets/25` as the concrete example, here is the full path a request takes, start to finish. This is worth memorizing as your reference mental model for how *any* request flows through this system:

**Startup (happens once, when the app launches):**
1. `Main` creates the `HttpServer` and `Router`
2. `router.addRoute("GET", "/tickets/{id}", handler)` — the Router now knows this route exists
3. `server.createContext("/", router)` — the server is told to delegate all incoming requests to this Router
4. `server.start()` — the application is now listening and waiting; nothing else happens until a request arrives

**Per-request (happens every single time a request comes in):**
5. Postman sends `GET /tickets/25`
6. The `HttpServer` receives it and creates the `HttpExchange` object representing this request
7. The `HttpServer` automatically calls `router.handle(exchange)` — you never call this yourself
8. The Router reads `exchange.getRequestMethod()` → `"GET"`, and `exchange.getRequestURI().getPath()` → `"/tickets/25"`
9. The Router searches its registered routes and finds a pattern match: `GET /tickets/{id}`
10. `matchPath()` compares the pattern segment-by-segment against the actual path, and extracts `{id} = "25"`
11. That extracted value gets stored back onto the *same* `HttpExchange` object: `exchange.setAttribute("id", "25")` — this is how a value discovered by the Router becomes available to the Handler that runs next
12. The Router calls the matched route's handler: `route.handler.handle(exchange)` — **this is the moment your actual endpoint logic starts running**
13. The Handler retrieves the path parameter back out: `exchange.getAttribute("id")` → `"25"`
14. The Handler does its work (in today's endpoints: calls a DAO directly) and builds an `ApiResponse.success(...)` object
15. The Handler calls `ResponseUtil.sendJson(exchange, 200, apiResponse)`
16. `ResponseUtil` uses Gson to convert the Java `ApiResponse` object into a JSON string
17. `ResponseUtil` writes the `Content-Type` header, the HTTP status code, and the JSON body onto that same `HttpExchange`
18. The response travels back up: `ResponseUtil → HttpExchange → HttpServer → HTTP → Postman`

**The one rule worth remembering above everything else from this section:** one single `HttpExchange` object is created once per request, and every layer — Router, Handler, ResponseUtil — reads from and writes to that *exact same object*, never a new or separate one. That's the thread tying the entire request together from start to finish.

---

## 6. Path Parameters

Rather than hardcoding a separate route for every possible ID (`/tickets/1`, `/tickets/2`, `/tickets/3`...), a route can contain a wildcard segment written in curly braces: `/tickets/{id}`. The matching logic splits both the registered pattern and the actual incoming path on `/`, then compares segment by segment:
```
Pattern: ""  /  tickets  /  {id}
Actual:  ""  /  tickets  /  25
```
A literal segment (`tickets`) must match exactly; a `{...}`-wrapped segment matches *any* value in that position and captures it (`id = "25"`). If any literal segment fails to match (e.g. `tickets` vs `parking-lots`), the whole route is rejected as a non-match, and the Router moves on to check the next registered route.

**When no route matches at all:** the Router returns a `404`, but only after exhausting every registered route with no match found — 404 specifically means *"this route genuinely doesn't exist,"* not a generic catch-all error. The plan going forward is to route even the 404 response through the same `ApiResponse`/`ResponseUtil` envelope, so error responses look structurally identical to success responses, just with `success: false`.

---

## 7. Response Infrastructure — `ApiResponse` and `ResponseUtil`

Without a shared response structure, every single handler would independently have to repeat the same 6-step chain: build a Java response object → run it through Gson → set `Content-Type` → set the status code → write the response bytes → close the stream. That repetition is exactly what these two classes exist to eliminate — and importantly, they answer **two different questions**, which is why they're two separate classes rather than one:

### `ApiResponse` answers: *what should my response look like?*
A plain Java object with a fixed, consistent shape used for every endpoint, success or failure alike:
```json
// success
{ "success": true,  "data": { ... }, "error": null }

// failure
{ "success": false, "data": null,    "error": "Ticket not found" }
```
Critically: **`ApiResponse` does not send anything over HTTP.** Building an `ApiResponse` object is purely an in-memory Java step — it's a data container, nothing more, at this point.

### `ResponseUtil` answers: *how do I actually deliver that over HTTP?*
This is where the repetitive mechanical work lives — converting an `ApiResponse` into JSON via Gson, setting the `Content-Type` header, setting the HTTP status code, writing the response body, and closing the output stream, all through the request's `HttpExchange`.

**Why `ResponseUtil.sendJson(...)` is called as a static method, not instantiated:** `Main` never creates a `ResponseUtil` object (`new ResponseUtil()`) — there's no per-request state this class needs to hold onto between calls, so it's called directly as `ResponseUtil.sendJson(exchange, 200, apiResponse)`, a static utility method.

**The three arguments to `sendJson()`, and where each one comes from:**
- **`exchange`** — the *same* `HttpExchange` object that's been flowing through the whole request from the moment the `HttpServer` created it — passed down from `Router.handle()` → `Handler.handle()` → here.
- **`200`** (or whichever code fits) — the intended HTTP status. Common ones used/discussed today: `200` success, `201` created, `400` bad request, `404` not found, `409` conflict, `500` server error.
- **`apiResponse`** — the `ApiResponse` object built just before this call, e.g. via `ApiResponse.success(...)`.

---

## 8. Gson — Java Objects ↔ JSON

Added to `pom.xml` today. Gson's job is strictly the translation between a Java object and its JSON text representation:
```
Java → JSON   (used today, for responses)
JSON → Java   (needed starting tomorrow, for parsing request bodies like POST /vehicles/entry)
```
Gson has no awareness of MySQL, DAOs, or your database at all — it only ever converts between a Java object in memory and a JSON string. The actual full data path for a future write endpoint will be:
```
Incoming JSON → Gson → Java object → Service → DAO/JDBC → MySQL
```

### The `LocalDateTime` serialization bug — a real debugging lesson worth keeping
`GET /parking-lots` correctly fetched real rows from the DAO, but Gson failed specifically when trying to serialize the `createdAt` field:
```
Failed making field 'java.time.LocalDateTime#date' accessible
```
**The lesson here is genuinely important, not just a one-off fix:** the database and DAO layers were both working correctly — the failure happened specifically at the boundary between "Java object in memory" and "JSON text," which is a completely different layer than the one that had actually done the real work. When something breaks, the error message tells you *where in the pipeline* it broke, and that's not always where the actual logic lives. Gson, by default, doesn't know how to serialize Java 8+ time types like `LocalDateTime` out of the box.

**The fix:** register a custom Gson type adapter for `LocalDateTime` inside `ResponseUtil`, telling Gson explicitly to serialize it as its string representation, rather than trying to reflect into its internal fields (which is what was failing). Once registered, this adapter is now reused automatically for every future entity with a `LocalDateTime` field (`Ticket.entryTime`, `Payment.paidAt`, etc.) — a one-time fix that covers the whole project going forward.

---

## 9. Block 3 — The First Real Endpoints

### `GET /parking-lots`
```
Postman → HttpServer → Router → Handler → ParkingLotDao.findAll()
       → SELECT * FROM ParkingLot → List<ParkingLot>
       → ApiResponse.success(...) → ResponseUtil → JSON → Postman
```
No DAO changes were needed — `findAll()` already existed from Day 3. This was the first genuine end-to-end **REST → DAO → MySQL → REST** round trip in the project.

### `GET /slots/available?lotId=1&vehicleType=FOUR_WHEELER`
The original rough plan had imagined `GET /slots?lotId=&status=`, but on inspecting the actual `SlotDao`, it already had a precisely-fitting method from Day 3:
```java
List<Slot> findAvailableByLotAndType(int lotId, VehicleType type);
```
which already runs `WHERE parking_lot_id=? AND vehicle_type=? AND status='AVAILABLE'`. Rather than modifying an existing, working DAO method just to match an endpoint shape decided before the DAO was reexamined, **the endpoint was reshaped to match what the DAO already correctly did** — `/slots/available` instead of a generic `/slots` with a status filter. This is a real architectural principle worth remembering: *don't force an API shape decided in the abstract when your actual backend already has a clean, working capability — adapt the interface to fit the real capability, not the other way around.*

### Query parameters — the second parameter-passing mechanism
Path parameters (`/tickets/25`) carry a value as part of the URL's structure itself. **Query parameters** are different — they appear after a `?`, as `key=value` pairs joined by `&`:
```
/slots/available?lotId=1&vehicleType=FOUR_WHEELER
             ^--query string--^
lotId       = 1
vehicleType = FOUR_WHEELER
```
Because this project uses the low-level `HttpServer` rather than a framework, query strings are **not parsed automatically** — `exchange.getRequestURI().getQuery()` returns the raw string, and it's parsed manually (split on `&`, then each piece split on `=`). Once parsed, the Handler passes the extracted values straight into `slotDao.findAvailableByLotAndType(lotId, vehicleType)`.

---

## 10. What Deliberately Did NOT Happen Today

- No `POST` endpoints at all yet (`/vehicles/entry`, `/vehicles/exit/{id}`, `/parking-lots`, `/parking-lots/{id}/slots`) — these are Part 2 (Day 6)
- No `GET /tickets/{id}` or `GET /tickets?vehicleId=` yet
- No authentication/authorization — untouched, exactly as planned, reserved for after the REST foundation and remaining CRUD endpoints exist
- Today's two working endpoints go straight from Handler to DAO, **skipping the Service layer entirely** — this is intentional and fine specifically *because* these are simple, no-business-logic reads. Tomorrow's entry/exit endpoints reconnect the REST layer to `ParkingEntryService`/`ParkingExitService`, since those operations involve real business rules and transactions that must not be duplicated or bypassed at the Handler level.

---

## 11. The One Diagram to Keep Permanently

```
POSTMAN
   ↓ HTTP request
HTTP SERVER  ← creates/owns the HttpExchange
   ↓
ROUTER       ← matches method + path against registered routes
   ↓
HANDLER      ← reads request, extracts params, calls Service/DAO
   ↓
SERVICE/DAO  ← business logic / database access
   ↓
MYSQL
   ↑ result flows back up through the same chain
HANDLER → ApiResponse built → ResponseUtil.sendJson(exchange, status, response)
   ↑
HTTP EXCHANGE (same object, now carrying the response)
   ↑
HTTP SERVER
   ↑ HTTP response
POSTMAN
```

**The governing rule underneath all of it:** after `server.start()`, you never manually drive this chain — an incoming request is what triggers the entire sequence, automatically, end to end, every single time.

---

## 12. Status

- [x] `HttpServer` running on `localhost:8080`
- [x] `Router` built — route registration (`addRoute`), method+path matching, path parameter extraction, 404 handling for unmatched routes
- [x] `HttpExchange` ownership model understood correctly (created by `HttpServer`, flows unchanged through Router/Handler/ResponseUtil)
- [x] `ApiResponse` — consistent success/failure JSON envelope
- [x] `ResponseUtil` — static utility handling Gson serialization + HTTP response writing
- [x] `LocalDateTime` Gson serialization bug found and fixed with a custom type adapter
- [x] `GET /parking-lots` — working, tested, full REST→DAO→MySQL→REST round trip confirmed
- [x] `GET /slots/available?lotId=&vehicleType=` — working, endpoint shape deliberately adapted to fit the existing DAO method rather than forcing a mismatched design

---

## Status
✅ Day 5 Part 1 (Blocks 1–3) complete. Continuing as Day 6: entry/exit endpoints (reconnecting to the Service layer), admin write endpoints, ticket lookup endpoints, and a saved Postman collection.