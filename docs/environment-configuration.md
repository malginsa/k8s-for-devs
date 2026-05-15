### Environment Configuration

The `.env` file contains PostgreSQL credentials used by docker-compose:
- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`
- Default values: postgres/postgres/postgres

Each service has configurable environment variables in `application.properties`:
- `SONGS_DB_URL`, `RESOURCES_DB_URL`: Database hostnames
- `ENABLE_EUREKA_CLIENT`: Enable/disable service registry (default: false)
- `EUREKA_URI`: Eureka server location
- `SONGS_MS_URL`, `SONGS_MS_PORT`: Songs service endpoint for Resources service
