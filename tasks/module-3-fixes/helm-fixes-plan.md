# Helm Values Restructuring - Per-Service Configuration Plan

## Problem Statement

**Current Issue**: The Helm chart uses a flat, shared configuration structure where:
- Single `replicaCount` value applies to ALL microservices
- Probe timings are hardcoded in templates
- Image names and tags are hardcoded in templates
- Resource limits (CPU/memory) are not configurable
- No per-service customization capability

**Requirement**: Restructure values.yaml to provide per-service configuration for:
1. **MS Resources**: CPU and memory limits/requests
2. **Replicas**: Independent replica count per service
3. **Probes Timings**: Configurable startup/liveness/readiness probe settings
4. **Images**: Configurable image names and tags per service

---

## Current State Analysis

### Current values.yaml
```yaml
namespace: k8s-program
replicaCount: 2  # ❌ Shared across both microservices
```

### Issues Identified

#### 1. **Shared Replica Count**
- `{{ .Values.replicaCount }}` used in both:
  - `4-resource-ms.yaml:23`
  - `5-song-ms.yaml:23`
- No way to scale resources-ms and songs-ms independently

#### 2. **Hardcoded Images**
- `4-resource-ms.yaml:34`: `image: resources-image:v5` (hardcoded)
- `5-song-ms.yaml:39`: `image: songs-image:v5` (hardcoded)
- No tag flexibility for different environments (dev/staging/prod)

#### 3. **Hardcoded Probe Timings**

**Resources MS** (`4-resource-ms.yaml`):
- Startup: `failureThreshold: 30, periodSeconds: 5, timeoutSeconds: 5`
- Liveness: `initialDelaySeconds: 10, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 6`
- Readiness: `initialDelaySeconds: 5, periodSeconds: 5, timeoutSeconds: 5, failureThreshold: 3`

**Songs MS** (`5-song-ms.yaml`):
- Startup: `failureThreshold: 30, periodSeconds: 5, timeoutSeconds: 5`
- Liveness: `initialDelaySeconds: 10, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 6`
- Readiness: `initialDelaySeconds: 10, periodSeconds: 5, timeoutSeconds: 5, failureThreshold: 3`

**Problem**: Cannot tune probes for different environments or service characteristics

#### 4. **No Resource Limits**
- No CPU/memory requests or limits defined
- Pods can consume unlimited resources
- Risk of resource starvation and OOM kills

#### 5. **Database Configuration** (Secondary Priority)
- `2-resource-db.yaml:34`: `image: postgres:latest` (hardcoded)
- `3-song-db.yaml:34`: `image: postgres:latest` (hardcoded)
- Probe timings hardcoded
- Storage size hardcoded (`storage: 1Gi`)

---

## Proposed Solution: Hierarchical Per-Service Values

### New values.yaml Structure

