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

- Add recipes by URL (scrapes the page's `schema.org/Recipe` data), by dish
  name ("spaghetti carbonara" — the local LLM suggests a typical ingredient
  list), from an uploaded file (PDF, DOCX, TXT, or a screenshot, photo or
  scanned PDF read by a local vision model), or type ingredients in by hand.
- A merged shopping list that sums quantities for repeated ingredients,
  converting between compatible units (200 g + 1 kg = 1200 g) and keeping
  amounts that can't be added (1 pcs onion + 200 g onion) in separate rows.
- Mark items purchased, edit quantity/unit, or clear the whole list.
- Automatic unit conversion within the same unit family (weight: mg/g/kg/lb,
  volume: ml/l/pint/oz/tsp/tbsp/cup) — count-based units like `pcs` are left
  alone since they can't be converted.
- A `/chat` endpoint that runs a local LLM tool-calling loop against the
  real shopping-list service, so natural-language requests actually change
  the data. It remembers the conversation (send back the `conversationId` it
  returns), so follow-ups like "make it 3 kg" work. Each response says which
  tools actually ran and whether the list changed, and wiping the whole list
  through chat needs an explicit "yes" first.

## Tech stack

- Java 21, Maven, Spring Boot 4 (Spring Framework 7)
- Spring Web, Validation, Spring Data JPA, H2 (in-memory dev database)
- [Ollama](https://ollama.com) for local, free LLM tool-calling
- Jsoup for recipe page scraping, Apache PDFBox and POI for reading uploaded PDF/DOCX files

## Getting started

Requirements: Java 21, and [Ollama](https://ollama.com) running locally if
you want to use `/chat`.

```powershell
# pull the model once, if you want the chat and from-text/from-file endpoints
ollama pull llama3.2

# only needed to upload images (screenshots/photos) of recipes
ollama pull gemma3:4b

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
| `GET` | `/recipes/{id}` | Get one recipe |
| `DELETE` | `/recipes/{id}` | Delete a recipe (`?removeFromShoppingList=true` also subtracts its ingredients from the list) |
| `POST` | `/recipes` | Add a recipe by typing in its ingredients |
| `POST` | `/recipes/from-url` | Add a recipe by scraping a recipe page |
| `POST` | `/recipes/from-text` | Add a recipe from a dish name (LLM-suggested ingredients) |
| `POST` | `/recipes/from-file` | Add a recipe from an uploaded PDF, DOCX, TXT or image (multipart, field `file`) |
| `POST` | `/chat` | Talk to the shopping list in plain English (optional `conversationId` for memory) |
| `DELETE` | `/chat/{conversationId}` | Forget a conversation |

## Project layout

```
controller/     REST endpoints
service/        Business logic (shopping list, recipes, unit conversion)
repository/     Spring Data JPA repositories
domain/         JPA entities
dto/            Request/response records
agent/          Ollama tool-calling agent for /chat
extraction/     Recipe parsing (URL scraping, dish-name lookup and file reading via Ollama, ingredient line parsing)
```
