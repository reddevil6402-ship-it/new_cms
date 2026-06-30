#!/bin/bash
# start-services.sh
# A script to build common dependencies, start database containers, and run all microservices in the background.

echo -e "\033[0;32m=== Starting NextGen CMS Development Environment ===\033[0m"

# Create logs directory if it doesn't exist
mkdir -p logs

# 1. Build and install cms-common locally
echo -e "\n\033[0;33m[1/4] Installing cms-common...\033[0m"
cd cms-common && mvn install -DskipTests && cd ..

# 2. Spin up Docker containers for databases and middleware
echo -e "\n\033[0;33m[2/4] Starting Docker infrastructure (databases, Redis, OpenSearch)...\033[0m"
docker-compose up -d

# 3. Define all Spring Boot services
services=(
  "cms-gateway"
  "cms-iam-service"
  "cms-schema-service"
  "cms-content-service"
  "cms-workflow-service"
  "cms-media-service"
  "cms-form-service"
  "cms-notification-service"
  "cms-audit-service"
  "cms-search-service"
)

# 4. Start each Spring Boot service in the background
echo -e "\n\033[0;33m[3/4] Starting 10 Spring Boot services in the background...\033[0m"
for service in "${services[@]}"; do
  echo -e "Starting \033[0;36m$service\033[0m (logging to logs/$service.log)..."
  cd "$service" && mvn spring-boot:run > "../logs/$service.log" 2>&1 &
  cd ..
done

# 5. Start the frontend Next.js app
echo -e "\n\033[0;33m[4/4] Starting Next.js Admin UI in the background...\033[0m"
cd cms-admin-ui && npm run dev > ../logs/cms-admin-ui.log 2>&1 &
cd ..

echo -e "\n\033[0;32m=== All services launched in the background! ===\033[0m"
echo "Logs are available under the 'logs/' folder. You can tail them using:"
echo "  tail -f logs/<service-name>.log"
echo "To stop all background Java/Node processes, you can run:"
echo "  killall java node"
