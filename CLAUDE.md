# PantryPal — AI Recipe → Shopping List Agent

## What this is
Spring Boot backend that takes a recipe (URL, name, or screenshot/PDF)
and produces a merged shopping list via a local LLM agent.

## Stack
- Java 21, Maven, Spring Boot (latest stable)
- Spring Web, Validation, Spring Data JPA, H2 (dev), DevTools
- Ollama (local, free) — llama3.2/qwen2.5 for text extraction & tool-calling
- Jsoup for URL scraping, Apache POI/PDFBox for doc parsing, Tesseract for OCR
  (or a vision model in Ollama as an alternative to OCR)

## Architecture
Controller → Service → Repository, standard layering.
Agent layer: ChatController → Ollama tool-calling → real Java methods
(addItem, removeItem, listItems) → aggregation logic merges duplicate
ingredients across recipes.

## Planned endpoints
- POST /recipes/from-url
- POST /recipes/from-text (name-based search)
- POST /recipes/from-file (doc/pdf/screenshot)
- GET  /shopping-list

## Conventions
- Tests: JUnit 5 + Mockito, MockMvc for controllers
- No paid APIs — Ollama only, or free tiers (Tavily/Brave for search)