```yaml
# Global settings
global:
  namespace: k8s-program
  
  # Global defaults (can be overridden per service)
  defaultResources:
    requests:
      cpu: "100m"
      memory: "256Mi"
    limits:
      cpu: "500m"
      memory: "512Mi"
  
  defaultProbes:
    startup:
      failureThreshold: 30
      periodSeconds: 5
      timeoutSeconds: 5
    liveness:
      initialDelaySeconds: 10
      periodSeconds: 10
      timeoutSeconds: 5
      failureThreshold: 6
    readiness:
      initialDelaySeconds: 10
      periodSeconds: 5
      timeoutSeconds: 5
      failureThreshold: 3

# Microservices configuration
microservices:
  resourcesMs:
    enabled: true
    replicaCount: 2
    
    image:
      repository: resources-image
      tag: v5
      pullPolicy: IfNotPresent
    
    service:
      type: NodePort
      port: 8080
      targetPort: 8080
      nodePort: 30080
    
    resources:
      requests:
        cpu: "200m"
        memory: "512Mi"
      limits:
        cpu: "1000m"
        memory: "1Gi"
    
    probes:
      startup:
        enabled: true
        httpGet:
          path: /actuator/health
          port: 8080
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 5
      
      liveness:
        enabled: true
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 10
        timeoutSeconds: 5
        failureThreshold: 6
      
      readiness:
        enabled: true
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 5
        periodSeconds: 5
        timeoutSeconds: 5
        failureThreshold: 3
    
    env:
      # Additional environment variables can be added here
      # Example:
      # - name: CUSTOM_VAR
      #   value: "custom-value"
  
  songsMs:
    enabled: true
    replicaCount: 2
    
    image:
      repository: songs-image
      tag: v5
      pullPolicy: IfNotPresent
    
    service:
      type: NodePort
      port: 8081
      targetPort: 8081
      nodePort: 30081
    
    resources:
      requests:
        cpu: "200m"
        memory: "512Mi"
      limits:
        cpu: "1000m"
        memory: "1Gi"
    
    probes:
      startup:
        enabled: true
        httpGet:
          path: /actuator/health
          port: 8081
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 5
      
      liveness:
        enabled: true
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        initialDelaySeconds: 10
        periodSeconds: 10
        timeoutSeconds: 5
        failureThreshold: 6
      
      readiness:
        enabled: true
        httpGet:
          path: /actuator/health/readiness
          port: 8081
        initialDelaySeconds: 10
        periodSeconds: 5
        timeoutSeconds: 5
        failureThreshold: 3
    
    # Note: volumeMounts removed as part of PVC fix
    # volumes:
    #   persistentVolumeClaim:
    #     enabled: false  # Set to true if needed (see pvc-fixes-plan.md)

# Database configuration
databases:
  resourcesDb:
    enabled: true
    replicas: 1
    
    image:
      repository: postgres
      tag: "16-alpine"  # Use specific version instead of 'latest'
      pullPolicy: IfNotPresent
    
    service:
      type: ClusterIP
      port: 5432
    
    resources:
      requests:
        cpu: "100m"
        memory: "256Mi"
      limits:
        cpu: "500m"
        memory: "512Mi"
    
    storage:
      size: 1Gi
      storageClass: ""  # Use default storage class
      accessModes:
        - ReadWriteOnce
    
    probes:
      startup:
        tcpSocket:
          port: 5432
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 2
      
      liveness:
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
      
      readiness:
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
  
  songsDb:
    enabled: true
    replicas: 1
    
    image:
      repository: postgres
      tag: "16-alpine"
      pullPolicy: IfNotPresent
    
    service:
      type: ClusterIP
      port: 5432
    
    resources:
      requests:
        cpu: "100m"
        memory: "256Mi"
      limits:
        cpu: "500m"
        memory: "512Mi"
    
    storage:
      size: 1Gi
      storageClass: ""
      accessModes:
        - ReadWriteOnce  # Fixed from ReadWriteMany (see below)
    
    probes:
      startup:
        tcpSocket:
          port: 5432
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 2
      
      liveness:
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
      
      readiness:
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

# Backward compatibility (deprecated)
# These values are kept for backward compatibility but should not be used
namespace: k8s-program  # Use global.namespace instead
replicaCount: 2          # Use microservices.*.replicaCount instead
```

---

## Template Changes Required

### 1. Resources MS Deployment (`4-resource-ms.yaml`)

#### Current (lines 23, 34, 52-74):
```yaml
spec:
  replicas: {{ .Values.replicaCount }}
  ...
  containers:
    - name: resources-ms
      image: resources-image:v5
      imagePullPolicy: IfNotPresent
      ...
      startupProbe:
        httpGet:
          path: /actuator/health
          port: 8080
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 5
```

