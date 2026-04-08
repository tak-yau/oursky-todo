# AGENTS.md

## Quick Commands

```bash
# Run backend
cd backend && sbt run

# Run tests
cd backend && sbt test

# Docker build (requires staging step)
cd backend && sbt stage && cp -r target/universal/stage target/docker-stage && docker build -t todo-backend:dev .
```

## Architecture

- **Backend entrypoint**: `Main.scala` — starts Netty server on port 8080
- **Routes**: `TodoRoutes.scala` — defines Tapir endpoints
- **Database layer**: `TodoModel.scala` → `DB.scala` — uses Magnum ORM with Scala 3 context bounds
- **Models**: `db/Models.scala` (`TodoRow`, `SubtaskRow`) → table names are `todo_row`, `subtask_row` (CamelToSnakeCase default)

## Known Issues

- **H2 in-memory only**: Default DB is `jdbc:h2:mem:todo` — data resets on restart.
- **No CI/CD**: No GitHub Actions workflow for automated testing

## Configuration

| Env Var | Default | Notes |
|---------|---------|-------|
| `DB_TYPE` | `h2` | `h2` or `postgres` |
| `DB_URL` | `jdbc:h2:mem:todo;DB_CLOSE_DELAY=-1` | |
| `DB_USER` | `sa` | H2 default; postgres typically needs `postgres` |
| `DB_PASSWORD` | `""` | |
| `QWEN_API_KEY` | — | Required for AI suggestions |
| `GEMINI_API_KEY` | — | Fallback AI |

## Tech Stack

- Scala 3.8.3, Tapir 1.13.6, Magnum 1.3.1, uPickle 4.4.2
- sttp client 4.0.0 for HTTP calls
- munit for testing

## Tests

Run single test: `cd backend && sbt "testOnly *TodoServiceSuite"`