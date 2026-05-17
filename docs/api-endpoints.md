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

### Operational endpoints (both services)

Spring Boot Actuator exposes these on the same HTTP port as the application (8080 for Resources, 8081 for Songs). They are consumed by Kubernetes probes — response details are intentionally suppressed (`management.endpoint.health.show-details=never`), so the bodies are minimal `{"status":"UP"}` / `{"status":"DOWN"}` payloads.

- `GET /actuator/health` — aggregate health (used as the `startupProbe` target)
- `GET /actuator/health/liveness` — JVM-alive signal (used as the `livenessProbe` target; failure → Pod restart)
- `GET /actuator/health/readiness` — ready-to-serve-traffic signal (used as the `readinessProbe` target; failure → Pod removed from `Service` endpoints, **no** restart)
- `GET /actuator/info` — build/info metadata

