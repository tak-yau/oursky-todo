#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

cleanup() {
  echo ""
  echo "🧹 Cleaning up..."
  docker compose -f docker-compose.test.yml down 2>/dev/null || true
}
trap cleanup EXIT

# Use system Java 17 for compilation
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

echo "🔨 Building JVM fat JAR..."
(cd backend && sbt --batch assembly)

echo ""
echo "🐳 Building Docker images..."
echo "  (Native image build takes 5-10 minutes inside Docker)"
docker compose -f docker-compose.test.yml build

echo ""
echo "🚀 Starting containers..."
docker compose -f docker-compose.test.yml up -d

echo ""
echo "⏳ Waiting for services to be healthy..."
for i in $(seq 1 90); do
  JVM_OK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/health 2>/dev/null || echo "000")
  NATIVE_OK=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/health 2>/dev/null || echo "000")

  if [ "$JVM_OK" = "200" ] && [ "$NATIVE_OK" = "200" ]; then
    echo ""
    echo "✅ Both services healthy!"
    echo ""
    echo "📊 JVM health response:"
    curl -s http://localhost:8081/health
    echo ""
    echo "📊 Native health response:"
    curl -s http://localhost:8082/health
    echo ""
    echo ""
    JVM_SIZE=$(docker images todo-app-scala-vue-todo-backend-jvm --format '{{.Size}}' 2>/dev/null)
    NATIVE_SIZE=$(docker images todo-app-scala-vue-todo-backend-native --format '{{.Size}}' 2>/dev/null)
    echo "📦 JVM image size: ${JVM_SIZE:-N/A}"
    echo "📦 Native image size: ${NATIVE_SIZE:-N/A}"
    exit 0
  fi

  echo "  Waiting... JVM=$JVM_OK Native=$NATIVE_OK ($i/90)"
  sleep 2
done

echo ""
echo "❌ Timeout waiting for services"
echo ""
echo "📋 JVM logs:"
docker compose -f docker-compose.test.yml logs todo-backend-jvm 2>/dev/null || true
echo ""
echo "📋 Native logs:"
docker compose -f docker-compose.test.yml logs todo-backend-native 2>/dev/null || true
exit 1
