#!/bin/bash
# Stop Production Kubernetes Cluster
# This script gracefully stops all components of the Todo App production deployment

set -e  # Exit on error

# Configuration
NAMESPACE="todo-app-prod"
TIMEOUT=300  # Timeout in seconds for operations

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=========================================${NC}"
echo -e "${YELLOW}Stopping Production Kubernetes Cluster${NC}"
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

# Function to check if namespace exists
check_namespace() {
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        echo -e "${YELLOW}Namespace '${NAMESPACE}' does not exist. Already stopped?${NC}"
        exit 0
    fi
}

# Scale down deployments to zero
echo -e "\n${GREEN}[1/4] Scaling down deployments...${NC}"
kubectl scale deployment todo-backend-deployment --replicas=0 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
kubectl scale deployment todo-frontend-deployment --replicas=0 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
echo "  ✓ Scaled down all deployments to 0 replicas"

# Wait for pods to terminate
echo -e "\n${GREEN}[2/4] Waiting for pods to terminate...${NC}"
kubectl wait --for=delete pod --all -n "$NAMESPACE" --timeout="${TIMEOUT}s" || true
echo "  ✓ All pods terminated"

# Optionally delete services (commented out by default)
# Uncomment if you want to completely remove the cluster including LoadBalancer charges
echo -e "\n${GREEN}[3/4] Services status...${NC}"
kubectl get svc -n "$NAMESPACE" || true
echo "  ℹ Services are still present (to delete, uncomment lines below)"

# To completely stop and remove services (including LoadBalancer):
# echo -e "\n${YELLOW}[3/4] Deleting services...${NC}"
# kubectl delete svc todo-backend-service -n "$NAMESPACE" || true
# kubectl delete svc todo-frontend-service -n "$NAMESPACE" || true
# echo "  ✓ All services deleted"

# Show final status
echo -e "\n${GREEN}[4/4] Final cluster status...${NC}"
kubectl get all -n "$NAMESPACE" || true

# Summary
echo -e "\n${YELLOW}=========================================${NC}"
echo -e "${GREEN}✓ Production cluster stopped successfully!${NC}"
echo -e "${YELLOW}=========================================${NC}"
echo ""
echo "Summary:"
echo "  • All deployments scaled to 0 replicas"
echo "  • No pods are running"
echo "  • Services still exist (to avoid LoadBalancer charges, delete them)"
echo ""
echo "To completely remove resources and stop all charges:"
echo "  kubectl delete svc -n ${NAMESPACE} --all"
echo ""
echo "To restart the cluster later:"
echo "  ./start-cluster.sh"
