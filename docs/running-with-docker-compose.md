### Running with Docker Compose

```bash
# Start all services with databases
docker-compose up --build

# Start in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f [service-name]

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```
