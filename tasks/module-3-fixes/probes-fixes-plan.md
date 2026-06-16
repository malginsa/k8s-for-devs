# Kubernetes Probe Timeout Audit & Optimization Plan

**Date:** 2026-05-28  
**Scope:** Resource and Song microservices probe configuration review

## Current Configuration Summary

### Resource Microservice (resources-ms)
**Location:** `k8s-helm-chart/templates/4-resource-ms.yaml:52-72`

| Probe Type | Endpoint | Period | Timeout | Failure Threshold | Max Wait Time |
|------------|----------|--------|---------|-------------------|---------------|
| Startup | `/actuator/health` | 5s | **2s** | 30 | 150s (2.5 min) |
| Liveness | `/actuator/health/liveness` | 10s | **2s** | 3 | N/A |
| Readiness | `/actuator/health/readiness` | 5s | **2s** | 3 | N/A |

### Song Microservice (songs-ms)
**Location:** `k8s-helm-chart/templates/5-song-ms.yaml:57-77`

| Probe Type | Endpoint | Period | Timeout | Failure Threshold | Max Wait Time |
|------------|----------|--------|---------|-------------------|---------------|
| Startup | `/actuator/health` | 5s | **2s** | 30 | 150s (2.5 min) |
| Liveness | `/actuator/health/liveness` | 10s | **2s** | 3 | N/A |
| Readiness | `/actuator/health/readiness` | 5s | **2s** | 3 | N/A |

## Issues Identified

### 1. **Too Aggressive Timeout Values**
- **Current:** 2 seconds for all HTTP probes
- **Problem:** 
  - Spring Boot actuator endpoints need time for health checks (database connectivity, disk space, custom health indicators)
  - Under load or during database query execution, 2s may be insufficient
  - Can cause false positives leading to unnecessary pod restarts

### 2. **Inconsistent with Database Probes**
- Database probes use 5s timeout for liveness/readiness
- Application probes that depend on database connectivity use only 2s
- Creates timing mismatch in dependency chain

### 3. **No Initial Delay for Liveness Probes**
- Liveness probe starts immediately after startup probe succeeds
- No grace period for application warmup, JIT compilation, cache initialization

### 4. **Spring Boot Considerations**
- Spring Boot applications typically need 10-30s for full startup
- Health endpoints may perform actual database queries
- Actuator health indicators can be slow under certain conditions

### 5. **Equal failureThreshold for Liveness and Readiness (Anti-Pattern)**
- **Current:** Both probes use failureThreshold: 3
- **Problem:**
  - Liveness triggers container restart (expensive, causes downtime)
  - Readiness only removes from service endpoints (cheap, reversible)
  - Equal thresholds mean both trigger simultaneously, defeating the purpose of having separate probes
- **Best Practice:** Liveness should have **higher** threshold than readiness
  - Allow more chances before restarting container
  - Remove from rotation quickly, but restart cautiously

### 6. **Database Dependency Not Explicit in Readiness**
- **Current State:** Spring Boot's `/actuator/health/readiness` includes database health checks by default (when Spring Data JPA is present)
- **Problem:** Configuration doesn't explicitly show database dependency in readiness
- **Solution:** Add explicit configuration to ensure database health is included in readiness probe groups
- **Required Changes:**
  - Add `management.endpoint.health.group.readiness.include=readinessState,db` to both services
  - Ensures PostgreSQL connectivity is checked before marking pod as ready
  - Makes dependency explicit and auditable

## Recommended Configuration

### Summary of All Changes

| Component | Current | Proposed | Rationale |
|-----------|---------|----------|-----------|
| **All HTTP probe timeouts** | 2s | **5s** | Match database probes, handle slow queries |
| **Liveness failureThreshold** | 3 | **6** | More tolerant than readiness before restart |
| **Readiness failureThreshold** | 3 | **3** | Keep unchanged - quick removal from service |
| **Liveness initialDelay** | 0s | **10s** | Grace period after startup for warmup |
| **Readiness initialDelay** | 0s | **5s** | Brief delay for cache/connection pool init |
| **Readiness DB check** | Implicit | **Explicit** | Add `readiness.include=readinessState,db` |

