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
│  │ (port 80 → 8080)    │      │  - Qwen API key             │  │
│  │                      │      │  - Gemini API key            │  │
│  └──────────┬──────────┘      │  - Supabase credentials      │  │
│             │                 └──────────────┬───────────────┘  │
│             ▼                                │                  │
│  ┌─────────────────────┐                     │                  │
│  │ Backend (×2)        │                     │                  │
│  │ Scala + Pekko HTTP  │◄────────────────────┘                  │
│  │                     │                                         │
│  │  ┌───────────────┐  │                                         │
│  │  │ GuardianActor │  │  Routes commands to:                    │
│  │  │               │  │  - TodoActor (CRUD)                     │
│  │  │               │  │  - AISuggestionActor (AI)              │
│  │  └───────────────┘  │                                         │
│  └──────────┬──────────┘                                         │
└─────────────┼────────────────────────────────────────────────────┘
              │ JDBC (PostgreSQL, SSL)
              ▼
┌──────────────────────────────────────────────────────────────────┐
│  Supabase (PostgreSQL)                                           │
│  aws-1-us-east-2.pooler.supabase.com:5432                        │
└──────────────────────────────────────────────────────────────────┘
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

### Production Image (jlink-optimized)

```bash
# Build backend image (~179MB)
docker build -f Dockerfile.backend -t todo-backend:latest .
```

The image uses a multi-stage build with `jlink` to create a custom minimal JRE containing only required modules.

### Native Image (Experimental)

```bash
# Build native image (blocked by Pekko/GraalVM Unsafe incompatibility)
docker build -f Dockerfile.native -t todo-backend-native:latest .
```

> **Note**: Native image compilation fails at runtime due to `sun.misc.Unsafe` incompatibility with Pekko 1.3.x. The Dockerfile is included for future use.

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

Database schema is automatically initialized on application startup. The `TodoApp` main method creates tables if they don't exist:

```scala
val tables = new Tables(profile)
Await.result(db.run(tables.createSchema), 10.seconds)
```

This uses `CREATE TABLE IF NOT EXISTS` for idempotent execution — safe to run on every deploy. No init container or migration scripts needed.

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

### Kubernetes Manifests

The `k8s/` directory contains all manifests:

| File | Description |
|---|---|
| `deployment.yaml` | Backend deployment with 2 replicas, resource limits, probes |
| `service.yaml` | ClusterIP service (port 80 → 8080) |
| `configmap.yaml` | Non-sensitive config (DB_TYPE, etc.) |
| `secret.yaml` | Sensitive data (DB credentials, API keys) |
| `hpa.yaml` | Horizontal Pod Autoscaler (2-10 replicas) |

### Manual Deployment

1. Create the namespace and apply manifests:

```bash
kubectl create namespace todo-app-prod --dry-run=client -o yaml | kubectl apply -f -
```

2. Update `k8s/secret.yaml` with base64-encoded credentials:

```bash
echo -n "your-db-user" | base64
echo -n "your-db-password" | base64
```

Edit `k8s/secret.yaml` with the encoded values, then apply:

```bash
kubectl apply -f k8s/secret.yaml -n todo-app-prod
```

3. Apply remaining manifests:

```bash
kubectl apply -f k8s/configmap.yaml -n todo-app-prod
kubectl apply -f k8s/deployment.yaml -n todo-app-prod
kubectl apply -f k8s/service.yaml -n todo-app-prod
kubectl apply -f k8s/hpa.yaml -n todo-app-prod
```

4. Wait for deployment to roll out:

```bash
kubectl rollout status deployment/todo-backend -n todo-app-prod
```

## Accessing the Application

After deployment, you can access the application:

1. Get the external IP of the service:

```bash
kubectl -n todo-app-prod get services
```

2. Visit `http://<EXTERNAL-IP>` in your browser.

## Components

The deployment includes:

- **Namespace**: `todo-app-prod` — Isolated environment for production
- **todo-secret**: Secret containing database credentials and API keys
- **todo-backend-config**: ConfigMap for non-sensitive settings
- **todo-backend**: Backend deployment (2 replicas) running on port 8080
  - **Security**: Non-root user, read-only filesystem, no privilege escalation
  - **Probes**: Liveness and readiness checks on `/health`
  - **Resources**: 250m-500m CPU, 512Mi-1Gi memory
- **todo-backend-hpa**: Autoscaler (2-10 replicas, CPU 70%, memory 80%)

## Scaling

Default production configuration uses 2 replicas. The HPA will auto-scale between 2-10 based on load.

Manual scaling:

```bash
kubectl -n todo-app-prod scale deployment/todo-backend --replicas=3
```

## Troubleshooting

### Check pod status:

```bash
kubectl get pods -n todo-app-prod
```

### View pod logs:

```bash
kubectl logs -f -l app=todo-backend -n todo-app-prod
```

### Check rollout status:

```bash
kubectl rollout status deployment/todo-backend -n todo-app-prod
```

### View rollout history:

```bash
kubectl rollout history deployment/todo-backend -n todo-app-prod
```

### Common issues:

| Issue | Solution |
|-------|----------|
| Backend pod not starting | Check pod logs for database connection errors |
| CrashLoopBackOff | Verify Supabase credentials in secret |
| Database connection refused | Verify SSL mode and network access to Supabase |
| ImagePullBackOff | Ensure image exists in registry |
| OOMKilled | Increase memory limit in deployment.yaml |
