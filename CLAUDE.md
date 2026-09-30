# PantryPal — AI Recipe → Shopping List Agent

## What this is
Spring Boot backend that takes a recipe (URL, name, or screenshot/PDF)
and produces a merged shopping list via a local LLM agent.

## Stack
- Java 21, Maven, Spring Boot (latest stable)
- Spring Web, Validation, Spring Data JPA, H2 (dev), DevTools
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
- Tests: JUnit 5 + Mockito, MockMvc for controllers
- No paid APIs — Ollama only, or free tiers (Tavily/Brave for search)