### Resource Microservice - Proposed Changes

**Spring Boot Configuration** (`resources-service/src/main/resources/application.properties`):
```properties
# Add after line 39:
management.endpoint.health.group.readiness.include=readinessState,db
```

**Kubernetes Configuration** (`k8s-helm-chart/templates/4-resource-ms.yaml`):
```yaml
startupProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  failureThreshold: 30
  periodSeconds: 5
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  # Max startup time: 30 * 5 = 150s (unchanged)

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10    # ✨ NEW: grace period after startup
  periodSeconds: 10
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  failureThreshold: 6        # ⬆️ Increased from 3 → 6 (higher than readiness)

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5     # ✨ NEW: allow warmup
  periodSeconds: 5
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  failureThreshold: 3
  successThreshold: 1        # ✨ Explicit default
```

### Song Microservice - Proposed Changes

**Spring Boot Configuration** (`songs-service/src/main/resources/application.properties`):
```properties
# Add after line 30:
management.endpoint.health.group.readiness.include=readinessState,db
```

**Kubernetes Configuration** (`k8s-helm-chart/templates/5-song-ms.yaml`):
```yaml
startupProbe:
  httpGet:
    path: /actuator/health
    port: 8081
  failureThreshold: 30
  periodSeconds: 5
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  # Max startup time: 30 * 5 = 150s (unchanged)

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 10    # ✨ NEW: grace period after startup
  periodSeconds: 10
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  failureThreshold: 6        # ⬆️ Increased from 3 → 6 (higher than readiness)

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 5     # ✨ NEW: allow warmup
  periodSeconds: 5
  timeoutSeconds: 5          # ⬆️ Increased from 2s → 5s
  failureThreshold: 3
  successThreshold: 1        # ✨ Explicit default
```

## Rationale for Changes

### 1. **Timeout: 2s → 5s**
- **Why:** Aligns with database probe timeouts (consistency in dependency chain)
- **Impact:** Reduces false-positive failures during:
  - Database connection pool exhaustion
  - Slow database queries in health checks
  - GC pauses in JVM
  - Temporary network latency
- **Tradeoff:** Adds 3s to failure detection, but prevents unnecessary restarts

### 2. **initialDelaySeconds for Liveness: 10s**
- **Why:** Gives Spring Boot time to fully initialize after startup
- **Impact:** 
  - Prevents premature liveness failures
  - Allows JIT warmup, connection pool initialization
  - Safe buffer between startup completion and health monitoring
- **Best Practice:** Liveness probes should be lenient during initialization

### 3. **initialDelaySeconds for Readiness: 5s**
- **Why:** Brief grace period for cache warming and connection pool stabilization
- **Impact:** 
  - Pod won't receive traffic immediately after startup
  - Prevents routing requests to cold application instances
  - Reduces latency spikes for initial requests

### 4. **Liveness failureThreshold: 3 → 6**
- **Why:** Liveness should be more tolerant than readiness before triggering restart
- **Impact:**
  - Readiness removes from endpoints after 3 failures (15s): quick, cheap, reversible
  - Liveness restarts container after 6 failures (60s): slow, cautious, last resort
  - 45s gap between "stop traffic" and "restart pod" for transient issues to self-heal
- **Best Practice:** Restart is expensive - give more chances before killing container
- **Calculation:** 
  - Readiness fails at: 3 * 5s = 15s of consecutive failures
  - Liveness fails at: 6 * 10s = 60s of consecutive failures

## Implementation Steps

1. **Update Spring Boot Configuration - Resource Service**
   - Edit: `resources-service/src/main/resources/application.properties`
   - Add after line 39:
   ```properties
   # Explicitly include database health in readiness probe
   management.endpoint.health.group.readiness.include=readinessState,db
   ```

2. **Update Spring Boot Configuration - Song Service**
   - Edit: `songs-service/src/main/resources/application.properties`
   - Add after line 30:
   ```properties
   # Explicitly include database health in readiness probe
   management.endpoint.health.group.readiness.include=readinessState,db
   ```

