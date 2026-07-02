# Smart Fridge

A full-stack Spring Boot web application for managing a household fridge's contents — tracking expiry dates, scanning food items from photos using a custom-trained deep learning model, and generating AI-powered recipe suggestions from what's on hand.

Originally built as a deep learning coursework project, then rebuilt from the ground up into a multi-user web application with proper authentication, authorization, and per-user data isolation.

## Overview

Users register an account, add items to their fridge either manually or by uploading a photo of a dish, and get expiry tracking out of the box (unset expiry dates are inferred from a built-in shelf-life table). A recipe suggestion engine calls the Gemini API to propose meals that prioritize ingredients closest to expiring. Admins get a separate panel to inspect all users and all fridge items across the system.

> **Note on image scanning:** the current model is an EfficientNet classifier trained on 10 prepared-dish classes (e.g. pizza, sushi, hamburger, Caesar salad) rather than raw individual ingredients — so it identifies "what dish is this," not "what's this one ingredient." Swapping in a different trained/traced model is straightforward since the classifier is loaded from a single TorchScript file.

## Technical Highlights

A few parts of this project go beyond a standard CRUD app and were the more interesting engineering problems to solve:

- **Async image classification pipeline.** Uploaded photos are classified by an EfficientNet model exported to TorchScript (`model_traced.pt`) and served through Deep Java Library's PyTorch engine. The model loads once in the background at startup via a singleton `ModelHolder` (a `CountDownLatch` gates readiness so early requests fail gracefully instead of blocking). Inference itself runs off the request thread on a dedicated `ImageScanWorker` pulling off a `LinkedBlockingQueue` — necessary because `MultipartFile` contents become invalid once the HTTP request lifecycle ends, so image bytes are eagerly copied to memory on the controller thread before being handed off to the async worker. The controller then blocks on a `CompletableFuture` with a 30-second timeout while the worker does the actual prediction.
- **IDOR-hardened data access.** Every fridge item lookup is scoped through `Principal` → `userRepository.findByUsername()` → the resolved `User` entity. User IDs are never accepted from client input (forms, hidden fields, or query params), which closes off the usual insecure-direct-object-reference path in a multi-tenant app like this.
- **Role-gated admin section.** `/systems/**` is restricted to `ROLE_ADMIN` at the security filter chain level, separate from the `hasAnyRole(ADMIN, CUSTOMER)` rules that gate the rest of the authenticated app.
- **Rate-limited, persisted AI output.** Recipe generation hits the Gemini API through a plain Java `HttpClient`, gated by a per-user daily quota (10/day) enforced by a `ConcurrentHashMap`-backed `RateLimiterService`. Generated recipes aren't just displayed and discarded — they're serialized to JSON and upserted into the database per user, then re-hydrated and cross-checked against the *current* fridge contents on every page load, so the "ingredients you already have" list stays accurate even for a recipe set generated days earlier.
- **Structured LLM output.** Gemini responses are constrained to JSON and deserialized directly into `Recipe` / `RecipesResponse` records rather than parsed as free text.

## Features

- **Fridge management** — full CRUD on fridge items (name, category, quantity, unit, storage date, expiry date), scoped per user.
- **Manual and image-based entry** — a dual-form insertion page with drag-and-drop upload for the image scan path (JPEG/PNG only; other formats, including WEBP, are rejected).
- **Automatic expiry inference** — if no expiry date is provided, one is calculated at persist time from a shelf-life lookup table (e.g. milk → 7 days, chicken → 2 days, apples → 30 days).
- **AI recipe suggestions** — Gemini-powered recipe generation that prioritizes soon-to-expire ingredients, with a regenerate option and a breakdown of which ingredients you have vs. need.
- **Authentication & role-based access** — session-based auth via Spring Security's `JdbcUserDetailsManager`, BCrypt password hashing, and `ROLE_ADMIN` / `ROLE_CUSTOMER` separation.
- **Self-service profile editing** — users can update their own name/email, resolved via `Principal` to prevent editing other accounts.
- **Admin panel** — read-only views of all fridge items system-wide, all registered users, and per-user item breakdowns. (Admin editing of user accounts/items is not implemented yet — see Roadmap.)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java, Spring Boot, Spring MVC |
| Security | Spring Security (session-based, BCrypt, role-based access control) |
| Persistence | Spring Data JPA / Hibernate, MySQL |
| Frontend | Thymeleaf, Spring Security Dialect, CSS, vanilla JS |
| ML / Image scanning | Deep Java Library (DJL) with the PyTorch engine, TorchScript model |
| AI recipe generation | Google Gemini API (`gemini-2.0-flash`) |
| Build | Maven |
| Tunneling (dev/demo) | Cloudflare Tunnel |

## Getting Started

### Prerequisites

- JDK 17+
- Maven
- A running MySQL server
- A Gemini API key ([Google AI Studio](https://aistudio.google.com/))
- Windows (the current `pom.xml` pins the DJL native PyTorch dependency to the `win-x86_64` classifier; running on macOS/Linux requires swapping this classifier before building)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/dany439/Smart-Fridge-Web.git
   cd Smart-Fridge-Web
   ```

2. **Configure the database**

   Update `src/main/resources/application.properties` with your MySQL connection details, then run the schema script:
   ```bash
   mysql -u <user> -p < sql_files/smart_fridge_init.sql
   ```

3. **Add your Gemini API key**
   ```properties
   gemini.api.key=YOUR_GEMINI_API_KEY
   ```

4. **Build and run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

   The app runs at `http://localhost:8080` by default.

## Usage

| Action | Route |
|---|---|
| Register | `/register/showRegistrationPage` |
| Login | `/showMyLoginPage` |
| Add items (manual / image) | `/fridge/insert` |
| View fridge contents | `/fridge/list` |
| Log consumed items | `/fridge/consume` |
| Get recipe suggestions | `/fridge/suggestions` |
| Edit your profile | `/profile/edit` |
| Admin — all items | `/systems/viewall` |
| Admin — view users | `/systems/users` |

## Project Structure

```
Smart-Fridge-Web/
├── sql_files/
│   └── smart_fridge_init.sql
├── src/
│   ├── main/
│   │   ├── java/com/smartfridge/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── exceptions/
│   │   │   ├── records/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   ├── util/
│   │   │   └── worker/
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/
│   │       └── templates/
│   └── test/
│       └── java/com/smartfridge/
├── pom.xml
└── mvnw / mvnw.cmd
```

## Key Design Decisions

- **Explicit routes over magic values.** Admin "view all" is its own route (`/systems/viewall`) rather than overloading an ID parameter (e.g. `id=0`) to trigger different behavior.
- **User resolution always server-side.** Controllers never trust a client-supplied user ID; every data access goes through the authenticated `Principal`.
- **TorchScript over raw state dicts.** DJL requires models exported via `torch.jit.trace`, so the classifier is shipped as a traced TorchScript module rather than a raw PyTorch `state_dict`.

## Roadmap

- [ ] Password reset flow (self-service and/or admin-forced) — not implemented yet
- [ ] Admin edit capability for users and fridge items (currently the `/systems` panel is view-only)
- [ ] Session invalidation for a customer's active sessions after an admin-forced password reset (via `SessionRegistry`), once password reset itself exists
- [ ] Expanded shelf-life coverage and category-based defaults
- [ ] Repo cleanup and CI setup

## License

No license has been added yet. All rights reserved by the author until one is specified.

## Author

[dany439](https://github.com/dany439)
