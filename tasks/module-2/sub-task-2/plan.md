# Module 2 Sub-Task 2: Health Checks and Kubernetes Probes Implementation Plan

## Overview

This plan details how to wire end-to-end health checking through the stack:

1. Expose health-check endpoints from each Spring Boot application via Spring Boot Actuator.
2. Add `startupProbe`, `livenessProbe`, and `readinessProbe` to the microservice **`Deployment`** objects (`resources-ms`, `songs-ms`).
3. Add the same three probe types to the database **`StatefulSet`** objects (`resources-db`, `songs-db`).

### What already exists (so we don't duplicate work)

- `spring-boot-starter-actuator` is already a dependency in both `resources-service/build.gradle` and `songs-service/build.gradle` (recent commit `035f8fc adds actuator, fixes DB names`). With Spring Boot 3.1.4 the default exposes `/actuator/health` over HTTP, but **liveness / readiness probes are not exposed by default outside Kubernetes-detected environments** — they have to be enabled explicitly so they're predictable.
- `resources-ms` and `songs-ms` are `Deployment` objects (`k8s/4-resource-ms.yaml`, `k8s/5-song-ms.yaml`).
- `resources-db` and `songs-db` are already `StatefulSet` objects (`k8s/2-resource-db.yaml`, `k8s/3-song-db.yaml`) — the `kubernetes.md` docs called them "deployment and service" generically; the manifests use `kind: StatefulSet` with `volumeClaimTemplates`.
- Container ports are `8080` (resources-ms), `8081` (songs-ms), and `5432` (both DBs).

---

## Step 1: Add Health-Check Endpoints to the Applications

### Objective

Expose Spring Boot Actuator health endpoints so Kubernetes can probe them. Three logical endpoints are needed:

- `GET /actuator/health/liveness` — is the JVM alive? (Kubernetes restarts the Pod on failure)
- `GET /actuator/health/readiness` — can the Pod accept traffic right now? (Kubernetes removes it from the `Service` endpoints on failure; e.g. DB is down)
- `GET /actuator/health` — aggregate health, used as the startup probe target while the app is still booting

### 1.1 Update `resources-service/src/main/resources/application.properties`

Append:

```properties
# Spring Boot Actuator — Kubernetes health probes
management.endpoints.web.exposure.include=health,info
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

**Why each property:**
- `management.endpoints.web.exposure.include=health,info` — opens `/actuator/health` over HTTP (default exposure is restrictive). Keep `info` available; do **not** include `env` / `metrics` / `mappings` here, since this is consumed by k8s, not operators.
- `management.endpoint.health.probes.enabled=true` — turns on the `/actuator/health/liveness` and `/actuator/health/readiness` sub-endpoints unconditionally. Spring auto-enables them when it detects Kubernetes, but enabling it explicitly removes the auto-detection dependency and matches the behaviour we expect when running locally (`docker-compose`, `bootRun`).
- `management.endpoint.health.show-details=never` — the probe consumer is kube-proxy/kubelet; HTTP 200 vs 503 is all that matters. Avoid leaking DB hostnames / disk paths in the response body.
- `management.health.livenessstate.enabled=true` / `management.health.readinessstate.enabled=true` — registers the `LivenessStateHealthIndicator` / `ReadinessStateHealthIndicator` so the sub-endpoints reflect the application's `LivenessState` / `ReadinessState` (not the aggregate of every health contributor).

### 1.2 Update `songs-service/src/main/resources/application.properties`

Append the same five lines verbatim.

### 1.3 Verify locally before touching k8s

After rebuilding the images, run a quick container-local sanity check on each service:

```bash
curl -fsS http://localhost:8080/actuator/health/liveness   # → {"status":"UP"}
curl -fsS http://localhost:8080/actuator/health/readiness  # → {"status":"UP"} once DB is reachable
curl -fsS http://localhost:8080/actuator/health            # aggregate, used by startup probe
```

Same checks against `:8081` for `songs-ms`.

### 1.4 Rebuild Docker images

The k8s manifests reference `resources-image:v2` and `songs-image:v2` with `imagePullPolicy: IfNotPresent`. Bump the tag (e.g. `v3`) so Kubernetes definitely picks up the new bytecode rather than reusing a cached layer:

```bash
./gradlew :resources-service:bootBuildImage --imageName=resources-image:v3
./gradlew :songs-service:bootBuildImage     --imageName=songs-image:v3
```

(Or whatever build flow `deploy-local.bat` already uses — the important thing is to bump the tag and update the `image:` fields in `k8s/4-resource-ms.yaml` and `k8s/5-song-ms.yaml` in Step 2.)

### 1.5 Files to modify in this step

- `resources-service/src/main/resources/application.properties`
- `songs-service/src/main/resources/application.properties`

(No code changes — actuator + the new properties give us everything.)

---

## Step 2: Probes for the Microservice `Deployment` Objects

### Objective

Add `startupProbe`, `livenessProbe`, and `readinessProbe` to both microservice `Deployment` manifests so Kubernetes can:

- Hold off liveness/readiness until the JVM has finished booting (`startupProbe`).
- Restart a wedged container (`livenessProbe`).
- Stop sending traffic to a Pod that temporarily can't service requests, e.g. DB connection lost (`readinessProbe`).

### 2.1 Update `k8s/4-resource-ms.yaml`

Inside the single container spec (`spec.template.spec.containers[0]`), under the existing `ports:` / `envFrom:` / `env:` block, add:

```yaml
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            failureThreshold: 30
            periodSeconds: 5
            timeoutSeconds: 2
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 0     # startupProbe gates this
            periodSeconds: 10
            timeoutSeconds: 2
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 0
            periodSeconds: 5
            timeoutSeconds: 2
            failureThreshold: 3
