#!/bin/bash
#
# One-Command GCP Deployment Script for TODO App
# Optimized for minimum cost with interactive configuration
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if a command exists
cmd_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Verify prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing=()
    
    if ! cmd_exists gcloud; then
        missing+=("gcloud")
    fi
    
    if ! cmd_exists kubectl; then
        missing+=("kubectl")
    fi
    
    if ! cmd_exists docker; then
        missing+=("docker")
    fi
    
    if ! cmd_exists base64; then
        missing+=("base64")
    fi
    
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing required tools: ${missing[*]}"
        echo ""
        echo "Please install the missing tools:"
        echo "  - gcloud: https://cloud.google.com/sdk/docs/install"
        echo "  - kubectl: https://kubernetes.io/docs/tasks/tools/"
        echo "  - docker: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    log_success "All prerequisites satisfied!"
}

# Configuration - uses environment variables or defaults
collect_configuration() {
    echo ""
    log_info "=== GCP Deployment Configuration ==="
    echo ""
    
    # Use environment variables with sensible defaults
    PROJECT_ID=${GCP_PROJECT_ID:-"your-gcp-project-id-here"}  # Must be set for deployment
    ZONE=${GCP_ZONE:-"us-central1-a"}
    CLUSTER_NAME=${GKE_CLUSTER_NAME:-"todo-app-cluster"}
    GEMINI_API_KEY=${GEMINI_API_KEY:-"your-gemini-api-key-here"}  # Must be set for deployment
    USE_MICRO=${USE_MICRO:-"n"}  # Use e2-small by default (e2-micro too constrained)
    SINGLE_NODE=${SINGLE_NODE:-"y"}  # Single node for cost savings
    
    log_info "Configuration (set via environment variables):"
    log_info "  PROJECT_ID: $PROJECT_ID"
    log_info "  ZONE: $ZONE"
    log_info "  CLUSTER_NAME: $CLUSTER_NAME"
    log_info "  USE_MICRO: $USE_MICRO"
    log_info "  SINGLE_NODE: $SINGLE_NODE"
    log_info "  GEMINI_API_KEY: ${GEMINI_API_KEY:0:15}..."
    echo ""
}

# Build backend JAR
build_backend() {
    echo ""
    log_info "=== Building Backend ==="
    
    if [ ! -d "backend" ]; then
        log_error "Backend directory not found!"
        exit 1
    fi
    
    cd backend
    
    # Check if JAR already exists
    if ls target/scala-*/oursky-todo-backend*.jar 1>/dev/null 2>&1; then
        log_warn "Existing JAR found. Rebuilding..."
    fi
    
    # Build with sbt using assembly for fat JAR
    if cmd_exists sbt; then
        log_info "Running 'sbt assembly' (this may take a while on first run)..."
        sbt clean assembly || {
            log_error "Backend build failed!"
            exit 1
        }
    else
        log_error "sbt is required to build the backend but not found!"
        echo "Install sbt from: https://www.scala-sbt.org/"
        exit 1
    fi
    
    cd ..
    log_success "Backend built successfully!"
}

# Retry function with exponential backoff for GCP API quota limits
retry_gcp_command() {
    local max_attempts=5
    local attempt=1
    local wait_time=30  # Start with 30 seconds
    local output=""
    
    while [ $attempt -le $max_attempts ]; do
        log_info "Attempt $attempt/$max_attempts..."
        
        # Capture both output and exit code
        output=$("$@" 2>&1)
        local exit_code=$?
        
        if [ $exit_code -eq 0 ]; then
            echo "$output"
            return 0
        fi
        
        # Check if it's a quota error
        if echo "$output" | grep -qi "quota\|rate.limit\|429\|43"; then
            log_warn "GCP API quota limit hit. Waiting ${wait_time}s before retry..."
            sleep $wait_time
            wait_time=$((wait_time * 2))  # Exponential backoff
        else
            log_error "Command failed with exit code $exit_code"
            echo "$output" | tail -20
            return $exit_code
        fi
        
        attempt=$((attempt + 1))
    done
    
    log_error "All retry attempts exhausted"
    return 1
}

# Check if image exists in GCR
image_exists_in_gcr() {
    local image=$1
    set +e
    gcloud container images describe "$image" --quiet 2>/dev/null
    local result=$?
    set -e
    return $result
}

