# Oursky TODO App

> A smart task management app with AI subtask suggestions, persistent storage, and one-command production deployment.

---

## ✨ What Makes This Different

| Feature | Why It Matters |
|---------|----------------|
| **AI Subtask Suggestions** | Type "Plan vacation" → get suggested subtasks via Qwen 3.6 Plus (with Gemini fallback) |
| **Persistent Storage** | Data survives server restarts with Magnum ORM and PostgreSQL/H2 |
| **One-Command Deploy** | Production-ready on GCP with Supabase in minutes |
| **Cost-Optimized** | Runs for ~$5/month with e2-micro instances + Supabase free tier |
| **Zero-Downtime Updates** | Rolling restarts keep your app always available |
| **High Availability** | 2 replicas for both frontend and backend in production |
| **Direct-Style Scala** | No monad transformers - simpler code with structured concurrency |

---

## 🎯 Quick Start

### Run Locally

```bash
# Backend (uses H2 in-memory by default)
cd backend && sbt run

# Frontend (new terminal)
cd frontend && npm install && npm run dev
```

### Run with Docker

```bash
# Build Docker image with jlink JRE
cd backend
docker build -t todo-backend:dev .
docker run -p 8080:8080 todo-backend:dev
```

### Run with Custom Database

```bash
# Connect to any PostgreSQL database
export DB_TYPE="postgres"
export DB_URL="jdbc:postgresql://host:5432/dbname"
export DB_USER="user"
export DB_PASSWORD="password"
cd backend && sbt run
```

---

## 🐳 Docker

### Build

```bash
cd backend
sbt stage                          # Build staged layout
cp -r target/universal/stage target/docker-stage
docker build -t todo-backend:dev .
```

### Run

```bash
docker run -p 8080:8080 todo-backend:dev
```

### Test CRUD

```bash
curl http://localhost:8080/health
curl -X POST http://localhost:8080/api/todos -H "Content-Type: application/json" -d '{"title":"Test"}'
curl http://localhost:8080/api/todos
```

### jlink Modules

The Docker image uses a minimal JRE created with jlink. Required modules:

```bash
jlink --add-modules \
  java.sql,java.naming,java.logging,java.net.http,java.management,jdk.unsupported
```

---

## 🚀 Deployment

### Deploy to Production

```bash
# Set required environment variables
export GCP_PROJECT_ID="your-gcp-project"
export QWEN_API_KEY="your-qwen-key"
export GEMINI_API_KEY="your-gemini-key"
export SUPABASE_PASSWORD="your-supabase-password"

# Deploy (interactive prompts for any missing vars)
./deploy.sh
```

**Required credentials:**
- `GCP_PROJECT_ID` — Your GCP project ID
- `QWEN_API_KEY` — OpenRouter API key for Qwen 3.6 Plus (Primary AI)
- `GEMINI_API_KEY` — Google Gemini API key (Fallback AI)
- `SUPABASE_PASSWORD` — Supabase PostgreSQL database password

**Defaults (can be overridden):**
- `SUPABASE_HOST` — `aws-1-us-east-2.pooler.supabase.com`
- `SUPABASE_PORT` — `5432`
- `SUPABASE_USER` — `postgres.<project-ref>`
- `SUPABASE_DB` — `postgres`

See [DEPLOYMENT-README.md](DEPLOYMENT-README.md) for detailed deployment guide.

---

## 🛠️ Tech Stack

**Backend:** Scala 3.8.3, Tapir, Netty Sync, Magnum, uPickle | **Frontend:** Vue 3, Vite | **AI:** Qwen 3.6 Plus & Google Gemini | **Database:** PostgreSQL (Supabase) / H2 (dev) | **Infrastructure:** GCP GKE, Kubernetes | **Docker:** jlink minimal JRE

---

## 📚 Documentation

- [Deployment Guide](DEPLOYMENT-README.md) — Detailed GCP/Kubernetes/Supabase setup
- [API Reference](#api-reference) — Below

---

## API Reference

### Todos
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/todos` | List all todos (sorted by creation date) |
| `POST` | `/api/todos` | Create todo |
| `GET` | `/api/todos/:id` | Get todo by ID |
| `PUT` | `/api/todos/:id` | Update todo (title and/or completed) |
| `DELETE` | `/api/todos/:id` | Delete todo (cascades to subtasks) |

### Subtasks
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/todos/:todoId/subtasks` | Add subtask (supports nesting up to 4 levels) |
| `PUT` | `/api/todos/:todoId/subtasks/:subtaskId` | Update subtask completion |
| `DELETE` | `/api/todos/:todoId/subtasks/:subtaskId` | Delete subtask |

### AI
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/ai/suggestions` | Get AI subtask suggestions |

### Health
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check |

---

## Database

### Development
Uses embedded H2 in-memory database (`jdbc:h2:mem:todo`). No persistence - data resets on restart.

### Production
Uses Supabase (PostgreSQL) with connection pooling. Schema is initialized automatically via Kubernetes init container on deploy.

### Schema
The application uses Magnum ORM with default CamelToSnakeCase mapping:

| Class | Table |
|-------|-------|
| `TodoRow` | `todo_row` |
| `SubtaskRow` | `subtask_row` |

You can customize table names using a custom `SqlNameMapper`:

```scala
import com.augustnagro.magnum.*

object MyCustomMapper extends SqlNameMapper:
  def toTableName(className: String): String = className match
    case "TodoRow" => "todos"   // Custom: TodoRow -> todos
    case "SubtaskRow" => "subtasks"
    case _ => SqlNameMapper.CamelToSnakeCase.toTableName(className)

  def toColumnName(fieldName: String): String = 
    SqlNameMapper.CamelToSnakeCase.toColumnName(fieldName)

@Table(H2DbType, MyCustomMapper)
case class TodoRow(...)
```

Note: Custom mapper requires the mapper object to be correctly resolved by the Scala compiler in your build context.

### Configuration
Database settings are controlled via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_TYPE` | Database type (`h2` or `postgres`) | `h2` |
| `DB_URL` | JDBC connection URL | `jdbc:h2:mem:todo;DB_CLOSE_DELAY=-1` |
| `DB_USER` | Database username | `sa` |
| `DB_PASSWORD` | Database password | `""` |

---

## Error Handling

The backend uses `Either[TodoError, T]` for expected errors:

| Error | HTTP Status | Description |
|-------|-----------|-------------|
| `TodoError.NotFound` | 404 | Todo not found |
| `TodoError.MaxDepthExceeded` | 400 | Subtask nesting exceeds 4 levels |

---

## License

MIT