#### Proposed:
```yaml
{{- $ms := .Values.microservices.resourcesMs -}}
{{- if $ms.enabled }}
---
apiVersion: v1
kind: Service
metadata:
  name: resources-ms
  namespace: {{ .Values.global.namespace }}
spec:
  type: {{ $ms.service.type }}
  ports:
    - port: {{ $ms.service.port }}
      targetPort: {{ $ms.service.targetPort }}
      {{- if eq $ms.service.type "NodePort" }}
      nodePort: {{ $ms.service.nodePort }}
      {{- end }}
  selector:
    app: resources-ms
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: resources-ms
  namespace: {{ .Values.global.namespace }}
spec:
  replicas: {{ $ms.replicaCount }}
  selector:
    matchLabels:
      app: resources-ms
  template:
    metadata:
      labels:
        app: resources-ms
    spec:
      containers:
        - name: resources-ms
          image: "{{ $ms.image.repository }}:{{ $ms.image.tag }}"
          imagePullPolicy: {{ $ms.image.pullPolicy }}
          ports:
            - containerPort: {{ $ms.service.targetPort }}
          
          {{- if $ms.resources }}
          resources:
            {{- toYaml $ms.resources | nindent 12 }}
          {{- end }}
          
          envFrom:
            - configMapRef:
                name: resources-ms-config
          env:
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: postgres-username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: postgres-password
            {{- if $ms.env }}
            {{- toYaml $ms.env | nindent 12 }}
            {{- end }}
          
          {{- if $ms.probes.startup.enabled }}
          startupProbe:
            httpGet:
              path: {{ $ms.probes.startup.httpGet.path }}
              port: {{ $ms.probes.startup.httpGet.port }}
            failureThreshold: {{ $ms.probes.startup.failureThreshold }}
            periodSeconds: {{ $ms.probes.startup.periodSeconds }}
            timeoutSeconds: {{ $ms.probes.startup.timeoutSeconds }}
          {{- end }}
          
          {{- if $ms.probes.liveness.enabled }}
          livenessProbe:
            httpGet:
              path: {{ $ms.probes.liveness.httpGet.path }}
              port: {{ $ms.probes.liveness.httpGet.port }}
            initialDelaySeconds: {{ $ms.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ $ms.probes.liveness.periodSeconds }}
            timeoutSeconds: {{ $ms.probes.liveness.timeoutSeconds }}
            failureThreshold: {{ $ms.probes.liveness.failureThreshold }}
          {{- end }}
          
          {{- if $ms.probes.readiness.enabled }}
          readinessProbe:
            httpGet:
              path: {{ $ms.probes.readiness.httpGet.path }}
              port: {{ $ms.probes.readiness.httpGet.port }}
            initialDelaySeconds: {{ $ms.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ $ms.probes.readiness.periodSeconds }}
            timeoutSeconds: {{ $ms.probes.readiness.timeoutSeconds }}
            failureThreshold: {{ $ms.probes.readiness.failureThreshold }}
          {{- end }}
{{- end }}
```

### 2. Songs MS Deployment (`5-song-ms.yaml`)

Similar changes as above, but referencing `.Values.microservices.songsMs`

**Key difference**: Remove volumeMounts and volumes sections (per PVC fix plan)

### 3. Database StatefulSets (`2-resource-db.yaml`, `3-song-db.yaml`)

#### Current (lines 23, 34, 59-90):
```yaml
spec:
  replicas: 1
  ...
  containers:
    - name: postgres
      image: postgres:latest  # ❌ Avoid 'latest' tag
      ...
      startupProbe:
        tcpSocket:
          port: 5432
        failureThreshold: 30
        periodSeconds: 5
        timeoutSeconds: 2
```

