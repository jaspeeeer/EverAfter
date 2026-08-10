#!/usr/bin/env bash
#
# End-to-end test runner.
#
# Spins up an EPHEMERAL Postgres (so E2E never touches Supabase), starts the Spring backend
# against it, then runs Playwright (which starts/reuses the Next dev server). Everything is torn
# down on exit. Pass extra Playwright args through, e.g. `scripts/e2e.sh --headed`.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PG_CONTAINER="wp-e2e-pg"
PG_PORT=5433
BACKEND_LOG="/tmp/wp-e2e-backend.log"
BACKEND_PID=""

cleanup() {
  echo "==> Cleaning up"
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" >/dev/null 2>&1 || true
  pkill -f "wedding-planner-backend-0.0.1-SNAPSHOT.jar" >/dev/null 2>&1 || true
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Freeing port 8080 (any stale backend)"
pkill -f "wedding-planner-backend-0.0.1-SNAPSHOT.jar" >/dev/null 2>&1 || true

echo "==> Starting ephemeral Postgres on :$PG_PORT"
docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$PG_CONTAINER" \
  -e POSTGRES_DB=wedding -e POSTGRES_USER=wedding -e POSTGRES_PASSWORD=wedding \
  -p "$PG_PORT:5432" postgres:16-alpine >/dev/null
for i in $(seq 1 30); do
  docker exec "$PG_CONTAINER" pg_isready -U wedding >/dev/null 2>&1 && break
  sleep 1
done

echo "==> Packaging backend (skip tests)"
cd "$ROOT/backend"
./mvnw -q -DskipTests package

echo "==> Starting backend against the ephemeral DB"
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$PG_PORT/wedding" \
SPRING_DATASOURCE_USERNAME="wedding" \
SPRING_DATASOURCE_PASSWORD="wedding" \
APP_JWT_SECRET="e2e-only-signing-secret-at-least-32-bytes-long-000000" \
APP_RATE_LIMIT_ENABLED="false" \
  java -jar target/wedding-planner-backend-0.0.1-SNAPSHOT.jar >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

for i in $(seq 1 60); do
  grep -q "Started WeddingPlannerApplication" "$BACKEND_LOG" && break
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Backend failed to start:"; tail -20 "$BACKEND_LOG"; exit 1
  fi
  sleep 1
done
echo "    backend up (pid $BACKEND_PID)"

echo "==> Running Playwright"
cd "$ROOT/frontend"
export API_BASE_URL="http://localhost:8080"
npx playwright test "$@"
