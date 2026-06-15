## API Endpoints

### Resources Service (port 8080)
- `POST /api/v1/file` - Upload MP3 file (multipart/form-data)
- `POST /api/v1` - Upload MP3 binary (Content-Type: audio/mpeg)
- `GET /api/v1/{id}` - Download MP3 file
- `DELETE /api/v1?ids=1,2,3` - Delete resources by IDs (max 200 comma-separated)

### Songs Service (port 8081)
- `GET /api/v1/{id}` - Get song metadata by resource ID
- `POST /api/v1` - Save song metadata (JSON)
- `DELETE /api/v1?ids=1,2,3` - Delete songs by song IDs
- `DELETE /api/v1/by-resource-id?ids=1,2,3` - Delete songs by resource IDs (used by Resources Service)

### Operational endpoints (both services)

Spring Boot Actuator exposes these on the same HTTP port as the application (8080 for Resources, 8081 for Songs). They are consumed by Kubernetes probes — response details are intentionally suppressed (`management.endpoint.health.show-details=never`), so the bodies are minimal `{"status":"UP"}` / `{"status":"DOWN"}` payloads.

- `GET /actuator/health` — aggregate health (used as the `startupProbe` target)
- `GET /actuator/health/liveness` — JVM-alive signal (used as the `livenessProbe` target; failure → Pod restart)
- `GET /actuator/health/readiness` — ready-to-serve-traffic signal (used as the `readinessProbe` target; failure → Pod removed from `Service` endpoints, **no** restart)
- `GET /actuator/info` — build/info metadata

