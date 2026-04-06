#!/bin/bash
# Startup script for Oursky Todo Backend
# Uses sbt run to start the server (no assembly issues)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting backend..."
echo "   Run from source (recommended for dev)"
echo "   Or use: sbt 'set showSuccess := false' run"
echo "   Or use: sbt 'Universal / stage' and run the binary directly"
echo ""

# Run with sbt
exec sbt -batch run