# build-and-redeploy.ps1
$ErrorActionPreference = "Stop"

# 1. 스크립트 기준으로 프로젝트 루트로 이동
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Host ">>> Current branch will be used as-is." -ForegroundColor Cyan

# 2. Spring Boot 빌드 (테스트 스킵)
Write-Host ">>> Build Spring Boot (gradlew clean bootJar -x test)" -ForegroundColor Cyan
.\gradlew.bat clean bootJar -x test
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit 1
}

# 3. Docker에서 app1, app2만 재빌드 + 재시작
Write-Host ">>> Restart Docker containers (app1, app2) with rebuild" -ForegroundColor Cyan

# 먼저 docker compose 시도 (신규 문법)
docker compose up -d --no-deps --build app1 app2
if ($LASTEXITCODE -ne 0) {
    Write-Host "docker compose failed, trying 'docker-compose'..." -ForegroundColor Yellow
    docker-compose up -d --no-deps --build app1 app2
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Both 'docker compose' and 'docker-compose' failed."
        exit 1
    }
}

Write-Host ">>> Done. Current branch is built and deployed to app1/app2." -ForegroundColor Green
