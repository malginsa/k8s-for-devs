# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot microservices learning project demonstrating service discovery, inter-service communication, and containerization. The system manages MP3 audio resources and their metadata through two separate microservices.

## Architecture

The system consists of three services:

1. **Eureka Server** (port 8761): Netflix Eureka service registry for service discovery
2. **Resources Service** (port 8080): Handles MP3 file storage and extraction of audio metadata using Apache Tika
3. **Songs Service** (port 8081): Stores and manages song metadata (artist, album, title, etc.)

### Service Communication

- Resources Service communicates with Songs Service via OpenFeign client
- Service discovery is optional and controlled by `ENABLE_EUREKA_CLIENT` environment variable (defaults to false)
- When Eureka is disabled, services communicate via direct URLs configured through `songs.url` and `songs.port` properties
- The Feign client (`SongServiceClient`) in Resources Service calls Songs Service endpoints to save/delete song metadata

### Data Flow

1. Client uploads MP3 file to Resources Service (`POST /resources/file`)
2. Resources Service stores the binary data in its PostgreSQL database
3. Resources Service extracts metadata using Apache Tika (`SongDataParserService`)
4. Resources Service sends metadata to Songs Service via Feign client (`POST /songs`)
5. Songs Service stores metadata in its separate PostgreSQL database
6. When deleting resources, Resources Service also deletes corresponding song metadata via Feign client

## Build and Development

### Prerequisites
- Java 17
- Gradle (wrapper included)
- Docker and Docker Compose for containerized deployment
- PostgreSQL (for local development without Docker)

### Build Commands

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :songs-service:build
./gradlew :resources-service:build
./gradlew :eureka:build

# Clean build
./gradlew clean build

# Run specific service locally (requires PostgreSQL)
./gradlew :songs-service:bootRun
./gradlew :resources-service:bootRun
./gradlew :eureka:bootRun
```

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

### Environment Configuration

The `.env` file contains PostgreSQL credentials used by docker-compose:
- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`
- Default values: postgres/postgres/postgres

Each service has configurable environment variables in `application.properties`:
- `SONGS_DB_URL`, `RESOURCES_DB_URL`: Database hostnames
- `ENABLE_EUREKA_CLIENT`: Enable/disable service registry (default: false)
- `EUREKA_URI`: Eureka server location
- `SONGS_MS_URL`, `SONGS_MS_PORT`: Songs service endpoint for Resources service

## Database Configuration

- **Songs Service**: PostgreSQL on port 5432 (docker-compose) with database `songs-db`
- **Resources Service**: PostgreSQL on port 5433 (docker-compose) with database `resources-db`
- Both use `spring.jpa.hibernate.ddl-auto=create-drop` (recreates schema on startup)
- Separate databases maintain service independence

## Kubernetes Deployment

The `k8s/` directory contains Kubernetes manifests numbered for deployment order:
1. `0-namespace.yaml`: Namespace definition
2. `1-songs-storage.yaml`: Persistent storage for songs database
3. `2-resource-db.yaml`: Resources database deployment and service
4. `3-song-db.yaml`: Songs database deployment and service
5. `4-resource-ms.yaml`: Resources microservice deployment
6. `5-song-ms.yaml`: Songs microservice deployment

Deploy with: `kubectl apply -f k8s/`

## Key Technologies

- **Spring Boot 3.1.4**: Core framework
- **Spring Cloud 2022.0.4**: Microservices stack (Eureka, OpenFeign, Load Balancer)
- **Spring Data JPA**: Database access
- **PostgreSQL**: Data persistence
- **Apache Tika 2.6.0**: MP3 metadata extraction (Resources Service only)
- **Lombok**: Boilerplate reduction
- **MapStruct 1.5.5**: DTO mapping
- **Docker**: Containerization

## API Endpoints

### Resources Service (port 8080)
- `POST /resources/file` - Upload MP3 file (multipart/form-data)
- `POST /resources` - Upload MP3 binary (Content-Type: audio/mpeg)
- `GET /resources/{id}` - Download MP3 file
- `DELETE /resources?ids=1,2,3` - Delete resources by IDs (max 200 comma-separated)

### Songs Service (port 8081)
- `GET /songs/{id}` - Get song metadata by resource ID
- `POST /songs` - Save song metadata (JSON)
- `DELETE /songs?ids=1,2,3` - Delete songs by song IDs
- `DELETE /songs/by-resource-id?ids=1,2,3` - Delete songs by resource IDs (used by Resources Service)

## Common Development Patterns

- DTOs use validation annotations (`@Validated`, `@Size`)
- Exception handling via `@ExceptionHandler` in controllers
- Repository pattern with Spring Data JPA
- Feign client for synchronous inter-service calls
- MapStruct for automatic DTO-to-entity mapping
