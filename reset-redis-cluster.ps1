# ============================================
# Redis Cluster reset + app containers restart
# ============================================

$PROJECT_DIR = "C:\Users\kok84\Desktop\cache-practice"
$ErrorActionPreference = "Continue"

Write-Host ">>> Changing directory to project: $PROJECT_DIR"
Set-Location $PROJECT_DIR

# 1) Stop containers
Write-Host ">>> Stopping app and redis containers..."

docker compose stop app1 app2 `
  redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5 redis-node-6 `
  redis-cluster-init 2>$null

# 2) Remove Redis data volumes (do NOT touch grafana/prometheus volumes)
Write-Host ">>> Removing Redis data volumes..."

$redisVolumes = @(
  "cache-practice_redis-node-1-data",
  "cache-practice_redis-node-2-data",
  "cache-practice_redis-node-3-data",
  "cache-practice_redis-node-4-data",
  "cache-practice_redis-node-5-data",
  "cache-practice_redis-node-6-data"
)

foreach ($v in $redisVolumes) {
    $exists = docker volume ls -q --filter name=$v
    if ($exists) {
        Write-Host "  - removing volume $v"
        docker volume rm $v | Out-Null
    } else {
        Write-Host "  - volume $v not found, skip"
    }
}

# 3) Start Redis nodes
Write-Host ">>> Starting Redis nodes..."
docker compose up -d redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5 redis-node-6

Write-Host ">>> Waiting 10 seconds for Redis nodes to be ready..."
Start-Sleep -Seconds 10

# 4) Run cluster init
Write-Host ">>> Running redis-cluster-init..."
docker compose up -d redis-cluster-init

Write-Host ">>> Waiting 10 seconds for redis-cluster-init to complete..."
Start-Sleep -Seconds 10

Write-Host ">>> Checking cluster slots (redis-node-1)..."
docker exec -it redis-node-1 redis-cli cluster slots

Write-Host ">>> You should see 3 slot ranges: 0-5460, 5461-10922, 10923-16383."

# 5) Start app containers
Write-Host ">>> Starting app1 and app2 containers..."
docker compose up -d app1 app2

Write-Host ">>> Last 20 lines of app1 logs:"
docker logs cache-app-1 --tail 20

Write-Host ">>> Last 20 lines of app2 logs:"
docker logs cache-app-2 --tail 20

Write-Host ">>> Redis cluster reset and app restart finished."