# Build and push Docker images
build_and_push_images() {
    echo ""
    log_info "=== Building Docker Images ==="
    
    # Set GCP project for docker
    export DOCKER_PROJECT="gcr.io/$PROJECT_ID"
    
    # Build backend image locally first (don't tag with GCR yet to avoid push)
    log_info "Building backend image..."
    docker build -f Dockerfile.backend -t "todo-backend:local" . || {
        log_error "Backend Docker build failed!"
        exit 1
    }
    log_success "Backend image built locally"
    
    # Build frontend image locally first
    log_info "Building frontend image..."
    docker build -f Dockerfile.frontend -t "todo-frontend:local" . || {
        log_error "Frontend Docker build failed!"
        exit 1
    }
    log_success "Frontend image built locally"
    
    echo ""
    log_info "=== Pushing Images to GCR ==="
    log_warn "Note: If you see quota errors, the script will retry automatically with delays."
    echo ""
    
    # Authenticate docker with gcloud
    log_info "Authenticating Docker with GCP..."
    local auth_output
    auth_output=$(gcloud auth configure-docker --quiet 2>&1) || {
        log_error "Failed to authenticate Docker with GCP!"
        echo "$auth_output"
        exit 1
    }
    log_success "Docker authenticated with GCP"
    
    # Wait before any GCR operations to allow quota reset
    log_info "Waiting 30 seconds for API quota reset..."
    sleep 30
    
    # Push backend image (with exponential backoff for quota limits)
    log_info "Checking if backend image already exists in GCR..."
    if image_exists_in_gcr "$DOCKER_PROJECT/todo-backend:latest"; then
        log_warn "Backend image already exists in GCR, skipping push"
    else
        log_info "Pushing backend image..."
        local max_retries=5
        local retry_delay=120  # Start with 2 minutes for quota recovery
        local attempt=1
        
        while [ $attempt -le $max_retries ]; do
            log_info "Push attempt $attempt/$max_retries..."
            # Tag and push
            docker tag "todo-backend:local" "$DOCKER_PROJECT/todo-backend:latest"
            # Disable set -e temporarily for this block to properly capture exit code
            set +e
            push_output=$(docker push "$DOCKER_PROJECT/todo-backend:latest" 2>&1)
            exit_code=$?
            set -e
            
            if [ $exit_code -eq 0 ]; then
                break
            fi
            
            # Check if it's a quota error (match various quota-related messages)
            if echo "$push_output" | grep -qiE "quota.*exceeded|rate.?limit|throttl|429|43[0-9]"; then
                if [ $attempt -lt $max_retries ]; then
                    log_warn "Quota limit detected. Waiting ${retry_delay}s before retry..."
                    sleep $retry_delay
                    retry_delay=$((retry_delay * 2))  # Exponential backoff
                else
                    log_error "Failed to push backend image after $max_retries attempts!"
                    echo "$push_output"
                    exit 1
                fi
            else
                log_error "Failed to push backend image (non-quota error)!"
                echo "$push_output"
                exit 1
            fi
            
            attempt=$((attempt + 1))
        done
    fi
    log_success "Backend image available in GCR!"
    
    # Wait between backend and frontend pushes to avoid quota issues
    log_info "Waiting 30 seconds before frontend push..."
    sleep 30
    
    # Push frontend image (with exponential backoff for quota limits)
    log_info "Checking if frontend image already exists in GCR..."
    if image_exists_in_gcr "$DOCKER_PROJECT/todo-frontend:latest"; then
        log_warn "Frontend image already exists in GCR, skipping push"
    else
        retry_delay=120  # Reset delay for new operation
        attempt=1
        
        while [ $attempt -le $max_retries ]; do
            log_info "Push attempt $attempt/$max_retries..."
            docker tag "todo-frontend:local" "$DOCKER_PROJECT/todo-frontend:latest"
            # Disable set -e temporarily for this block to properly capture exit code
            set +e
            push_output=$(docker push "$DOCKER_PROJECT/todo-frontend:latest" 2>&1)
            exit_code=$?
            set -e
            
            if [ $exit_code -eq 0 ]; then
                break
            fi
            
            # Check if it's a quota error (match various quota-related messages)
            if echo "$push_output" | grep -qiE "quota.*exceeded|rate.?limit|throttl|429|43[0-9]"; then
                if [ $attempt -lt $max_retries ]; then
                    log_warn "Quota limit detected. Waiting ${retry_delay}s before retry..."
                    sleep $retry_delay
                    retry_delay=$((retry_delay * 2))  # Exponential backoff
                else
                    log_error "Failed to push frontend image after $max_retries attempts!"
                    echo "$push_output"
                    exit 1
                fi
            else
                log_error "Failed to push frontend image (non-quota error)!"
                echo "$push_output"
                exit 1
            fi
            
            attempt=$((attempt + 1))
        done
    fi
    log_success "Frontend image available in GCR!"
}

