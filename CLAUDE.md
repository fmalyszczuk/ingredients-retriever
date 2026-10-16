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
  (X-XSRF-TOKEN); passwords are BCrypt-hashed, never logged or returned; login failures must all look the same.
- Security: the server must never fetch a user-supplied URL directly. Anything that takes a URL from a user, a
  recipe page or the LLM goes through net.SafePageFetcher / net.UrlGuard (SSRF protection: http(s) on ports 80/443
  only, public addresses only, DNS pinned to the checked addresses, redirects validated hop by hop, size/time limits).
  Never use Jsoup.connect(url), RestClient or HttpClient on such a URL.
- Database: the schema is owned by Flyway (src/main/resources/db/migration). Hibernate is `ddl-auto=validate`
  only. Never edit an applied migration; add a new V<n>__*.sql. Entities must match the migration.
- DB tests use a real PostgreSQL via Testcontainers (skipped when Docker is unavailable).
- Tests: JUnit 5 + Mockito, MockMvc for controllers
- No paid APIs — Ollama only, or free tiers (Tavily/Brave for search)