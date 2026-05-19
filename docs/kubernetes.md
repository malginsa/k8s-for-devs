## Kubernetes Deployment

The project supports two deployment approaches:
1. **Direct kubectl deployment** using manifests in `k8s/` directory
2. **Helm chart deployment** using the chart in `k8s-helm-chart/` directory (recommended)

### Manifest Structure

The `k8s/` directory contains Kubernetes manifests numbered for deployment order. Lower-numbered prerequisites (namespace, storage, secrets, config) are applied before the workloads that depend on them.

1. `0-namespace.yaml`: `Namespace` definition (`k8s-program`) — every other resource lives in this namespace
2. `1.1-songs-storage.yaml`: `PersistentVolume` + `PersistentVolumeClaim` for songs application storage (mounted into `songs-ms` at `/app/data`)
3. `1.5-secrets.yaml`: `Secret` `db-credentials` holding the shared PostgreSQL username/password
4. `1.6-resources-config.yaml`: `ConfigMap` `resources-ms-config` (DB host/port, ports, songs service location)
5. `1.7-songs-config.yaml`: `ConfigMap` `songs-ms-config` (DB host/port, songs service port)
6. `1.8-database-config.yaml`: `ConfigMap` `database-config` providing per-database names (`POSTGRES_DB_RESOURCES`, `POSTGRES_DB_SONGS`)
7. `1.9.1-resources-db-init-config.yaml`: `ConfigMap` with the resources-db init SQL, mounted into `/docker-entrypoint-initdb.d`
8. `1.9.2-songs-db-init-config.yaml`: `ConfigMap` with the songs-db init SQL, mounted into `/docker-entrypoint-initdb.d`
9. `2-resource-db.yaml`: Resources PostgreSQL `Deployment` + `Service`
10. `3-song-db.yaml`: Songs PostgreSQL `Deployment` + `Service`
11. `4-resource-ms.yaml`: Resources microservice `Deployment` (2 replicas) + `NodePort` `Service` on port 8080 (nodePort 30080)
12. `5-song-ms.yaml`: Songs microservice `Deployment` (2 replicas) + `NodePort` `Service` on port 8081 (nodePort 30081)

Microservice Pods load non-secret config via `envFrom: configMapRef` and pull credentials via `env: valueFrom: secretKeyRef`. Service discovery between Pods is handled by Kubernetes DNS — services reference each other by their `Service` name (`resources-db`, `songs-db`, `songs-ms`).

### Health probes

All four workloads declare `startupProbe`, `livenessProbe`, and `readinessProbe`:

- **Microservice Deployments** (`4-resource-ms.yaml`, `5-song-ms.yaml`) probe via HTTP on the app's container port (`8080` / `8081`):
  - `startupProbe` → `GET /actuator/health` (aggregate; up to ~150 s warm-up budget)
  - `livenessProbe` → `GET /actuator/health/liveness` (JVM-alive; restarts the Pod on sustained failure)
  - `readinessProbe` → `GET /actuator/health/readiness` (drains the Pod from the `Service` endpoints if its dependencies are unhealthy, without restarting it)
- **Database StatefulSets** (`2-resource-db.yaml`, `3-song-db.yaml`) probe the postgres container on port `5432`:
  - `startupProbe` → `tcpSocket` on `5432` (succeeds the moment Postgres opens its listener)
  - `livenessProbe` / `readinessProbe` → `exec pg_isready -U postgres -d <db> -h 127.0.0.1`

### Deployment Commands

#### Option 1: Helm Deployment (Recommended)

**Deploy with default values** (namespace: `k8s-program`, replicas: `2`):
```bash
helm install microservices-app k8s-helm-chart
```

**Deploy with custom values**:
```bash
# Using command-line flags
helm install microservices-app k8s-helm-chart \
  --set namespace=k8s-program-custom \
  --set replicaCount=3

# Using a custom values file
helm install microservices-app k8s-helm-chart \
  --values k8s-helm-chart/values-custom.yaml
```

**Helm chart configuration**:
- **Configurable values** (in `values.yaml`):
  - `namespace`: Target namespace for all resources (default: `k8s-program`)
  - `replicaCount`: Number of replicas for microservices only (default: `2`)
- **Hardcoded values**: Images, ports, database configs, storage, health probes

**Helm management commands**:
```bash
# List releases
helm list

# Upgrade deployment
helm upgrade microservices-app k8s-helm-chart

# Uninstall
helm uninstall microservices-app

# Get deployed values
helm get values microservices-app
```

#### Option 2: Direct kubectl Deployment

**Deploy all resources**:
```bash
kubectl apply -f k8s/
```

The `kubectl apply -f <directory>` command applies manifests in lexicographic filename order, which is why the prerequisite resources use lower numeric prefixes (`0-`, `1.1-`, `1.5-` … `1.9.2-`) before the workloads (`2-` through `5-`).

#### Verification (Both Methods)

**Verify deployment**:
```bash
# Check all resources
kubectl get all -n k8s-program

# Watch pods starting up
kubectl get pods -n k8s-program -w

# Check services
kubectl get svc -n k8s-program
```

### Rancher Desktop Configuration

This project uses **Rancher Desktop** for local Kubernetes:

- **Kubernetes Distribution**: K3s (lightweight Kubernetes)
- **Container Runtime**: containerd
- **Image Management**: Docker images built locally are automatically available to Kubernetes
- **Service Access**: NodePort services accessible on `localhost` (30080, 30081)
- **Context Name**: `rancher-desktop`

**Image Pull Policy**: Manifests use `imagePullPolicy: IfNotPresent` to prioritize local images over remote registries. This allows development without pushing to Docker Hub.

### Helm Chart Structure

The `k8s-helm-chart/` directory contains the Helm chart for deploying the application:

```
k8s-helm-chart/
├── Chart.yaml                           # Chart metadata
├── values.yaml                          # Default configuration values
└── templates/                           # Kubernetes manifest templates
    ├── 0-namespace.yaml                 # Namespace definition
    ├── 1.1-songs-storage.yaml          # PersistentVolume and PVC
    ├── 1.5-secrets.yaml                # Database credentials
    ├── 1.6-resources-config.yaml       # Resources MS ConfigMap
    ├── 1.7-songs-config.yaml           # Songs MS ConfigMap
    ├── 1.8-database-config.yaml        # Database ConfigMap
    ├── 1.9.1-resources-db-init-config.yaml  # Resources DB init SQL
    ├── 1.9.2-songs-db-init-config.yaml      # Songs DB init SQL
    ├── 2-resource-db.yaml              # Resources PostgreSQL Deployment + Service
    ├── 3-song-db.yaml                  # Songs PostgreSQL Deployment + Service
    ├── 4-resource-ms.yaml              # Resources MS Deployment + Service
    └── 5-song-ms.yaml                  # Songs MS Deployment + Service
```

**Template Variables**:
- `{{ .Values.namespace }}` - Used in ALL templates for namespace reference
- `{{ .Values.replicaCount }}` - Used ONLY in microservice deployments (resources-ms, songs-ms)

**Design Principles**:
- Minimal templating: Only namespace and replicaCount are configurable
- Leading numbers in template filenames preserve deployment order
- All other values (images, ports, probes, database configs) remain hardcoded
- Database deployments use fixed `replicas: 1` (not templated)