# Create GKE cluster
create_gke_cluster() {
    echo ""
    log_info "=== Creating GKE Cluster ==="
    
    # Wait before creating cluster to avoid quota issues after image push
    log_warn "Waiting 30 seconds before cluster creation (to avoid API quota limits)..."
    sleep 30
    
    # Check if cluster already exists
    if gcloud container clusters describe "$CLUSTER_NAME" --zone="$ZONE" &>/dev/null; then
        log_warn "Cluster '$CLUSTER_NAME' already exists. Using existing cluster."
    else
        # Determine machine type and node count
        local MACHINE_TYPE="e2-small"
        local NUM_NODES=3
        
        if [ "$USE_MICRO" = "y" ]; then
            MACHINE_TYPE="e2-micro"
            log_warn "Using e2-micro (cheapest option, but has limitations)"
        fi
        
        if [ "$SINGLE_NODE" = "y" ]; then
            NUM_NODES=1
            log_warn "Using single-node cluster (lower cost, no high availability)"
        fi
        
        log_info "Creating cluster with $NUM_NODES x $MACHINE_TYPE nodes..."
        log_info "This may take 5-10 minutes..."
        echo ""
        
        gcloud container clusters create "$CLUSTER_NAME" \
            --num-nodes="$NUM_NODES" \
            --zone="$ZONE" \
            --machine-type="$MACHINE_TYPE" \
            --disk-size=20GB \
            --disk-type=pd-standard \
            --enable-autorepair \
            --no-enable-basic-auth \
            --enable-ip-alias \
            --quiet || {
                log_error "Failed to create GKE cluster!"
                exit 1
        }
        
        log_success "GKE cluster created successfully!"
    fi
    
    # Get cluster credentials
    log_info "Getting cluster credentials..."
    gcloud container clusters get-credentials "$CLUSTER_NAME" --zone="$ZONE" --quiet
}

# Deploy to Kubernetes
deploy_to_kubernetes() {
    echo ""
    log_info "=== Deploying to Kubernetes ==="
    
    # Create namespace
    log_info "Creating namespace..."
    kubectl create namespace todo-app-prod --dry-run=client -o yaml | kubectl apply -f -
    
    # Use envsubst to substitute environment variables in deployment YAML
    log_info "Substituting environment variables in deployment manifests..."
    if ! command -v envsubst &> /dev/null; then
        log_error "envsubst not found. Installing gettext..."
        sudo apt-get update && sudo apt-get install -y gettext || {
            log_error "Please install gettext (provides envsubst) and try again"
            exit 1
        }
    fi
    
    # Create temporary file with substituted values
    local TEMP_DEPLOY_FILE=$(mktemp)
    export GEMINI_API_KEY PROJECT_ID
    envsubst < gcp-deployment.yaml > "$TEMP_DEPLOY_FILE" || {
        log_error "Failed to substitute environment variables!"
        rm -f "$TEMP_DEPLOY_FILE"
        exit 1
    }
    
    # Apply deployment with substituted values
    log_info "Applying Kubernetes resources..."
    kubectl apply -f "$TEMP_DEPLOY_FILE" || {
        log_error "Failed to apply Kubernetes resources!"
        rm -f "$TEMP_DEPLOY_FILE"
        exit 1
    }
    
    # Cleanup temporary file
    rm -f "$TEMP_DEPLOY_FILE"
    
    log_success "Deployment applied successfully!"
}