#### Proposed (example for resources-db):
```yaml
{{- $db := .Values.databases.resourcesDb -}}
{{- if $db.enabled }}
---
apiVersion: v1
kind: Service
metadata:
  name: resources-db
  namespace: {{ .Values.global.namespace }}
spec:
  ports:
    - port: {{ $db.service.port }}
      targetPort: {{ $db.service.port }}
  selector:
    app: resources-db
  type: {{ $db.service.type }}
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: resources-db
  namespace: {{ .Values.global.namespace }}
spec:
  serviceName: "resources-db"
  replicas: {{ $db.replicas }}
  selector:
    matchLabels:
      app: resources-db
  template:
    metadata:
      labels:
        app: resources-db
    spec:
      containers:
        - name: postgres
          image: "{{ $db.image.repository }}:{{ $db.image.tag }}"
          imagePullPolicy: {{ $db.image.pullPolicy }}
          ports:
            - containerPort: {{ $db.service.port }}
          
          {{- if $db.resources }}
          resources:
            {{- toYaml $db.resources | nindent 12 }}
          {{- end }}
          
          env:
            - name: POSTGRES_DB
              valueFrom:
                configMapKeyRef:
                  name: database-config
                  key: POSTGRES_DB_RESOURCES
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: postgres-username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: postgres-password
          
          volumeMounts:
            - name: db-data
              mountPath: /var/lib/postgresql
            - name: init-script
              mountPath: /docker-entrypoint-initdb.d
              readOnly: true
          
          startupProbe:
            tcpSocket:
              port: {{ $db.probes.startup.tcpSocket.port }}
            failureThreshold: {{ $db.probes.startup.failureThreshold }}
            periodSeconds: {{ $db.probes.startup.periodSeconds }}
            timeoutSeconds: {{ $db.probes.startup.timeoutSeconds }}
          
          livenessProbe:
            exec:
              command:
                {{- toYaml $db.probes.liveness.exec.command | nindent 16 }}
            periodSeconds: {{ $db.probes.liveness.periodSeconds }}
            timeoutSeconds: {{ $db.probes.liveness.timeoutSeconds }}
            failureThreshold: {{ $db.probes.liveness.failureThreshold }}
          
          readinessProbe:
            exec:
              command:
                {{- toYaml $db.probes.readiness.exec.command | nindent 16 }}
            periodSeconds: {{ $db.probes.readiness.periodSeconds }}
            timeoutSeconds: {{ $db.probes.readiness.timeoutSeconds }}
            failureThreshold: {{ $db.probes.readiness.failureThreshold }}
      
      volumes:
        - name: init-script
          configMap:
            name: resources-db-init-sql
  
  volumeClaimTemplates:
    - metadata:
        name: db-data
      spec:
        accessModes:
          {{- toYaml $db.storage.accessModes | nindent 10 }}
        {{- if $db.storage.storageClass }}
        storageClassName: {{ $db.storage.storageClass }}
        {{- end }}
        resources:
          requests:
            storage: {{ $db.storage.size }}
{{- end }}
```

### 4. Namespace Template (`0-namespace.yaml`)

#### Current:
```yaml
metadata:
  name: {{ .Values.namespace }}
```

#### Proposed:
```yaml
metadata:
  name: {{ .Values.global.namespace }}
```

---

## Environment-Specific Values Files

### values-dev.yaml (Development)
```yaml
global:
  namespace: k8s-program-dev

microservices:
  resourcesMs:
    replicaCount: 1
    image:
      tag: dev-latest
    resources:
      requests:
        cpu: "50m"
        memory: "256Mi"
      limits:
        cpu: "500m"
        memory: "512Mi"
  
  songsMs:
    replicaCount: 1
    image:
      tag: dev-latest
    resources:
      requests:
        cpu: "50m"
        memory: "256Mi"
      limits:
        cpu: "500m"
        memory: "512Mi"

databases:
  resourcesDb:
    resources:
      requests:
        cpu: "50m"
        memory: "128Mi"
      limits:
        cpu: "200m"
        memory: "256Mi"
    storage:
      size: 500Mi
  
  songsDb:
    resources:
      requests:
        cpu: "50m"
        memory: "128Mi"
      limits:
        cpu: "200m"
        memory: "256Mi"
    storage:
      size: 500Mi
```

### values-prod.yaml (Production)
```yaml
global:
  namespace: k8s-program-prod

microservices:
  resourcesMs:
    replicaCount: 3
    image:
      tag: v5
    resources:
      requests:
        cpu: "500m"
        memory: "1Gi"
      limits:
        cpu: "2000m"
        memory: "2Gi"
    probes:
      startup:
        failureThreshold: 60  # More time for production startup
      liveness:
        periodSeconds: 30
      readiness:
        periodSeconds: 10
  
  songsMs:
    replicaCount: 3
    image:
      tag: v5
    resources:
      requests:
        cpu: "500m"
        memory: "1Gi"
      limits:
        cpu: "2000m"
        memory: "2Gi"
    probes:
      startup:
        failureThreshold: 60
      liveness:
        periodSeconds: 30
      readiness:
        periodSeconds: 10

databases:
  resourcesDb:
    resources:
      requests:
        cpu: "250m"
        memory: "512Mi"
      limits:
        cpu: "1000m"
        memory: "1Gi"
    storage:
      size: 10Gi
  
  songsDb:
    resources:
      requests:
        cpu: "250m"
        memory: "512Mi"
      limits:
        cpu: "1000m"
        memory: "1Gi"
    storage:
      size: 10Gi
```