```

Also bump:

```yaml
          image: resources-image:v3
```

### 2.2 Update `k8s/5-song-ms.yaml`

Identical block, but the `port:` value is `8081` (matches `containerPort: 8081`):

```yaml
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8081
            failureThreshold: 30
            periodSeconds: 5
            timeoutSeconds: 2
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            periodSeconds: 10
            timeoutSeconds: 2
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            periodSeconds: 5
            timeoutSeconds: 2
            failureThreshold: 3
```

And:

```yaml
          image: songs-image:v3
```

### 2.3 Why these specific values

- `startupProbe.failureThreshold: 30` × `periodSeconds: 5` = **150 s** to come up before kubelet gives up. Spring Boot + JPA + Hibernate on a cold JVM can easily take 30-60 s on a developer laptop; 150 s is generous without being a foot-gun.
- `livenessProbe.periodSeconds: 10`, `failureThreshold: 3` → 30 s of sustained failure before a restart. Avoids restart loops on transient pauses (GC, disk hiccups).
- `readinessProbe.periodSeconds: 5`, `failureThreshold: 3` → ~15 s to drain a failing Pod from `Service` endpoints. Faster than liveness because we want traffic shifted promptly, but the Pod is **not** killed.
- `startupProbe` deliberately hits `/actuator/health` (aggregate) so a slow DB does not let the JVM be declared "started" prematurely. Once `startupProbe` passes, the more granular liveness/readiness endpoints take over.
- `timeoutSeconds: 2` is enough for an in-cluster HTTP hit; default of `1` is too aggressive for a busy node.

### 2.4 Files to modify in this step

- `k8s/4-resource-ms.yaml` (add probes; bump image tag)
- `k8s/5-song-ms.yaml` (add probes; bump image tag)

---

## Step 3: Probes for the Database `StatefulSet` Objects

### Objective

The PostgreSQL containers (`postgres:latest`) don't expose an HTTP health endpoint. We use:

- `exec` probes invoking **`pg_isready`** for `liveness` and `readiness`. `pg_isready` is shipped inside the official `postgres` image and returns exit code `0` when the server is accepting connections.
- A **TCP-socket startup probe** on port 5432 — it succeeds the moment Postgres opens its listening socket, which is earlier than `pg_isready` succeeding (Postgres writes a small "starting" period before fully accepting connections). This gives the slower `pg_isready` checks a real grace window.

### 3.1 Update `k8s/2-resource-db.yaml`

Inside the single container spec (`spec.template.spec.containers[0]`), under the existing `volumeMounts:` block, add:

```yaml
          startupProbe:
            tcpSocket:
              port: 5432
            failureThreshold: 30
            periodSeconds: 5
            timeoutSeconds: 2
          livenessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
                - -d
                - resources_db
                - -h
                - 127.0.0.1
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
                - -d
                - resources_db
                - -h
                - 127.0.0.1
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 3
```

### 3.2 Update `k8s/3-song-db.yaml`

Identical block, but use database name `songs_db`:

```yaml
          startupProbe:
            tcpSocket:
              port: 5432
            failureThreshold: 30
            periodSeconds: 5
            timeoutSeconds: 2
          livenessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
                - -d
                - songs_db
                - -h
                - 127.0.0.1
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
                - -d
                - songs_db
                - -h
                - 127.0.0.1
            periodSeconds: 5
            timeoutSeconds: 5
            failureThreshold: 3
