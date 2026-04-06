#!/bin/bash
# Build script for GraalVM Native Image
# Requires GraalVM to be installed with native-image component
# Or uses sbt-native-image to auto-download via Coursier

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building GraalVM Native Image via sbt..."
echo ""
echo "This will:"
echo "  1. Download GraalVM 21.0.2 if not present"
echo "  2. Compile Scala code"
echo "  3. Run native-image AOT compilation (~3-10 min)"
echo ""

cd "$SCRIPT_DIR/.."
sbt native/nativeImage

echo ""
echo "✅ Build complete!"
echo "  Native binary: backend/native/target/todo-backend-native"
echo ""

BINARY="$SCRIPT_DIR/native/target/todo-backend-native"
if [ -f "$BINARY" ]; then
  echo "📊 Binary size: $(du -h "$BINARY" | cut -f1)"
  echo ""
  echo "⚡ Startup time test:"
  timeout 5 "$BINARY" &
  PID=$!
  sleep 3
  kill $PID 2>/dev/null || true
  wait $PID 2>/dev/null || true
fi
