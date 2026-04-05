#!/bin/bash
# Restart Production Kubernetes Cluster
# This script performs a rolling restart of all components

set -e  # Exit on error

# Configuration
NAMESPACE="todo-app-prod"
TIMEOUT=300  # Timeout in seconds for operations
ROLLING=true  # Set to false for full stop/start instead of rolling restart

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=========================================${NC}"
echo -e "${YELLOW}Restarting Production Kubernetes Cluster${NC}"
echo -e "${YELLOW}Namespace: ${NAMESPACE}${NC}"
echo -e "${YELLOW}Mode: $([ "$ROLLING" = true ] && echo 'Rolling Restart' || echo 'Full Stop/Start')${NC}"
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

if [ "$ROLLING" = true ]; then
    echo -e "\n${GREEN}Performing ROLLING RESTART (zero-downtime)...${NC}"
    echo "This will restart pods one at a time, maintaining availability."
else
    echo -e "\n${YELLOW}Performing FULL STOP/START...${NC}"
    echo "This will stop all services first, then start them again."
fi
echo ""

# Function to perform rolling restart
rolling_restart() {
    echo -e "\n${GREEN}[1/4] Rolling restart: Backend deployment${NC}"
    kubectl rollout restart deployment todo-backend-deployment -n "$NAMESPACE"
    echo "  ✓ Restart initiated for backend"
    
    echo -e "\n${GREEN}[2/4] Waiting for backend rollout to complete...${NC}"
    kubectl rollout status deployment/todo-backend-deployment -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    echo "  ✓ Backend rollout completed"
    
    echo -e "\n${GREEN}[3/4] Rolling restart: Frontend deployment${NC}"
    kubectl rollout restart deployment todo-frontend-deployment -n "$NAMESPACE"
    echo "  ✓ Restart initiated for frontend"
    
    echo -e "\n${GREEN}[4/4] Waiting for frontend rollout to complete...${NC}"
    kubectl rollout status deployment/todo-frontend-deployment -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    echo "  ✓ Frontend rollout completed"
}

# Function to perform full stop/start restart
full_restart() {
    # Stop all deployments
    echo -e "\n${GREEN}[1/4] Stopping all deployments...${NC}"
    kubectl scale deployment todo-backend-deployment --replicas=0 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    kubectl scale deployment todo-frontend-deployment --replicas=0 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    echo "  ✓ All deployments stopped"
    
    # Wait for pods to terminate
    echo -e "\n${GREEN}[2/4] Waiting for pods to terminate...${NC}"
    kubectl wait --for=delete pod --all -n "$NAMESPACE" --timeout="${TIMEOUT}s" || true
    echo "  ✓ All pods terminated"
    
    # Start all deployments
    echo -e "\n${GREEN}[3/4] Starting all deployments...${NC}"
    kubectl scale deployment todo-backend-deployment --replicas=2 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    kubectl scale deployment todo-frontend-deployment --replicas=2 -n "$NAMESPACE" --timeout="${TIMEOUT}s"
    echo "  ✓ All deployments started"
    
    # Wait for pods to be ready
    echo -e "\n${GREEN}[4/4] Waiting for pods to become ready...${NC}"
    kubectl wait --for=condition=ready pod --selector="app in (todo-backend, todo-frontend)" \
        -n "$NAMESPACE" --timeout="${TIMEOUT}s" || true
    echo "  ✓ Pods are becoming available"
}

# Execute the appropriate restart strategy
if [ "$ROLLING" = true ]; then
    rolling_restart
else
    full_restart
fi

# Show final status
echo -e "\n${YELLOW}=========================================${NC}"
echo -e "${GREEN}✓ Cluster restarted successfully!${NC}"
echo -e "${YELLOW}=========================================${NC}"
echo ""
echo "Cluster Status:"
kubectl get all -n "$NAMESPACE" --show-labels
echo ""

# Get external IP
EXTERNAL_IP=$(kubectl get svc todo-frontend-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
if [ -z "$EXTERNAL_IP" ]; then
    EXTERNAL_IP="$(kubectl get svc todo-frontend-service -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Checking...")"
fi

echo "Access your application at: http://${EXTERNAL_IP}"
echo ""
echo "Rollout history:"
kubectl rollout history deployment/todo-backend-deployment -n "$NAMESPACE" | tail -4 | head -3
echo ""
echo "To check pod logs:"
echo "  kubectl logs -f -l app=todo-backend -n ${NAMESPACE}"
echo "  kubectl logs -f -l app=todo-frontend -n ${NAMESPACE}"
