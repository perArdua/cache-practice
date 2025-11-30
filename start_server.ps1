$ErrorActionPreference = "Stop"

Write-Host "1. 기존 컨테이너 정리" -ForegroundColor Cyan
docker compose down --remove-orphans

Write-Host "2. Docker 이미지 빌드" -ForegroundColor Cyan
docker build -t cache-practice-app:latest .

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker build failed. Stop."
    exit 1
}

Write-Host "3. docker compose로 전체 스택 기동" -ForegroundColor Cyan
docker compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose up failed. Stop."
    exit 1
}

Write-Host "4. 실행 중 컨테이너 확인" -ForegroundColor Cyan
docker ps

Write-Host "서버 및 모니터링 스택 기동 완료" -ForegroundColor Green
