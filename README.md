# Oursky TODO App

> A smart task management app with AI subtask suggestions, persistent storage, and one-command production deployment.

---

## ✨ What Makes This Different

| Feature | Why It Matters |
|---------|----------------|
| **AI Subtask Suggestions** | Type "Plan vacation" → get suggested subtasks like "Book flights", "Research hotels" |
| **Persistent Storage** | Data survives server restarts with Slick ORM and PostgreSQL/H2 |
| **Optimized Docker Image** | jlink-custom JRE reduces image size by 49% (352MB → 179MB) |
| **Cost-Optimized** | Runs for ~$5/month with e2-micro instances + Supabase free tier |
| **High Availability** | 2 replicas with rolling updates, HPA, and pod anti-affinity |

---

## 🎯 Quick Start

### Run Locally

```bash
# Backend (uses local H2 database by default)
cd backend && export QWEN_API_KEY="your-key" && export GEMINI_API_KEY="your-key" && sbt run

# Frontend (new terminal)
cd frontend && npm install && npm run dev
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

### Run from Docker Image

```bash
docker build -f Dockerfile.backend -t todo-backend:latest .
docker run -p 8080:8080 \
  -e DB_TYPE=h2 \
  -e QWEN_API_KEY="your-key" \
  todo-backend:latest
```

**Environment Variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | `8080` |
| `DB_TYPE` | Database type (`h2` or `postgres`) | `h2` |
| `DB_URL` | JDBC connection URL | `jdbc:h2:./data/todo-db` |
| `DB_USER` | Database username | `""` |
| `DB_PASSWORD` | Database password | `""` |
| `QWEN_API_KEY` | Qwen AI API key | (not set) |
| `GEMINI_API_KEY` | Gemini AI API key | (not set) |

---

## 🚀 Deployment

### Deploy to GKE

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

## 🐳 Docker Image

The production Docker image uses a multi-stage build with `jlink` to create a custom minimal JRE:

| Metric | Value |
|---|---|
| **Base** | Alpine 3.19 + custom JRE |
| **Size** | ~179MB (down from 352MB) |
| **JVM Flags** | Container-aware, G1GC, 256k thread stacks |

```bash
docker build -f Dockerfile.backend -t todo-backend:latest .
```

### Native Image (Experimental)

GraalVM native image compilation is currently blocked by a Pekko/GraalVM incompatibility with `sun.misc.Unsafe`. The `Dockerfile.native` is included for future use once Pekko releases a fix.

---

## 🛠️ Tech Stack

**Backend:** Scala 3, Apache Pekko HTTP (Actor-based), Slick 3.5 | **Frontend:** Vue 3, Vite | **AI:** Qwen 3.6 Plus & Google Gemini | **Database:** PostgreSQL (Supabase) / H2 (dev) | **Infrastructure:** GCP GKE, Kubernetes

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
| `PUT` | `/api/todos/:id` | Update todo (title and/or completed) |
| `DELETE` | `/api/todos/:id` | Delete todo (cascades to subtasks) |

### Subtasks
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/todos/:id/subtasks` | Add subtask (supports nesting up to 5 levels) |
| `PUT` | `/api/todos/:id/subtasks/:sid` | Update subtask completion |
| `DELETE` | `/api/todos/:id/subtasks/:sid` | Delete subtask |

### AI
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/ai/suggestions` | Get AI subtask suggestions |

### Health
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Health check endpoint |

---

## Database

### Development
Uses embedded H2 database with auto-server mode. Data persists to `backend/data/todo-db.mv.db`.

### Production
Uses Supabase (PostgreSQL) with SSL. Schema is initialized automatically on application startup.

### Configuration
Database settings are controlled via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_TYPE` | Database type (`h2` or `postgres`) | `h2` |
| `DB_URL` | JDBC connection URL | `jdbc:h2:./data/todo-db;AUTO_SERVER=TRUE` |
| `DB_USER` | Database username | `""` |
| `DB_PASSWORD` | Database password | `""` |

---

## License

MIT