3. **Rebuild Docker Images**
   ```bash
   # Resource service
   cd resources-service
   ./gradlew bootBuildImage --imageName=resources-image:v4
   
   # Song service
   cd ../songs-service
   ./gradlew bootBuildImage --imageName=songs-image:v5
   ```

4. **Update Resource Microservice K8s Config**
   - Edit: `k8s-helm-chart/templates/4-resource-ms.yaml`
   - Update image tag: `resources-image:v4`
   - Lines: 52-72 (probe configurations)

5. **Update Song Microservice K8s Config**
   - Edit: `k8s-helm-chart/templates/5-song-ms.yaml`
   - Update image tag: `songs-image:v5`
   - Lines: 57-77 (probe configurations)

6. **Testing Strategy**
   ```bash
   # 1. Apply changes to development namespace
   helm upgrade --install app-release k8s-helm-chart \
     -f k8s-helm-chart/values.yaml \
     --namespace dev

   # 2. Monitor pod startup and probe behavior
   kubectl get pods -n k8s-program -w
   kubectl describe pod <pod-name> -n k8s-program
   
   # 3. Check probe events
   kubectl get events -n k8s-program --sort-by='.lastTimestamp' | grep -i probe
   
   # 4. Verify database dependency - Stop database and check readiness
   kubectl scale statefulset resources-db -n k8s-program --replicas=0
   # Wait 15-20s, then check - resources-ms should become NOT Ready
   kubectl get pods -n dev
   kubectl scale statefulset resources-db -n k8s-program --replicas=1
   
   # 5. Test with actuator endpoint directly
   kubectl port-forward -n dev svc/resources-ms 8080:8080
   curl http://localhost:8080/actuator/health/readiness
   # Should show: {"status":"UP","groups":["readiness"]} when DB is available
   # Should show: {"status":"DOWN"} when DB is unavailable
   
   # 6. Verify no false-positive restarts under load
   # Load test while monitoring pod stability
   ```

7. **Validation Criteria**
   - ✅ No false-positive liveness failures during normal operation
   - ✅ Pods reach Ready state within expected timeframe (< 30s after startup)
   - ✅ **Readiness probe fails when database is unavailable** (new requirement)
   - ✅ **Readiness probe succeeds when database is available** (new requirement)
   - ✅ Readiness probe successfully removes pods during rolling updates
   - ✅ Liveness probe successfully restarts genuinely failed pods
   - ✅ No increase in startup time beyond acceptable range (< 180s)
   - ✅ Database health check visible in actuator endpoint response

## Alternative Considerations

### Option 1: Keep Current Values (Not Recommended)
- **Pros:** No changes, current behavior preserved
- **Cons:** Risk of false positives, inconsistent with dependencies

### Option 2: More Aggressive (Faster Feedback)
- timeoutSeconds: 3s (instead of 5s)
- initialDelaySeconds: 5s for liveness (instead of 10s)
- **Pros:** Faster failure detection
- **Cons:** Higher risk of false positives during load/GC pauses

### Option 3: More Conservative (Maximum Stability)
- timeoutSeconds: 10s
- initialDelaySeconds: 15s for liveness
- **Pros:** Maximum resilience to transient issues
- **Cons:** Slower failure detection, delayed recovery

## Recommended: Proposed Configuration (5s timeout, 10s/5s delays)
- **Balance:** Good failure detection speed with resilience to transient issues
- **Consistency:** Matches database probe timeout values
- **Production-Ready:** Standard pattern for Spring Boot microservices

## Rollback Plan

If issues arise after deployment:
```bash
# Revert to previous Helm release
helm rollback app-release -n dev

# Or revert specific values
git checkout HEAD~1 -- k8s-helm-chart/templates/4-resource-ms.yaml
git checkout HEAD~1 -- k8s-helm-chart/templates/5-song-ms.yaml
helm upgrade --install app-release k8s-helm-chart -f k8s-helm-chart/values.yaml --namespace dev
```

## References
- [Kubernetes Probes Best Practices](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- [Spring Boot Actuator Health Endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.health)
- Database probe configuration: `k8s-helm-chart/templates/2-resource-db.yaml:59-90`, `k8s-helm-chart/templates/3-song-db.yaml:59-90`
