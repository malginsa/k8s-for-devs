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
