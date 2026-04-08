# Kubernetes Deployment for TODO App

This guide explains how to deploy the TODO application with AI suggestions to a Kubernetes cluster on GCP with Supabase as the production database.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        User / Browser                            │
└──────────────────────────────┬───────────────────────────────────┘
                               │ HTTP
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                        GKE Cluster                               │
│                                                                  │
│  ┌─────────────────────┐      ┌──────────────────────────────┐  │
│  │ LoadBalancer Svc    │      │  todo-secret (K8s Secret)    │  │
│  │ (port 80 → 80)      │      │  - Qwen API key             │  │
│  │                      │      │  - Gemini API key            │  │
│  └──────────┬──────────┘      │  - Supabase credentials      │  │
│             │                 └──────────────┬───────────────┘  │
│             ▼                                │                  │
│  ┌─────────────────────┐      ┌──────────────▼───────────────┐  │
│  │ Frontend (×2)       │      │  db-migrations (ConfigMap)   │  │
│  │ Vue 3 + Nginx       │      │  - V1__initial_schema.sql    │  │
│  └──────────┬──────────┘      └──────────────┬───────────────┘  │
│             │ HTTP                            │                  │
│             ▼                                 │                  │
│  ┌─────────────────────┐                      │                  │
│  │ Backend (×2)        │                      │                  │
│  │ Scala + Tapir + Netty │◄─────────────────────┘                  │
│  │                     │                                         │
│  │  ┌───────────────┐  │                                         │
│  │  │ db-migrate    │  │  (init container)                       │
│  │  │ postgres:17   │  ��                                         │
│  │  └───────────────┘  │                                         │
│  └──────────┬──────────┘                                         │
└─────────────┼────────────────────────────────────────────────────┘
               │ JDBC (PostgreSQL)
               ▼
┌──────────────────────────────────────────────────────────────────┐
│  Supabase (PostgreSQL)                                           │
│  <supabase-host>:<supabase-port>                                 │
└──────────────────────────────────────────────────────────────────┘ 

  ┌──────────────────┐
  │  GCR Registry    │  (deployment-time only)
  │  - todo-backend  │────► docker pull on pod start
  │  - todo-frontend │
  └──────────────────┘
```

## Quick Start

### Deploy to GCP (Recommended)

```bash
# Set required environment variables
export GCP_PROJECT_ID="your-gcp-project"
export QWEN_API_KEY="your-qwen-key"
export GEMINI_API_KEY="your-gemini-key"
export SUPABASE_PASSWORD="your-supabase-password"

# Deploy (prompts for any missing values)
./deploy.sh
```

### Manual Deployment

Follow the steps below for manual deployment or non-GCP clusters.

## Building Docker Images

### Docker Build (New Stack)

The backend uses a two-stage Docker build with jlink minimal JRE:

```bash
cd backend

# Stage 1: Build the project
sbt stage

# Stage 2: Copy staged artifacts
cp -r target/universal/stage target/docker-stage

# Build Docker image with jlink
docker build -t gcr.io/your-project-id/todo-backend:latest .
```

### For GCP Deployment (via deploy.sh)

The deployment script automatically builds and pushes images:

```bash
# Backend image pushed to GCR
gcr.io/your-project-id/todo-backend:latest

# Frontend image pushed to GCR
gcr.io/your-project-id/todo-frontend:latest
```

### For Local/Manual Deployment

```bash
# Build backend image (new Dockerfile in backend/)
docker build -t todo-backend:latest ./backend

# Build frontend image
docker build -f Dockerfile.frontend -t todo-frontend:latest .
```

### jlink Modules

The Docker image uses a minimal JRE created with jlink:

```bash
jlink --add-modules \
  java.sql,java.naming,java.logging,java.net.http,java.management,jdk.unsupported
```

Required because:
- `java.sql` - Magnum database access
- `java.naming` - JNDI for HikariCP
- `java.logging` - Logback
- `java.net.http` - sttp client for AI API calls
- `java.management` - JMX (optional monitoring)
- `jdk.unsupported` - sun.misc.Unsafe (Scala runtime)

## Database Configuration

### Supabase Setup (Production)

1. Create a Supabase project at https://supabase.com
2. Go to Project Settings → Database → Connection string
3. Note the following values:
   - **Host**: e.g., `aws-1-us-east-2.pooler.supabase.com`
   - **Port**: `5432` (direct) or `6543` (pooler)
   - **User**: e.g., `postgres.<project-ref>`
   - **Password**: Your database password
   - **Database**: `postgres`

### Schema Initialization

Database schema is automatically initialized on deployment via a Kubernetes init container:

1. The `db-migrate` init container runs before the backend starts
2. It uses `postgres:17-alpine` with `psql` to execute migration scripts
3. Migration scripts are stored in `backend/src/main/resources/db/` as `V1__initial_schema.sql`
4. Uses `CREATE TABLE IF NOT EXISTS` for idempotent execution — safe to run on every deploy

**Schema:** The new stack uses Magnum ORM with default CamelToSnakeCase mapping:

| Class | Table |
|-------|-------|
| `TodoRow` | `todo_row` |
| `SubtaskRow` | `subtask_row` |

To use custom table names like `todos`/`subtasks`, define a custom SqlNameMapper:

```scala
import com.augustnagro.magnum.*

