# PantryPal — AI Recipe → Shopping List Agent

## What this is
Spring Boot backend that takes a recipe (URL, name, or screenshot/PDF)
and produces a merged shopping list via a local LLM agent.

## Stack
- Java 21, Maven, Spring Boot (latest stable)
- Spring Web, Validation, Spring Data JPA, PostgreSQL (docker-compose.yml for dev) with Flyway migrations, DevTools
- Ollama (local, free) — llama3.2/qwen2.5 for text extraction & tool-calling
- Jsoup for URL scraping, Apache POI/PDFBox for doc parsing, an Ollama vision
  model (gemma3:4b by default) for images instead of Tesseract OCR

## Architecture
Controller → Service → Repository, standard layering.
Agent layer: ChatController → Ollama tool-calling → real Java methods
(addItem, removeItem, listItems) → aggregation logic merges duplicate
ingredients across recipes.

## Planned endpoints
- POST /recipes/from-url
- POST /recipes/from-text { text } (dish name -> Ollama-generated ingredients; implemented)
- POST /recipes/from-file multipart `file` (pdf/docx/txt via PDFBox/POI -> text LLM; png/jpg via Ollama vision model; implemented)
- GET  /shopping-list

## Conventions
- Authentication: the app is closed by default (SecurityConfig: `anyRequest().authenticated()`). Do not add
  `permitAll()` for anything except the /auth entry points without an explicit decision, and every new
  endpoint must be covered by SecurityIntegrationTest's endpoint list. Auth is a cookie session + CSRF token
  (X-XSRF-TOKEN), sessions stored in PostgreSQL (Flyway V3, Spring Session JDBC); passwords are BCrypt-hashed, never logged or returned; login failures must all look the same.
- HTTPS: `pantrypal.security.require-https` (env PANTRYPAL_REQUIRE_HTTPS) refuses non-HTTPS requests (config.RequireHttpsFilter), implies
  Secure cookies and HSTS; config.HttpsSettingsCheck fails startup on settings that cannot work. Never trust X-Forwarded-*
  yourself: only through server.forward-headers-strategy=native (trusted proxies only), and use request.getRemoteAddr().
- Email flows (verify, reset): tokens are single-use, hashed in the database and sent only through
  `mail.AccountMailService` (after commit, background thread, links from `pantrypal.public-url` never from the request).
  "Send me a link" endpoints answer 204 whatever the address, and share the AuthThrottle mail limits. Tests catch mail
  with a `@Primary Mailer`; pom's surefire config switches verification off for the other tests.
- Sensitive account actions (change password, delete account) re-ask for the password, go through the same throttle as
  login, and answer a wrong password with 400, never 401. Deleting an account relies on ON DELETE CASCADE for user data
  and publishes AccountDeletedEvent (sessions, chats): new user-owned in-memory state must listen for it.
- Brute force: `POST /auth/login` and `/auth/register` go through `auth.AuthThrottle` (429 + Retry-After). Keep the
  check before the password is verified, and keep every failure looking the same. Tests get generous limits from the
  surefire config in pom.xml; BruteForceProtectionIntegrationTest sets low ones.
- Data ownership: recipes and shopping list items belong to a user (user_id, migration V4). Every repository query is
  scoped by the owner via `CurrentUser.id()`; never fetch by id alone or `findAll()`. Someone else's row looks like a
  missing one (404). Chat conversation ids are namespaced by user. New user-owned data needs a user_id column and a
  case in DataOwnershipIntegrationTest.
- Security: the server must never fetch a user-supplied URL directly. Anything that takes a URL from a user, a
  recipe page or the LLM goes through net.SafePageFetcher / net.UrlGuard (SSRF protection: http(s) on ports 80/443
  only, public addresses only, DNS pinned to the checked addresses, redirects validated hop by hop, size/time limits).
  Never use Jsoup.connect(url), RestClient or HttpClient on such a URL.
- Database: the schema is owned by Flyway (src/main/resources/db/migration). Hibernate is `ddl-auto=validate`
  only. Never edit an applied migration; add a new V<n>__*.sql. Entities must match the migration.
- DB tests use a real PostgreSQL via Testcontainers (skipped when Docker is unavailable).
- Tests: JUnit 5 + Mockito, MockMvc for controllers
- No paid APIs — Ollama only, or free tiers (Tavily/Brave for search)