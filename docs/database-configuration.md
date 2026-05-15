## Database Configuration

- **Songs Service**: PostgreSQL on port 5432 (docker-compose) with database `songs-db`
- **Resources Service**: PostgreSQL on port 5433 (docker-compose) with database `resources-db`
- Both use `spring.jpa.hibernate.ddl-auto=create-drop` (recreates schema on startup)
- Separate databases maintain service independence