### values-custom.yaml (Updated)
```yaml
# Override namespace for custom deployment
global:
  namespace: k8s-program-custom

# Scale up for testing
microservices:
  resourcesMs:
    replicaCount: 3
  songsMs:
    replicaCount: 3
```

---

## Migration Strategy

### Phase 1: Add New Values (Backward Compatible)
1. ✅ Add new hierarchical structure to `values.yaml`
2. ✅ Keep old values (`namespace`, `replicaCount`) for backward compatibility
3. ✅ Update templates to check new values first, fall back to old values
4. ✅ Test with existing deployments (should work without changes)

### Phase 2: Update Templates
1. ✅ Update all templates to use new values structure
2. ✅ Add resource limits/requests
3. ✅ Parameterize images
4. ✅ Parameterize probes
5. ✅ Test with `helm template` and `helm upgrade --dry-run`

### Phase 3: Remove Old Values (Breaking Change)
1. ✅ Remove backward compatibility values
2. ✅ Update documentation
3. ✅ Create migration guide for users

---

## Implementation Checklist

### Values Files
- [ ] Create new `values.yaml` with hierarchical structure
- [ ] Create `values-dev.yaml` for development environment
- [ ] Create `values-prod.yaml` for production environment
- [ ] Update `values-custom.yaml` to use new structure

### Templates - Microservices
- [ ] Update `4-resource-ms.yaml`:
  - [ ] Parameterize replicas
  - [ ] Parameterize image (repository + tag)
  - [ ] Add resource limits/requests
  - [ ] Parameterize all probe timings
  - [ ] Add `enabled` flag support
- [ ] Update `5-song-ms.yaml`:
  - [ ] Parameterize replicas
  - [ ] Parameterize image (repository + tag)
  - [ ] Add resource limits/requests
  - [ ] Parameterize all probe timings
  - [ ] Remove PVC volumes (per PVC fix plan)
  - [ ] Add `enabled` flag support

### Templates - Databases
- [ ] Update `2-resource-db.yaml`:
  - [ ] Parameterize image (change from `latest` to specific version)
  - [ ] Add resource limits/requests
  - [ ] Parameterize storage size
  - [ ] Parameterize probe timings
  - [ ] Add `enabled` flag support
- [ ] Update `3-song-db.yaml`:
  - [ ] Parameterize image (change from `latest` to specific version)
  - [ ] Add resource limits/requests
  - [ ] Parameterize storage size
  - [ ] Fix accessMode from `ReadWriteMany` to `ReadWriteOnce` ⚠️
  - [ ] Parameterize probe timings
  - [ ] Add `enabled` flag support

### Templates - Other
- [ ] Update `0-namespace.yaml` to use `global.namespace`
- [ ] Update all other templates using `.Values.namespace`

### Testing
- [ ] Test with default values: `helm template k8s-helm-chart`
- [ ] Test with custom values: `helm template k8s-helm-chart -f values-custom.yaml`
- [ ] Test with dev values: `helm template k8s-helm-chart -f values-dev.yaml`
- [ ] Test with prod values: `helm template k8s-helm-chart -f values-prod.yaml`
- [ ] Deploy to dev cluster: `helm upgrade --install k8s-app k8s-helm-chart -f values-dev.yaml`
- [ ] Verify all pods start correctly
- [ ] Verify resource limits are applied: `kubectl describe pod <pod-name>`
- [ ] Test scaling: `helm upgrade k8s-app k8s-helm-chart --set microservices.resourcesMs.replicaCount=3`

### Documentation
- [ ] Update `docs/kubernetes.md` with new values structure
- [ ] Create `docs/helm-values-reference.md` documenting all values
- [ ] Add examples for different environments
- [ ] Document migration path from old to new values

