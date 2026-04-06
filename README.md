# Oursky TODO App

> A smart task management app with AI subtask suggestions, persistent storage, and one-command production deployment.

---

## ✨ What Makes This Different

| Feature | Why It Matters |
|---------|----------------|
| **AI Subtask Suggestions** | Type "Plan vacation" → get suggested subtasks like "Book flights", "Research hotels" |
| **Persistent Storage** | Data survives server restarts with Slick ORM and PostgreSQL/H2 |
| **One-Command Deploy** | Production-ready on GCP with Supabase in minutes |
| **Cost-Optimized** | Runs for ~$5/month with e2-micro instances + Supabase free tier |
| **Zero-Downtime Updates** | Rolling restarts keep your app always available |
| **High Availability** | 2 replicas for both frontend and backend in production |

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

### Run Compiled Binary

```bash
cd backend

# Option 1: Using the startup script (recommended)
./start.sh

# Option 2: Build and run universal stage binary (recommended over assembly JAR)
sbt "Universal / stage"
target/universal/stage/bin/oursky-todo-backend

# Option 3: GraalVM Native (instant startup, requires GraalVM)
./build-native.sh
./todo-backend-native
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

## ⚡ GraalVM Native Image (Optional)

For **instant startup (<100ms)** and **low RAM usage**, build a native binary:

### Prerequisites

```bash
# Install GraalVM (via SDKMAN recommended)
curl -s "https://get.sdkman.io" | bash
sdk install graalvm

# Install native-image component
gu install native-image
```

### Build
```bash
cd backend
./build-native.sh
```

> **Note**: The `sbt assembly` JAR has known configuration merge issues with Pekko and should be avoided. Use `sbt "Universal / stage"` instead.

This creates `todo-backend-native` - a native executable with:
- ⚡ Startup time: <100ms
- 💾 RAM usage: ~50MB (vs 200MB+ JVM)

### Run

```bash
# Set environment variables
export QWEN_API_KEY="your-key"
export GEMINI_API_KEY="your-key"

# Run native binary
./todo-backend-native
```

---

## 🛠️ Tech Stack

**Backend:** Scala 3, Apache Pekko (Actor), Slick 3.5 | **Frontend:** Vue 3, Vite | **AI:** Qwen 3.6 Plus & Google Gemini | **Database:** PostgreSQL (Supabase) / H2 (dev) | **Infrastructure:** GCP GKE, Kubernetes

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

---

## Database

### Development
Uses embedded H2 database with auto-server mode. Data persists to `backend/data/todo-db.mv.db`.

### Production
Uses Supabase (PostgreSQL) with connection pooling. Schema is initialized automatically via Kubernetes init container on deploy.

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