# Wait for deployment to be ready
wait_for_deployment() {
    echo ""
    log_info "=== Waiting for Deployment ==="
    log_info "This may take 2-3 minutes..."
    echo ""
    
    # Wait for pods to be running
    log_info "Waiting for pods to be ready..."
    kubectl wait --for=condition=ready pod -l app=todo-backend --namespace=todo-app-prod --timeout=180s || {
        log_error "Backend pod failed to become ready!"
        kubectl get pods -n todo-app-prod
        kubectl describe pod -l app=todo-backend -n todo-app-prod | tail -50
        exit 1
    }
    
    kubectl wait --for=condition=ready pod -l app=todo-frontend --namespace=todo-app-prod --timeout=180s || {
        log_error "Frontend pod failed to become ready!"
        kubectl get pods -n todo-app-prod
        exit 1
    }
    
    # Wait for external IP
    log_info "Waiting for LoadBalancer external IP..."
    local retries=0
    local max_retries=30
    EXTERNAL_IP=""
    
    while [ $retries -lt $max_retries ]; do
        EXTERNAL_IP=$(kubectl get service todo-frontend-service --namespace=todo-app-prod -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
        
        if [ -n "$EXTERNAL_IP" ] && [ "$EXTERNAL_IP" != "<pending>" ]; then
            break
        fi
        
        sleep 10
        retries=$((retries + 1))
        echo -n "."
    done
    echo ""
    
    if [ -z "$EXTERNAL_IP" ] || [ "$EXTERNAL_IP" = "<pending>" ]; then
        log_warn "External IP not yet assigned. Check with: kubectl get services -n todo-app-prod"
        EXTERNAL_IP="(pending)"
    fi
    
    echo ""
    log_success "=== Deployment Complete! ==="
    echo ""
    echo "Your application is available at:"
    echo "  http://$EXTERNAL_IP"
    echo ""
}

# Show status and cleanup instructions
show_status() {
    local EXTERNAL_IP=$1
    
    echo "=== Current Status ==="
    echo ""
    echo "Pods:"
    kubectl get pods --namespace=todo-app-prod
    echo ""
    echo "Services:"
    kubectl get services --namespace=todo-app-prod
    echo ""
    
    echo "=== Useful Commands ==="
    echo ""
    echo "View logs:"
    echo "  Backend:  kubectl logs -l app=todo-backend -n todo-app-prod"
    echo "  Frontend: kubectl logs -l app=todo-frontend -n todo-app-prod"
    echo ""
    echo "Follow logs in real-time:"
    echo "  kubectl logs -f <pod-name> -n todo-app-prod"
    echo ""
    echo "Scale deployments:"
    echo "  kubectl scale deployment todo-backend-deployment --replicas=2 -n todo-app-prod"
    echo "  kubectl scale deployment todo-frontend-deployment --replicas=2 -n todo-app-prod"
    echo ""
    echo "Delete deployment (keeps cluster):"
    echo "  kubectl delete namespace todo-app-prod"
    echo ""
    echo "Delete entire cluster (stops all charges):"
    echo "  gcloud container clusters delete $CLUSTER_NAME --zone=$ZONE --quiet"
    echo ""
    
    # Cost estimate
    echo "=== Estimated Monthly Cost ==="
    if [ "$USE_MICRO" = "y" ] && [ "$SINGLE_NODE" = "y" ]; then
        echo "  ~$5-10/month (single e2-micro node + LoadBalancer)"
    elif [ "$USE_MICRO" = "y" ]; then
        echo "  ~$15-25/month (3x e2-micro nodes + LoadBalancer)"
    elif [ "$SINGLE_NODE" = "y" ]; then
        echo "  ~$13-18/month (single e2-small node + LoadBalancer)"
    else
        echo "  ~$40-55/month (3x e2-small nodes + LoadBalancer)"
    fi
}

# Cleanup function for graceful shutdown
cleanup() {
    local exit_code=$?
    
    if [ $exit_code -ne 0 ]; then
        echo ""
        log_error "Deployment failed or was interrupted (exit code: $exit_code)"
        echo ""
        
        # Kill any background Docker processes
        log_info "Cleaning up background processes..."
        pkill -f "docker build" 2>/dev/null || true
        pkill -f "docker push" 2>/dev/null || true
        wait 2>/dev/null || true
        
        # Attempt to rollback partial Kubernetes deployment if cluster exists
        if [ -n "$CLUSTER_NAME" ] && [ -n "$ZONE" ]; then
            # Check if we can connect to the cluster
            if kubectl cluster-info --request-timeout=5s &>/dev/null; then
                log_info "Rolling back partial Kubernetes deployment..."
                kubectl delete namespace todo-app-prod --ignore-not-found=true 2>/dev/null || true
                log_success "Kubernetes resources cleaned up"
            fi
        fi
        
        echo ""
        echo "=== Cleanup Instructions ==="
        echo ""
        echo "To delete the cluster and stop all charges:"
        echo "  gcloud container clusters delete $CLUSTER_NAME --zone=$ZONE --quiet"
        echo ""
        echo "Or to keep the cluster but remove deployments:"
        echo "  kubectl delete namespace todo-app-prod"
    fi
    
    exit $exit_code
}

# Set up trap for cleanup on exit, interrupt, or termination
trap cleanup EXIT INT TERM

# Main execution
main() {
    echo "========================================"
    echo "  TODO App - GCP Deployment Script"
    echo "  Optimized for Minimum Cost"
    echo "========================================"
    echo ""
    
    # Check if already authenticated
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &>/dev/null; then
        log_info "Not authenticated with GCP. Authenticating..."
        gcloud auth login --quiet || {
            log_error "Authentication failed!"
            exit 1
        }
    fi
    
    check_prerequisites
    collect_configuration
    build_backend
    build_and_push_images
    create_gke_cluster
    deploy_to_kubernetes
    wait_for_deployment
    show_status "$EXTERNAL_IP"
}

# Run main function
main
