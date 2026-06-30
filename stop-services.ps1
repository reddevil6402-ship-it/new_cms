# stop-services.ps1
# A script to stop all running microservices (Java), frontend dev servers (Node), and Docker containers.

Write-Host "=== Stopping NextGen CMS Development Environment ===" -ForegroundColor Red

# 1. Stop all Java (Spring Boot) processes
Write-Host "`n[1/3] Stopping Java processes (Spring Boot microservices)..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped $($javaProcesses.Count) Java process(es)." -ForegroundColor Green
} else {
    Write-Host "No running Java processes found." -ForegroundColor DarkGray
}

# 2. Stop Node.js processes (Next.js Frontend)
Write-Host "`n[2/3] Stopping Node.js processes (Frontend dev servers)..." -ForegroundColor Yellow
$nodeProcesses = Get-Process -Name "node" -ErrorAction SilentlyContinue
if ($nodeProcesses) {
    Stop-Process -Name "node" -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped $($nodeProcesses.Count) Node.js process(es)." -ForegroundColor Green
} else {
    Write-Host "No running Node.js processes found." -ForegroundColor DarkGray
}

# 3. Stop Docker containers
Write-Host "`n[3/3] Stopping Docker infrastructure..." -ForegroundColor Yellow
docker-compose down

Write-Host "`n=== All services stopped successfully! ===" -ForegroundColor Red