---

## Benefits of New Structure

### 1. **Independent Service Scaling**
```bash
# Scale only resources-ms to 5 replicas
helm upgrade k8s-app k8s-helm-chart \
  --set microservices.resourcesMs.replicaCount=5

# Scale only songs-ms to 3 replicas
helm upgrade k8s-app k8s-helm-chart \
  --set microservices.songsMs.replicaCount=3
```

### 2. **Environment-Specific Images**
```bash
# Deploy dev environment
helm install k8s-app k8s-helm-chart -f values-dev.yaml

# Deploy prod environment
helm install k8s-app k8s-helm-chart -f values-prod.yaml
```

### 3. **Resource Management**
- Prevent resource starvation
- Enable Horizontal Pod Autoscaling (HPA)
- Cluster capacity planning
- Cost optimization

### 4. **Probe Tuning**
- Faster startup in dev (lower `failureThreshold`)
- More conservative in prod (higher thresholds)
- Service-specific tuning (databases vs microservices)

### 5. **Selective Deployment**
```bash
# Deploy only resources-ms (disable songs-ms)
helm upgrade k8s-app k8s-helm-chart \
  --set microservices.songsMs.enabled=false
```

---

## Example Usage Scenarios

### Scenario 1: Dev Environment - Fast Startup, Low Resources
```bash
helm install k8s-dev k8s-helm-chart -f values-dev.yaml
```
- 1 replica each
- Minimal resources (50m CPU, 256Mi RAM)
- Fast probes (low thresholds)
- `dev-latest` tags

### Scenario 2: Prod Environment - High Availability
```bash
helm install k8s-prod k8s-helm-chart -f values-prod.yaml
```
- 3 replicas each
- Production resources (500m-2000m CPU, 1-2Gi RAM)
- Conservative probes (high thresholds)
- Specific version tags (`v5`)

### Scenario 3: Hotfix - Update Only Resources MS Image
```bash
helm upgrade k8s-prod k8s-helm-chart \
  --reuse-values \
  --set microservices.resourcesMs.image.tag=v5.1-hotfix
```

### Scenario 4: Performance Testing - Scale Up
```bash
helm upgrade k8s-test k8s-helm-chart \
  --set microservices.resourcesMs.replicaCount=10 \
  --set microservices.resourcesMs.resources.limits.cpu=2000m
```

---

## Validation Commands

After implementing changes, use these commands to validate:

```bash
# 1. Lint the chart
helm lint k8s-helm-chart

# 2. Check rendered templates (default values)
helm template k8s-helm-chart --debug

# 3. Check rendered templates (custom values)
helm template k8s-helm-chart -f values-custom.yaml --debug

# 4. Dry-run install
helm install k8s-test k8s-helm-chart --dry-run --debug

# 5. Dry-run upgrade (if already deployed)
helm upgrade k8s-app k8s-helm-chart --dry-run --debug

# 6. Verify resource limits are rendered
helm template k8s-helm-chart | grep -A 5 "resources:"

# 7. Verify image tags are parameterized
helm template k8s-helm-chart | grep "image:"

# 8. Check specific service config
helm template k8s-helm-chart -s templates/4-resource-ms.yaml

# 9. After deployment, verify running config
kubectl get pods -n k8s-program -o yaml | grep -A 5 "resources:"
kubectl describe deployment resources-ms -n k8s-program
kubectl describe deployment songs-ms -n k8s-program
```

---

## Next Steps

1. **Review and approve** this plan
2. **Implement Phase 1**: Create new values structure
3. **Implement Phase 2**: Update templates
4. **Test thoroughly**: Use all validation commands
5. **Update documentation**: Helm values reference guide
6. **Deploy to dev**: Test in development environment
7. **Deploy to prod**: After successful dev testing

---

## References

- Helm Best Practices: [Values Files](https://helm.sh/docs/chart_best_practices/values/)
- Kubernetes: [Resource Management](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)
- Kubernetes: [Configure Liveness, Readiness and Startup Probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
- Related: `tasks/module-3-fixes/pvc-fixes-plan.md` (PVC removal for songs-ms)