object MyCustomMapper extends SqlNameMapper:
  def toTableName(className: String): String = className match
    case "TodoRow" => "todos"
    case "SubtaskRow" => "subtasks"
    case _ => SqlNameMapper.CamelToSnakeCase.toTableName(className)

  def toColumnName(fieldName: String): String = 
    SqlNameMapper.CamelToSnakeCase.toColumnName(fieldName)

@Table(H2DbType, MyCustomMapper)
case class TodoRow(...)
```

Default schema:

```sql
CREATE TABLE IF NOT EXISTS todo_row (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS subtask_row (
  id BIGSERIAL PRIMARY KEY,
  todo_id BIGINT NOT NULL,
  title VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT false,
  parent_id BIGINT,
  depth INT NOT NULL DEFAULT 1,
  CONSTRAINT fk_subtask_todo FOREIGN KEY (todo_id) REFERENCES todo_row(id) ON DELETE CASCADE
);
```

To add future migrations, create new SQL files (e.g., `V2__add_indexes.sql`) and update the init container command in `gcp-deployment.yaml`.

### Local Development (H2)

For local development, the app uses an embedded H2 database by default. No external database setup is required.

```bash
cd backend && export QWEN_API_KEY="your-key" && export GEMINI_API_KEY="your-key" && sbt run
```

To connect to a local PostgreSQL instance instead:

```bash
export DB_TYPE="postgres"
export DB_URL="jdbc:postgresql://localhost:5432/todo_dev"
export DB_USER="postgres"
export DB_PASSWORD="password"
cd backend && sbt run
```

## Deploying to Kubernetes

### Using deploy.sh (Recommended)

The `deploy.sh` script handles the entire deployment process:

1. Validates prerequisites (gcloud, kubectl, docker)
2. Builds backend JAR with `sbt assembly`
3. Builds and pushes Docker images to GCR
4. Creates GKE cluster (if not exists)
5. Applies Kubernetes manifests with environment variable substitution
6. Waits for pods to become ready

```bash
./deploy.sh
```

### Manual Deployment

1. Create the namespace and apply manifests:

```bash
kubectl create namespace todo-app-prod --dry-run=client -o yaml | kubectl apply -f -
```

2. Create the secret with your credentials:

```bash
export QWEN_API_KEY="your-qwen-key"
export GEMINI_API_KEY="your-api-key"
export SUPABASE_HOST="aws-1-us-east-2.pooler.supabase.com"
export SUPABASE_PORT="5432"
export SUPABASE_USER="postgres.<project-ref>"
export SUPABASE_PASSWORD="your-password"
export SUPABASE_DB="postgres"
export PROJECT_ID="your-gcp-project"

envsubst < gcp-deployment.yaml | kubectl apply -f -
```

3. Wait for deployments to roll out:

```bash
kubectl rollout status deployment/todo-backend-deployment -n todo-app-prod
kubectl rollout status deployment/todo-frontend-deployment -n todo-app-prod
```

## Accessing the Application

After deployment, you can access the application:

1. Get the external IP of the frontend service:

```bash
kubectl -n todo-app-prod get services
```

2. Visit `http://<EXTERNAL-IP>` in your browser.

3. Or use the start script which displays the URL automatically:

```bash
./start-cluster.sh  # Shows: Access your application at: http://<IP>
```

## Components

The deployment includes:

- **Namespace**: `todo-app-prod` — Isolated environment for production
- **todo-secret**: Secret containing Qwen and Gemini API keys and Supabase credentials
- **db-migrations**: ConfigMap containing SQL migration scripts
- **todo-backend-deployment**: Scala backend service (2 replicas) running on port 8080
  - **Init container**: `db-migrate` — Runs database migrations before app starts
- **todo-backend-service**: Internal ClusterIP service for backend
- **todo-frontend-deployment**: Vue frontend with Nginx (2 replicas) running on port 80
- **todo-frontend-service**: External LoadBalancer service for frontend access

## Cluster Management Scripts

### Start the Cluster

Brings all components up and scales to 2 replicas each:

```bash
./start-cluster.sh
```

**What it does:**

- Creates namespace `todo-app-prod` if needed
- Applies Kubernetes manifests from `gcp-deployment.yaml`
- Scales deployments to 2 replicas each
- Waits for pods to become ready
- Displays the external IP address

### Stop the Cluster

Gracefully stops all pods while preserving configuration:

```bash
./stop-cluster.sh
```

**What it does:**

- Scales all deployments to 0 replicas
- Waits for pod termination
- Preserves services (to avoid reconfiguration)

**Note:** To stop LoadBalancer charges completely:

```bash
kubectl delete svc -n todo-app-prod --all
```

### Restart the Cluster

Performs a zero-downtime rolling restart:

```bash
./restart-cluster.sh  # Rolling restart (recommended)
```

For full stop/start instead:

```bash
ROLLING=false ./restart-cluster.sh
```

**What it does:**

- Restarts backend first, waits for completion
- Then restarts frontend
- Maintains availability throughout

### Quick Reference Table

| Command | Description |
|---------|-------------|
| `./start-cluster.sh` | Start all services |
| `./stop-cluster.sh` | Stop all services (preserve config) |
| `./restart-cluster.sh` | Rolling restart (zero-downtime) |
| `ROLLING=false ./restart-cluster.sh` | Full stop/start restart |

## Scaling

Default production configuration uses 2 replicas for both frontend and backend. To scale further:

```bash
# Scale backend
kubectl -n todo-app-prod scale deployment/todo-backend-deployment --replicas=3

# Scale frontend
kubectl -n todo-app-prod scale deployment/todo-frontend-deployment --replicas=3
```

## Troubleshooting

### Check pod status:

```bash
kubectl get pods -n todo-app-prod
```

### View pod logs:

```bash
# Backend logs
kubectl logs -f -l app=todo-backend -n todo-app-prod

# Frontend logs
kubectl logs -f -l todo-frontend -n todo-app-prod

# Init container (database migration) logs
kubectl logs -l app=todo-backend -n todo-app-prod -c db-migrate
```

### Check rollout status:

```bash
kubectl rollout status deployment/todo-backend-deployment -n todo-app-prod
kubectl rollout status deployment/todo-frontend-deployment -n todo-app-prod
```

### View rollout history:

```bash
kubectl rollout history deployment/todo-backend-deployment -n todo-app-prod
```

### Database connectivity issues:

```bash
# Check if init container completed successfully
kubectl describe pod -l app=todo-backend -n todo-app-prod | grep -A 10 "Init Containers"

# View migration logs
kubectl logs -l app=todo-backend -n todo-app-prod -c db-migrate --previous
```

### Common issues:

| Issue | Solution |
|-------|----------|
| Backend pod not starting | Check init container logs for migration errors |
| 502 Bad Gateway | Backend may still be starting up; check readiness probe |
| Database connection refused | Verify Supabase credentials and network access |
| ImagePullBackOff | Ensure GCR images exist and pull secrets are configured |
| java.lang.ClassNotFoundException | Missing jlink module - ensure jdk.unsupported is included |
| Table "TODO_ROW" not found | Schema mismatch - ensure V1__initial_schema.sql uses `todo_row` not `todos` |

### Docker Troubleshooting

```bash
# Check container logs
docker logs <container-id>

# Run interactively for debugging
docker run -it todo-backend:dev /bin/bash

# Check jlink runtime
docker run todo-backend:dev ls -la /opt/java/bin/

# Test inline
docker exec <container-id> /opt/java/bin/java -jar /app/lib/oursky-todo-backend_3-1.0.0.jar
```

### jlink Issues

If you see `java.lang.ClassNotFoundException: sun.misc.Unsafe`, ensure jlink includes `jdk.unsupported`:

```bash
jlink --add-modules java.sql,java.naming,java.logging,java.net.http,java.management,jdk.unsupported
```

---

## Credentials Rotation

### AI API Keys (Qwen / Gemini)

1. Obtain new API key from provider:
   - **Qwen**: Get from OpenRouter (https://openrouter.ai/keys)
   - **Gemini**: Get from Google AI Studio (https://aistudio.google.com/app/apikey)

2. Update Kubernetes secret:

```bash
# Update Qwen API key
kubectl create secret generic todo-secret \
  --from-literal=qwen-api-key=your-new-key \
  --dry-run=client -o yaml | kubectl apply -f -

# Update Gemini API key
kubectl create secret generic todo-secret \
  --from-literal=gemini-api-key=your-new-key \
  --dry-run=client -o yaml | kubectl apply -f -
```

3. Restart pods to pick up new credentials:

```bash
kubectl rollout restart deployment/todo-backend-deployment -n todo-app-prod
```

### Supabase Credentials

1. Update password in Supabase Dashboard:
   - Go to Supabase Dashboard → Settings → Database
   - Under "Database password", click "Reset password"

2. Update Kubernetes secret:

```bash
kubectl create secret generic todo-secret \
  --from-literal=supabase-password=your-new-password \
  --dry-run=client -o yaml | kubectl apply -f -
```

3. Restart backend pods:

```bash
kubectl rollout restart deployment/todo-backend-deployment -n todo-app-prod
```

### Database Connection (Advanced)

If you need to rotate the PostgreSQL user credentials:

1. Update user in Supabase or your PostgreSQL instance
2. Update the following secrets:
   - `supabase-user`
   - `supabase-password`
   - `supabase-db-url` (if connection string changed)

```bash
kubectl create secret generic todo-secret \
  --from-literal=supabase-user=postgres.xxx \
  --from-literal=supabase-password=new-password \
  --from-literal=supabase-db-url=jdbc:postgresql://host:5432/db \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl rollout restart deployment/todo-backend-deployment -n todo-app-prod
```
