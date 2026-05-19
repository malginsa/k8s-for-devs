# Module 3 Sub-Task 1: Helm Chart Implementation Plan

## Overview
This plan details the implementation of Helm charts for deploying the microservices application consisting of Resources Service and Songs Service with their PostgreSQL databases.

## Prerequisites
- Helm 3.x installed
- Rancher Desktop running with K3s
- Docker images built: `resources-image:v3`, `songs-image:v4`
- Understanding of existing k8s/ manifests structure

---

## Step 1: Create Helm Chart Structure

### 1.1 Initialize Helm Chart
Create a new Helm chart with standard directory structure:

```bash
helm create k8s-helm-chart
```

### 1.2 Clean Up Default Files
Remove unnecessary default files created by Helm:

```bash
rm -rf k8s-helm-chart/templates/tests
rm k8s-helm-chart/templates/hpa.yaml
rm k8s-helm-chart/templates/ingress.yaml
rm k8s-helm-chart/templates/serviceaccount.yaml
```

### 1.3 Prepare for Template Files
You will copy all existing k8s/*.yaml files to `k8s-helm-chart/templates/` keeping original file names.

**Important**: Keep the leading numbers in file names as they determine deployment order in Helm.

**Files to copy from k8s/ directory:**
- `0-namespace.yaml` - Namespace definition (templatize namespace)
- `1.1-songs-storage.yaml` - PersistentVolume and PersistentVolumeClaim (templatize namespace)
- `1.5-secrets.yaml` - Database credentials (templatize namespace)
- `1.6-resources-config.yaml` - Resources MS configuration (templatize namespace)
- `1.7-songs-config.yaml` - Songs MS configuration (templatize namespace)
- `1.8-database-config.yaml` - Database configuration (templatize namespace)
- `1.9.1-resources-db-init-config.yaml` - Resources DB init scripts (templatize namespace)
- `1.9.2-songs-db-init-config.yaml` - Songs DB init scripts (templatize namespace)
- `2-resource-db.yaml` - Resources PostgreSQL Deployment and Service (templatize namespace)
- `3-song-db.yaml` - Songs PostgreSQL Deployment and Service (templatize namespace)
- `4-resource-ms.yaml` - Resources Microservice Deployment and Service (templatize namespace AND replicas)
- `5-song-ms.yaml` - Songs Microservice Deployment and Service (templatize namespace AND replicas)

### 1.4 Copy and Templatize Manifests
Copy all k8s/*.yaml files to templates/ keeping original names and templatize them:

```bash
cp k8s/0-namespace.yaml k8s-helm-chart/templates/
cp k8s/1.1-songs-storage.yaml k8s-helm-chart/templates/
cp k8s/1.5-secrets.yaml k8s-helm-chart/templates/
cp k8s/1.6-resources-config.yaml k8s-helm-chart/templates/
cp k8s/1.7-songs-config.yaml k8s-helm-chart/templates/
cp k8s/1.8-database-config.yaml k8s-helm-chart/templates/
cp k8s/1.9.1-resources-db-init-config.yaml k8s-helm-chart/templates/
cp k8s/1.9.2-songs-db-init-config.yaml k8s-helm-chart/templates/
cp k8s/2-resource-db.yaml k8s-helm-chart/templates/
cp k8s/3-song-db.yaml k8s-helm-chart/templates/
cp k8s/4-resource-ms.yaml k8s-helm-chart/templates/
cp k8s/5-song-ms.yaml k8s-helm-chart/templates/
```

### 1.5 Template Namespace in ALL Files
Replace all hardcoded `namespace: k8s-program` with `{{ .Values.namespace }}` in ALL template files:

**Files to modify (namespace field):**
- `0-namespace.yaml`: metadata.name field
- `1.1-songs-storage.yaml`: metadata.namespace in both PV and PVC
- `1.5-secrets.yaml`: metadata.namespace
- `1.6-resources-config.yaml`: metadata.namespace
- `1.7-songs-config.yaml`: metadata.namespace
- `1.8-database-config.yaml`: metadata.namespace
- `1.9.1-resources-db-init-config.yaml`: metadata.namespace
- `1.9.2-songs-db-init-config.yaml`: metadata.namespace
- `2-resource-db.yaml`: metadata.namespace in both Service and Deployment
- `3-song-db.yaml`: metadata.namespace in both Service and Deployment
- `4-resource-ms.yaml`: metadata.namespace in both Service and Deployment
- `5-song-ms.yaml`: metadata.namespace in both Service and Deployment

### 1.6 Template Replica Count in Microservices Only
Replace hardcoded `replicas: 2` with `{{ .Values.replicaCount }}` in microservice deployments:

**Files to modify (replicas field):**
- `4-resource-ms.yaml`: spec.replicas field in Deployment section
- `5-song-ms.yaml`: spec.replicas field in Deployment section

**Note**: Database deployments (`2-resource-db.yaml`, `3-song-db.yaml`) keep hardcoded `replicas: 1`

**Summary of changes:**
1. ALL 12 files: Replace `namespace: k8s-program` → `{{ .Values.namespace }}`
2. ONLY 2 files (microservices): Replace `replicas: 2` → `{{ .Values.replicaCount }}`
3. All other values remain unchanged (images, ports, configs, probes, etc.)

---

## Step 2: Create values.yaml

### 2.1 Define Default Values
Create `k8s-helm-chart/values.yaml` with default configuration:

```yaml
# Default values for k8s-helm-chart

# Namespace where all resources will be deployed
namespace: k8s-program

# Number of replicas for microservices (resources-ms and songs-ms)
replicaCount: 2
```

**Note**: This is a minimal values file containing only the two configurable parameters. All other settings (images, ports, database configuration, storage, etc.) are hardcoded in the template files.

### 2.2 Update Chart.yaml
Edit `k8s-helm-chart/Chart.yaml`:

```yaml
apiVersion: v2
name: k8s-helm-chart
description: A Helm chart for Spring Boot microservices with PostgreSQL
type: application
version: 0.1.0
appVersion: "1.0"
```

---

## Step 3: Deploy with Default Values

### 3.1 Validate Chart
Check chart for syntax errors:

```bash
helm lint k8s-helm-chart
```

### 3.2 Dry Run Installation
Preview generated manifests without applying:

```bash
helm install microservices-app k8s-helm-chart --dry-run --debug
```

Review output to ensure:
- Namespace is `k8s-program`
- ReplicaCount is `2` for both microservices
- All ConfigMaps, Secrets, and Services are generated correctly

### 3.3 Install with Helm
Deploy applications using default values:

```bash
helm install microservices-app k8s-helm-chart
```

Expected output:
```
NAME: microservices-app
LAST DEPLOYED: [timestamp]
NAMESPACE: default
STATUS: deployed
REVISION: 1
```

### 3.4 Verify Deployment
Check all resources are created:

```bash
# List Helm releases
helm list

# Check namespace exists
kubectl get namespace k8s-program

# Check all resources in namespace
kubectl get all -n k8s-program

# Watch pods until all are Running
kubectl get pods -n k8s-program -w
```

Expected pods:
- `resources-ms-*` (2 replicas)
- `songs-ms-*` (2 replicas)
- `resources-db-*` (1 replica)
- `songs-db-*` (1 replica)

### 3.5 Verify Services
Check services are accessible:

```bash
# List services
kubectl get svc -n k8s-program

# Test Resources MS endpoint
curl http://localhost:30080/actuator/health

# Test Songs MS endpoint
curl http://localhost:30081/actuator/health
```

Expected response: `{"status":"UP"}`

### 3.6 Check Health Probes
Verify all health checks are passing:

```bash
# Describe resources-ms pods
kubectl describe pod -l app=resources-ms -n k8s-program | grep -A 5 "Liveness\|Readiness\|Startup"

# Describe songs-ms pods
kubectl describe pod -l app=songs-ms -n k8s-program | grep -A 5 "Liveness\|Readiness\|Startup"
```

All probes should show status `Success`.

---

## Step 4: Deploy with Custom Values

### 4.1 Create Custom Values File
Create `k8s-helm-chart/values-custom.yaml`:

```yaml
# Custom values for testing override functionality

# Deploy to a different namespace
namespace: k8s-program-custom

# Increase replica count
replicaCount: 3
```

### 4.2 Uninstall Previous Deployment
Remove the default deployment:

```bash
helm uninstall microservices-app

# Verify cleanup
kubectl get all -n k8s-program
```

### 4.3 Install with Custom Values (Method 1: Values File)
Deploy using custom values file:

```bash
helm install microservices-app-custom k8s-helm-chart --values k8s-helm-chart/values-custom.yaml
```

### 4.4 Verify Custom Values Applied
Check that custom values are used:

```bash
# Verify custom namespace was created
kubectl get namespace k8s-program-custom

# Check replica count is 3
kubectl get deployment resources-ms -n k8s-program-custom -o jsonpath='{.spec.replicas}'
kubectl get deployment songs-ms -n k8s-program-custom -o jsonpath='{.spec.replicas}'

# Count running pods (should be 3 + 3 + 2 databases = 8 total)
kubectl get pods -n k8s-program-custom
```

### 4.5 Test Services in Custom Namespace
Verify applications are working:

```bash
# Test endpoints (NodePorts remain same: 30080, 30081)
curl http://localhost:30080/actuator/health
curl http://localhost:30081/actuator/health
```

### 4.6 Cleanup Custom Deployment
Remove custom deployment:

```bash
helm uninstall microservices-app-custom

# Delete custom namespace
kubectl delete namespace k8s-program-custom
```

### 4.7 Install with Custom Values (Method 2: Command Line)
Deploy using `--set` flags:

```bash
helm install microservices-app-cli k8s-helm-chart \
  --set namespace=k8s-program-test \
  --set replicaCount=4
```

### 4.8 Verify CLI Overrides
Check CLI parameters were applied:

```bash
# Verify namespace
kubectl get namespace k8s-program-test

# Verify replica count is 4
kubectl get deployment -n k8s-program-test

# Check pod count (should be 4 + 4 + 2 = 10 total)
kubectl get pods -n k8s-program-test --no-headers | wc -l
```

### 4.9 Final Cleanup
Remove test deployment:

```bash
helm uninstall microservices-app-cli
kubectl delete namespace k8s-program-test
```

---

## Success Criteria

### Step 1 Completion
- [ ] Helm chart directory structure created
- [ ] All template files created and templatized
- [ ] Namespace and replicaCount use template variables
- [ ] Chart passes `helm lint`

### Step 2 Completion
- [ ] values.yaml contains namespace and replicaCount defaults
- [ ] Default namespace is `k8s-program`
- [ ] Default replicaCount is `2`
- [ ] Chart.yaml has appropriate metadata

### Step 3 Completion
- [ ] `helm install` succeeds with default values
- [ ] All 6 pods are Running (2+2+1+1)
- [ ] Both microservices respond to health checks on ports 30080/30081
- [ ] All startup/liveness/readiness probes are healthy
- [ ] Resources accessible via NodePort

### Step 4 Completion
- [ ] Custom namespace deployment succeeds
- [ ] Replica count override works (3 replicas)
- [ ] Services remain accessible on same NodePorts
- [ ] CLI `--set` overrides work (4 replicas)
- [ ] Clean uninstall leaves no resources

---

## Troubleshooting

### Pods Not Starting
```bash
# Check pod status
kubectl get pods -n <namespace>

# View pod logs
kubectl logs <pod-name> -n <namespace>

# Describe pod for events
kubectl describe pod <pod-name> -n <namespace>
```

### Image Pull Errors
Ensure Docker images exist locally:
```bash
docker images | grep -E "resources-image|songs-image"
```

### Health Check Failures
Check application logs for startup issues:
```bash
kubectl logs -l app=resources-ms -n <namespace> --tail=50
kubectl logs -l app=songs-ms -n <namespace> --tail=50
```

### Database Connection Issues
Verify database pods are running and services are accessible:
```bash
kubectl get pods -l app=resources-db -n <namespace>
kubectl get pods -l app=songs-db -n <namespace>
kubectl get svc -n <namespace>
```

### Helm Template Issues
Debug template rendering:
```bash
helm template microservices-app k8s-helm-chart --debug
```

---

## Key Implementation Notes

1. **Minimal Templating**: Only `namespace` and `replicaCount` are templated - all other values (images, ports, database configs, storage, probes) remain hardcoded in templates
2. **Namespace Scope**: All resources must be deployed in the same namespace for service discovery via Kubernetes DNS
3. **Replica Count**: Only applies to microservices (resources-ms, songs-ms), not databases (which remain at 1 replica each)
4. **Image Pull Policy**: Keep `IfNotPresent` to use local Docker images
5. **NodePort Consistency**: NodePorts (30080, 30081) remain constant across deployments
6. **Health Probes**: All probe configurations from original manifests must be preserved
7. **Deployment Order**: Helm handles dependency ordering automatically
8. **Storage**: PersistentVolume uses hostPath - ensure path exists on node
9. **Database Init**: ConfigMaps with SQL init scripts must be mounted correctly
10. **No Over-Engineering**: Avoid creating complex value structures for components that don't need to be configurable

---

## Additional Helm Commands Reference

```bash
# List all releases
helm list

# Get release status
helm status microservices-app

# View release history
helm history microservices-app

# Upgrade release
helm upgrade microservices-app k8s-helm-chart

# Rollback to previous version
helm rollback microservices-app

# Get values of deployed release
helm get values microservices-app

# Uninstall and purge
helm uninstall microservices-app

# Package chart
helm package k8s-helm-chart
```
