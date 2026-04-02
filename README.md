# Oursky TODO App

> A smart task management app that suggests subtasks using AI. Deploy to production in one command.

---


## ✨ What Makes This Different

| Feature | Why It Matters |
|---------|----------------|
| **AI Subtask Suggestions** | Type "Plan vacation" → get suggested subtasks like "Book flights", "Research hotels" |
| **One-Command Deploy** | Production-ready on GCP in minutes, not days |
| **Cost-Optimized** | Runs for ~$5/month with e2-micro instances |
| **Zero-Downtime Updates** | Rolling restarts keep your app always available |

---


## 🎯 Quick Start
### Run Locally
```bash
# Backend
cd backend && export GEMINI_API_KEY="your-key" && sbt run

# Frontend (new terminal)
cd frontend && npm install && npm run dev
```

---

## 🚀 Deployment Commands

### Deploy to Production (Recommended)
```bash
./deploy.sh
```


See [DEPLOYMENT-README.md](DEPLOYMENT-README.md) for detailed deployment guide.

---

## 🛠️ Tech Stack

**Backend:** Scala 3, http4s, Cats Effect  |  **Frontend:** Vue 3, Vite  |  **AI:** Google Gemini API

---

## 📚 Documentation

- [Deployment Guide](DEPLOYMENT-README.md) — Detailed GCP/Kubernetes setup
- [API Reference](#api-reference) — Below

---

## API Reference

### Todos
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/todos` | List all todos |
| `POST` | `/api/todos` | Create todo |
| `PUT` | `/api/todos/:id` | Update todo |
| `DELETE` | `/api/todos/:id` | Delete todo |

### Subtasks
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/todos/:id/subtasks` | Add subtask |
| `PUT` | `/api/todos/:id/subtasks/:sid` | Toggle completion |
| `DELETE` | `/api/todos/:id/subtasks/:sid` | Delete subtask |

### AI
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/ai/suggestions` | Get AI suggestions |

---

## License
MIT
