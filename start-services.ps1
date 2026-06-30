# start-services.ps1
# A script to build common dependencies, start database containers, and run all microservices in separate windows.

Write-Host "=== Starting NextGen CMS Development Environment ===" -ForegroundColor Green

# 1. Build and install cms-common locally
Write-Host "`n[1/4] Installing cms-common..." -ForegroundColor Yellow
cd cms-common
mvn install -DskipTests
cd ..

# 2. Spin up Docker containers for databases and middleware
Write-Host "`n[2/4] Starting Docker infrastructure (databases, Redis, OpenSearch)..." -ForegroundColor Yellow
docker-compose up -d

# 3. Define all Spring Boot services (Gateway + 9 Microservices)
$services = @(
    "cms-gateway",
    "cms-iam-service",
    "cms-schema-service",
    "cms-content-service",
    "cms-workflow-service",
    "cms-media-service",
    "cms-form-service",
    "cms-notification-service",
    "cms-audit-service",
    "cms-search-service"
)

# 4. Start each Spring Boot service in a new Command Prompt window
Write-Host "`n[3/4] Starting 10 Spring Boot services in separate windows..." -ForegroundColor Yellow
foreach ($service in $services) {
    Write-Host "Starting $service..." -ForegroundColor Cyan
    Start-Process cmd -ArgumentList "/k", "title $service && cd $service && mvn spring-boot:run"
}

# 5. Start the frontend Next.js app
Write-Host "`n[4/4] Starting Next.js Admin UI..." -ForegroundColor Yellow
Start-Process cmd -ArgumentList "/k", "title cms-admin-ui && cd cms-admin-ui && npm run dev"

Write-Host "`n=== All services launched! Check the spawned terminal windows for details. ===" -ForegroundColor Green
