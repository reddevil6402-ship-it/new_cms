#!/bin/bash
# stop-services.sh
# A script to stop all running microservices, frontend servers, and Docker containers.

echo -e "\033[0;31m=== Stopping NextGen CMS Development Environment ===\033[0m"

# 1. Kill background Java and Node processes
echo -e "\n\033[0;33m[1/2] Stopping Java and Node background processes...\033[0m"
pkill -f "spring-boot" 2>/dev/null
pkill -f "node" 2>/dev/null
killall java node 2>/dev/null

echo "Processes terminated."

# 2. Stop Docker containers
echo -e "\n\033[0;33m[2/2] Stopping Docker infrastructure...\033[0m"
docker-compose down

echo -e "\n\033[0;31m=== All services stopped successfully! ===\033[0m"
