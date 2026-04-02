# Kubernetes Deployment for TODO App

This guide explains how to deploy the TODO application with AI suggestions to a Kubernetes cluster.

## Quick Start

### Deploy to GCP (Recommended)
```bash
./deploy.sh  # Interactive one-command deployment
```

### Manual Deployment
Follow the steps below for manual deployment or non-GCP clusters.

## Building Docker Images

### For GCP Deployment (via deploy.sh)
The deployment script automatically builds and pushes images:
```bash
# Backend image pushed to GCR
gcr.io/your-project-id/todo-backend:v3

# Frontend image pushed to GCR  
gcr.io/your-project-id/todo-frontend:v3
```

### For Local/Manual Deployment
```bash
# Build backend image
docker build -f Dockerfile.backend -t todo-backend:latest .

# Build frontend image
docker build -f Dockerfile.frontend -t todo-frontend:latest .
```

## Deploying to Kubernetes

### Using Cluster Management Scripts (Recommended)

After initial deployment, use the management scripts:

**Start the cluster:**
```bash
./start-cluster.sh
```

This will:
- Create namespace `todo-app-prod` if needed
- Apply all Kubernetes manifests from `gcp-deployment.yaml`
- Scale deployments to 1 replica each
- Wait for pods to become ready
- Display the external IP address

### Manual Deployment

1. Create the namespace and apply manifests:
```bash
kubectl apply -f gcp-deployment.yaml
```

2. Update the secret with your actual Gemini API key:
```bash
export GEMINI_API_KEY="your-api-key-here"
kubectl -n todo-app-prod create secret generic todo-secret \
  --from-literal=gemini-api-key="$GEMINI_API_KEY" --dry-run=client -o yaml | kubectl apply -f -
```

3. Scale deployments:
```bash
kubectl scale deployment todo-backend-deployment --replicas=1 -n todo-app-prod
kubectl scale deployment todo-frontend-deployment --replicas=1 -n todo-app-prod
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
- **Namespace**: `todo-app-prod` - Isolated environment for production
- **todo-backend-deployment**: Scala backend service running on port 8080
- **todo-backend-service**: Internal ClusterIP service for backend
- **todo-frontend-deployment**: Vue frontend with Nginx running on port 80
- **todo-frontend-service**: External LoadBalancer service for frontend access
- **todo-secret**: Secret containing the Gemini API key

## Cluster Management Scripts

### Start the Cluster
Brings all components up and scales to 1 replica each:
```bash
./start-cluster.sh
```

**What it does:**
- Creates namespace `todo-app-prod` if needed
- Applies Kubernetes manifests from `gcp-deployment.yaml`
- Scales deployments to 1 replica each
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

To scale the deployments:
```bash
# Scale backend
kubectl -n todo-app-prod scale deployment/todo-backend-deployment --replicas=2

# Scale frontend
kubectl -n todo-app-prod scale deployment/todo-frontend-deployment --replicas=2
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
kubectl logs -f -l app=todo-frontend -n todo-app-prod
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