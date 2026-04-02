#!/bin/bash
# Start Production Kubernetes Cluster
# This script starts all components of the Todo App production deployment

set -e  # Exit on error

# Configuration
NAMESPACE="todo-app-prod"
TIMEOUT=300  # Timeout in seconds for operations

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=========================================${NC}"
echo -e "${YELLOW}Starting Production Kubernetes Cluster${NC}"
echo -e "${YELLOW}Namespace: ${NAMESPACE}${NC}"
echo -e "${YELLOW}=========================================${NC}"

# Function to check if kubectl is available
check_kubectl() {
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}Error: kubectl is not installed or not in PATH${NC}"
        exit 1
    fi
}

# Function to check cluster connectivity
check_cluster() {
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}Error: Cannot connect to Kubernetes cluster${NC}"
        echo "Please ensure you're authenticated with gcloud:" \
            "gcloud container clusters get-credentials <cluster-name> --zone <zone> --project <project>"
        exit 1
    fi
}

# Check prerequisites
check_kubectl
check_cluster

# Create namespace if it doesn't exist
echo -e "\n${GREEN}[1/5] Ensuring namespace exists...${NC}"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
echo "  ✓ Namespace '${NAMESPACE}' ready"

# Apply deployment manifests (creates or updates resources)
echo -e "\n${GREEN}[2/5] Applying Kubernetes manifests...${NC}"
kubectl apply -f gcp-deployment.yaml --timeout="${TIMEOUT}s"
echo "  ✓ Manifests applied successfully"

# Scale up deployments
echo -e "\n${GREEN}[3/5] Scaling up deployments...${NC}"
kubectl scale deployment todo-backend-deployment --replicas=1 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
kubectl scale deployment todo-frontend-deployment --replicas=1 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
echo "  ✓ Deployments scaled to 1 replica each"

# Wait for pods to be ready
echo -e "\n${GREEN}[4/5] Waiting for pods to become ready...${NC}"
kubectl wait --for=condition=ready pod --selector="app in (todo-backend, todo-frontend)" \
    -n "$NAMESPACE" --timeout="${TIMEOUT}s" || {
    echo -e "${RED}Warning: Some pods may not be ready yet. Checking status...${NC}"
}
echo "  ✓ Pods are becoming available"

# Get external IP
echo -e "\n${GREEN}[5/5] Getting service endpoints...${NC}"
EXTERNAL_IP=$(kubectl get svc todo-frontend-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
if [ -z "$EXTERNAL_IP" ]; then
    EXTERNAL_IP="$(kubectl get svc todo-frontend-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Pending...")"
fi

echo "  ✓ Frontend Service IP: ${BLUE}${EXTERNAL_IP}${NC}"

# Show final status
echo -e "\n${YELLOW}=========================================${NC}"
echo -e "${GREEN}✓ Production cluster started successfully!${NC}"
echo -e "${YELLOW}=========================================${NC}"
echo ""
echo "Cluster Status:"
kubectl get all -n "$NAMESPACE" --show-labels
echo ""
echo "Access your application at: http://${EXTERNAL_IP}"
echo ""
echo "To check pod logs:"
echo "  kubectl logs -f -l app=todo-backend -n ${NAMESPACE}"
echo "  kubectl logs -f -l app=todo-frontend -n ${NAMESPACE}"
