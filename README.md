# PantryPal — Backend

Give it a recipe, get a shopping list. PantryPal turns recipes (currently:
a URL or manually-typed ingredients) into ingredients, and merges them into
one running shopping list — duplicate ingredients across recipes are
summed instead of piling up as duplicates. There's also a chat endpoint
backed by a local LLM (via [Ollama](https://ollama.com)) so you can manage
the list in plain English: add items, remove them, mark things purchased,
or convert a quantity between units (e.g. "convert the flour from lbs to kg").

Frontend repo: https://github.com/fmalyszczuk/pantrypal-frontend

## Features

- Add recipes by URL (scrapes the page's `schema.org/Recipe` data) or type
  ingredients in by hand.
- A merged shopping list that sums quantities for repeated ingredients.
- Mark items purchased, edit quantity/unit, or clear the whole list.
- Automatic unit conversion within the same unit family (weight: mg/g/kg/lb,
  volume: ml/l/pint/oz/tsp/tbsp/cup) — count-based units like `pcs` are left
  alone since they can't be converted.
- A `/chat` endpoint that runs a local LLM tool-calling loop against the
  real shopping-list service, so natural-language requests actually change
  the data.

## Tech stack

- Java 21, Maven, Spring Boot 4 (Spring Framework 7)
- Spring Web, Validation, Spring Data JPA, H2 (in-memory dev database)
- [Ollama](https://ollama.com) for local, free LLM tool-calling
- Jsoup for recipe page scraping

## Getting started

Requirements: Java 21, and [Ollama](https://ollama.com) running locally if
you want to use `/chat`.

```powershell
# pull the model once, if you want the chat endpoint
ollama pull llama3.2

# run the backend
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`. Data lives in an in-memory H2
database, so it resets every time the app restarts — there's an H2 console
at `http://localhost:8080/h2-console` if you want to poke around while it's
running.

Run the tests with:

```powershell
.\mvnw.cmd test
```

## API overview

| Method | Path | What it does |
|---|---|---|
| `GET` | `/shopping-list` | List all shopping-list items |
| `POST` | `/shopping-list/items` | Add an item (sums into an existing one by name) |
| `PATCH` | `/shopping-list/items/{name}` | Update quantity, unit, or purchased status |
| `DELETE` | `/shopping-list/items/{name}` | Remove one item |
| `DELETE` | `/shopping-list` | Clear the whole list |
| `GET` | `/recipes` | List saved recipes |
| `POST` | `/recipes` | Add a recipe by typing in its ingredients |
| `POST` | `/recipes/from-url` | Add a recipe by scraping a recipe page |
| `POST` | `/chat` | Talk to the shopping list in plain English |

## Project layout

```
controller/     REST endpoints
service/        Business logic (shopping list, recipes, unit conversion)
repository/     Spring Data JPA repositories
domain/         JPA entities
dto/            Request/response records
agent/          Ollama tool-calling agent for /chat
extraction/     Recipe parsing (URL scraping, ingredient line parsing)
```
