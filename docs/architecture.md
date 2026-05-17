## Architecture

The system consists of three services:

1. **Eureka Server** (port 8761): Netflix Eureka service registry for service discovery (used only outside Kubernetes; in-cluster, service discovery is handled by Kubernetes DNS)
2. **Resources Service** (port 8080): Handles MP3 file storage and extraction of audio metadata using Apache Tika
3. **Songs Service** (port 8081): Stores and manages song metadata (artist, album, title, etc.)

### Health and traffic gating

Both microservices expose Spring Boot Actuator probe endpoints (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`). Kubernetes consumes them via `startupProbe`, `livenessProbe`, and `readinessProbe` on the `resources-ms` / `songs-ms` `Deployment`s. The PostgreSQL `StatefulSet`s use `pg_isready` `exec` probes plus a TCP-socket startup probe on port 5432. The split between liveness and readiness means a Pod that temporarily can't reach its database is removed from the `Service` endpoints (drained) but **not** restarted — preventing restart loops when the dependency, not the JVM, is at fault.