```

### 3.3 Why these specific values

- `pg_isready -h 127.0.0.1`: forces the check to use TCP rather than the default Unix socket path, which avoids subtle "is the socket dir mounted?" failures on the first init run before `/var/run/postgresql` exists. It also exercises the same path the application uses.
- `pg_isready -d <db>`: returns failure if the named database isn't reachable yet. During the very first boot (`/docker-entrypoint-initdb.d` run), Postgres restarts internally. The startup-probe TCP check covers that window; once it passes, `pg_isready -d` ensures the schema-bearing database is the one we expect.
- `-U postgres`: the user comes from the `db-credentials` Secret (`postgres-username: postgres`). If the username changes, this needs to be updated — see the **Optional improvement** note below.
- `timeoutSeconds: 5`: `exec` probes have process-fork overhead inside the container; 5 s is the conventional safe value.
- `startupProbe.failureThreshold: 30 × periodSeconds: 5` = 150 s to first-time init. The init script ConfigMap (`k8s/1.9.1-...`, `k8s/1.9.2-...`) runs as part of `/docker-entrypoint-initdb.d` startup — well within this budget.

### 3.4 Optional improvement (not required for the task, document only)

Hard-coding `postgres` in the `pg_isready -U` arg duplicates the `db-credentials` Secret value. A cleaner version would source it from the env var already injected from the Secret:

```yaml
              command:
                - sh
                - -c
                - 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" -h 127.0.0.1'
```

Document this as a follow-up; the simple form above is fine for the task.

### 3.5 Files to modify in this step

- `k8s/2-resource-db.yaml`
- `k8s/3-song-db.yaml`

---

## Deployment & Verification

### Apply order (unchanged from sub-task-1)

```bash
kubectl apply -f k8s/
```

### Verify the application probes

```bash
# Confirm endpoints are reachable from inside the Pod (eliminates Service / NodePort variables)
kubectl exec -n k8s-program deploy/resources-ms -- \
    wget -qO- http://localhost:8080/actuator/health/liveness
kubectl exec -n k8s-program deploy/resources-ms -- \
    wget -qO- http://localhost:8080/actuator/health/readiness

kubectl exec -n k8s-program deploy/songs-ms -- \
    wget -qO- http://localhost:8081/actuator/health/liveness
kubectl exec -n k8s-program deploy/songs-ms -- \
    wget -qO- http://localhost:8081/actuator/health/readiness
```

Expect HTTP 200 with `{"status":"UP"}`.

### Verify probe wiring

```bash
kubectl describe pod -n k8s-program -l app=resources-ms | grep -E 'Liveness|Readiness|Startup'
kubectl describe pod -n k8s-program -l app=songs-ms     | grep -E 'Liveness|Readiness|Startup'
kubectl describe pod -n k8s-program -l app=resources-db | grep -E 'Liveness|Readiness|Startup'
kubectl describe pod -n k8s-program -l app=songs-db     | grep -E 'Liveness|Readiness|Startup'
```

Each pod description should list all three probe types with the configured paths/commands.

### Negative test: readiness should drop when DB is unreachable

This validates that `readinessProbe` is wired to a real signal, not just returning `UP` blindly.

```bash
# Scale resources-db to zero
kubectl scale -n k8s-program statefulset/resources-db --replicas=0

# Wait ~30 s for Spring Boot's DB health indicator to flip
kubectl get pods -n k8s-program -l app=resources-ms -w
# Expect READY 0/1 on the resources-ms pods (still Running, not restarting)

# Restore
kubectl scale -n k8s-program statefulset/resources-db --replicas=1
# READY should return to 1/1 within ~15 s
```

If the Pod is `READY 0/1` but **not** restarting, liveness vs. readiness are separated correctly. If it restarts, the liveness probe is too aggressive — check that liveness points at `/actuator/health/liveness`, **not** `/actuator/health` (the latter goes DOWN when DB is down, which would cause unwanted restarts).

### Functional smoke test

```bash
# Resources upload — must land in resources-db
curl -X POST http://localhost:30080/resources \
    -H 'Content-Type: audio/mpeg' \
    --data-binary @test.mp3

# Songs lookup — must hit songs-ms via Feign
curl http://localhost:30081/songs/1
```

Both should still work end-to-end exactly as before.

---

## Summary of File Changes

| File | Change |
|---|---|
| `resources-service/src/main/resources/application.properties` | Add 5 actuator probe properties |
| `songs-service/src/main/resources/application.properties` | Add 5 actuator probe properties |
| `k8s/4-resource-ms.yaml` | Add `startup`/`liveness`/`readiness` probes; bump `image:` tag |
| `k8s/5-song-ms.yaml` | Add `startup`/`liveness`/`readiness` probes; bump `image:` tag |
| `k8s/2-resource-db.yaml` | Add `startup` (TCP) + `liveness`/`readiness` (`pg_isready`) probes |
| `k8s/3-song-db.yaml` | Add `startup` (TCP) + `liveness`/`readiness` (`pg_isready`) probes |

## Documentation Touch-Ups (recommended, not required)

- `docs/kubernetes.md` — note that Pods now expose probes and the URLs.
- `docs/api-endpoints.md` — list `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` as operational endpoints.
- `docs/architecture.md` — short paragraph on the probe-driven traffic gating.
