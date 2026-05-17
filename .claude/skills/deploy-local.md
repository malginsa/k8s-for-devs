---
name: deploy-local
description: Deploy microservices to local Kubernetes cluster (Minikube/Docker Desktop)
tags: [deployment, kubernetes, docker]
---

# Local Kubernetes Deployment Skill

This skill automates the deployment of the Spring Boot microservices to a local Kubernetes cluster.

## What This Skill Does

1. **Pre-flight checks**: Verify cluster connectivity and required tools
2. **Build Docker images**: Rebuild service images with Maven and Docker
3. **Deploy infrastructure**: Apply k8s manifests in dependency order
4. **Wait for readiness**: Monitor pod status until all are ready
5. **Verify deployment**: Check service endpoints and logs
6. **Rollback on failure**: Automatic cleanup if deployment fails

## Usage

```bash
/deploy-local                    # Full deployment with image rebuild
/deploy-local --skip-build       # Deploy only (skip image rebuild)
/deploy-local --clean            # Delete everything first, then deploy
/deploy-local --verify-only      # Only check deployment status
```

## Instructions

### Step 1: Pre-flight Checks

Check required tools and cluster connectivity:

```bash
# Verify kubectl is installed and configured
kubectl version --client

# Verify cluster is running and accessible
kubectl cluster-info

# Check current context (should be local cluster like minikube or docker-desktop)
kubectl config current-context

# Verify namespace (create if needed)
kubectl get namespace k8s-program 2>/dev/null || echo "Namespace will be created"
```

**If any checks fail:**
- kubectl not found → Install kubectl
- Cluster unreachable → Start Minikube/Docker Desktop
- Wrong context → Switch context: `kubectl config use-context <local-context>`

### Step 2: Build Docker Images (unless --skip-build)

Build all service images with Gradle and Docker:

```bash
# Build all services with Gradle
./gradlew clean build -x test

# Build Resources Service image
docker build -t resources-image:v2 ./resources-service

# Build Songs Service image
docker build -t songs-image:v2 ./songs-service

# Build Eureka Server image
docker build -t eureka-server-image:latest ./eureka

# Verify images exist
docker images | grep -E "resources-image|songs-image|eureka-server-image"
```

**If using Minikube:** Load images into Minikube's Docker daemon:
```bash
# Check if using Minikube
if kubectl config current-context | grep -q minikube; then
    eval $(minikube docker-env)
    echo "Using Minikube Docker daemon - images available to cluster"
fi
```

**Progress Update:** Show which images were built and their sizes.

### Step 3: Clean Deployment (if --clean flag)

Remove all existing resources:

```bash
# Delete all resources in namespace
kubectl delete all --all -n k8s-program --ignore-not-found=true

# Delete PVCs (data will be lost!)
kubectl delete pvc --all -n k8s-program --ignore-not-found=true

# Delete ConfigMaps and Secrets
kubectl delete configmap --all -n k8s-program --ignore-not-found=true
kubectl delete secret --all -n k8s-program --ignore-not-found=true

# Wait for cleanup to complete
sleep 5
```

**Warning:** Ask user for confirmation before deleting PVCs as this destroys database data.

### Step 4: Deploy Kubernetes Manifests

Apply manifests in dependency order:

```bash
# 1. Namespace
kubectl apply -f k8s/0-namespace.yaml

# 2. Storage
kubectl apply -f k8s/1-songs-storage.yaml

# 3. Secrets and ConfigMaps (if they exist)
if ls k8s/1.5-secrets.yaml 2>/dev/null; then
    kubectl apply -f k8s/1.5-secrets.yaml
    kubectl apply -f k8s/1.6-resources-config.yaml 2>/dev/null || true
    kubectl apply -f k8s/1.7-songs-config.yaml 2>/dev/null || true
    kubectl apply -f k8s/1.8-database-config.yaml 2>/dev/null || true
    kubectl apply -f k8s/1.9.1-songs-db-init-config.yaml 2>/dev/null || true
    kubectl apply -f k8s/1.9.2-songs-db-init-config.yaml 2>/dev/null || true
fi

# 4. Databases
kubectl apply -f k8s/2-resource-db.yaml
kubectl apply -f k8s/3-song-db.yaml

# 5. Microservices
kubectl apply -f k8s/4-resource-ms.yaml
kubectl apply -f k8s/5-song-ms.yaml
```

**Progress Update:** Show each manifest as it's applied with `kubectl apply` output.

### Step 5: Wait for Databases to be Ready

Monitor database pods until ready:

```bash
echo "Waiting for databases to be ready..."

# Wait for resources-db (timeout 180 seconds)
kubectl wait --for=condition=ready pod -l app=resources-db -n k8s-program --timeout=180s

# Wait for songs-db (timeout 180 seconds)
kubectl wait --for=condition=ready pod -l app=songs-db -n k8s-program --timeout=180s

echo "Databases are ready"
```

**If timeout occurs:**
- Check pod status: `kubectl get pods -n k8s-program`
- Check pod logs: `kubectl logs -l app=resources-db -n k8s-program --tail=50`
- Common issues: Image pull errors, volume mount failures, init script errors

**Progress Update:** Show database pod status and initialization progress.

### Step 6: Wait for Microservices to be Ready

Monitor microservice pods until ready:

```bash
echo "Waiting for microservices to be ready..."

# Wait for resources-ms (timeout 180 seconds)
kubectl wait --for=condition=ready pod -l app=resources-ms -n k8s-program --timeout=180s

# Wait for songs-ms (timeout 180 seconds)  
kubectl wait --for=condition=ready pod -l app=songs-ms -n k8s-program --timeout=180s

echo "Microservices are ready"
```

**If timeout occurs:**
- Check pod status: `kubectl get pods -n k8s-program`
- Check logs: `kubectl logs -l app=resources-ms -n k8s-program --tail=50`
- Common issues: Database connection failures, environment variable errors, image not found

**Progress Update:** Show microservice pod status and replica counts.

### Step 7: Verify Deployment

Check that all components are running correctly:

```bash
# Get all resources in namespace
kubectl get all -n k8s-program

# Check pod status (all should be Running)
kubectl get pods -n k8s-program -o wide

# Check service endpoints
kubectl get services -n k8s-program

# Get NodePort URLs
echo "Resources Service: http://localhost:30080"
echo "Songs Service: http://localhost:30081"
```

**Verification Checks:**

1. **Pod Status:**
   - All pods should show `Running` status
   - All containers should show `Ready` (e.g., `1/1`, `2/2`)
   - No `CrashLoopBackOff`, `ImagePullBackOff`, or `Error` states

2. **Database Verification:**
```bash
# Check resources-db table
kubectl exec -it resources-db-0 -n k8s-program -- psql -U postgres -d resources_db -c "\dt"

# Check songs-db table
kubectl exec -it songs-db-0 -n k8s-program -- psql -U postgres -d songs_db -c "\dt"
```

3. **Service Health Check:**
```bash
# Test resources service endpoint
curl -f http://localhost:30080/actuator/health 2>/dev/null || echo "Resources service not responding"

# Test songs service endpoint  
curl -f http://localhost:30081/actuator/health 2>/dev/null || echo "Songs service not responding"
```

4. **Quick end-to-end tests
```bash
# Upload MP3: should return id of the resource in the format {"id":1} 
curl -X POST http://localhost:30080/resources -H "Content-Type: audio/mpeg" --data-binary @test.mp3

# Get the song with id returned in the first command:  
curl http://localhost:30081/songs/1
```


5. **Check Recent Logs:**
```bash
# Resources MS logs (last 20 lines)
echo "=== Resources MS Logs ==="
kubectl logs -l app=resources-ms -n k8s-program --tail=20

# Songs MS logs (last 20 lines)
echo "=== Songs MS Logs ==="
kubectl logs -l app=songs-ms -n k8s-program --tail=20
```

**Look for in logs:**
- ✅ "Started ResourcesApplication" or "Started SongsApplication"
- ✅ Database connection successful
- ❌ Connection refused errors
- ❌ Authentication failures
- ❌ Exception stack traces

### Step 8: Display Deployment Summary

Show final status and access information:

```bash
echo "======================================"
echo "Deployment Complete!"
echo "======================================"
echo ""
echo "Services:"
echo "  Resources MS: http://localhost:30080"
echo "  Songs MS:     http://localhost:30081"
echo ""
echo "Quick Test Commands:"
echo "  # Upload MP3:"
echo "  curl -X POST http://localhost:30080/resources -H \"Content-Type: audio/mpeg\" --data-binary @test.mp3"
echo ""
echo "  # Get the song with id 1:"
echo "  curl http://localhost:30081/songs/1"
echo ""
echo "Useful Commands:"
echo "  # Watch pods:"
echo "  kubectl get pods -n k8s-program -w"
echo ""
echo "  # View logs:"
echo "  kubectl logs -f -l app=resources-ms -n k8s-program"
echo "  kubectl logs -f -l app=songs-ms -n k8s-program"
echo ""
echo "  # Port forward (alternative access):"
echo "  kubectl port-forward svc/resources-ms 8080:8080 -n k8s-program"
echo "  kubectl port-forward svc/songs-ms 8081:8081 -n k8s-program"
echo ""
```

## Error Handling and Rollback

If any step fails, offer rollback options:

### Automatic Checks:
- If image build fails → Stop before deployment
- If database pods don't start → Show logs and stop
- If microservice pods don't start → Show logs and offer rollback

### Rollback Procedure:
```bash
# Option 1: Delete failed deployment
kubectl delete -f k8s/4-resource-ms.yaml -n k8s-program
kubectl delete -f k8s/5-song-ms.yaml -n k8s-program

# Option 2: Full cleanup
kubectl delete namespace k8s-program

# Option 3: Keep databases, redeploy services
kubectl delete deployment resources-ms songs-ms -n k8s-program
kubectl apply -f k8s/4-resource-ms.yaml
kubectl apply -f k8s/5-song-ms.yaml
```

**Ask user which rollback option they prefer before executing.**

## Troubleshooting Guide

### Common Issues:

**1. Image Not Found**
- **Symptom:** `ErrImagePull` or `ImagePullBackOff`
- **Fix:** Verify image exists locally: `docker images | grep resources-image`
- **Fix:** Check `imagePullPolicy: IfNotPresent` in deployment YAML
- **Fix (Minikube):** Use Minikube Docker daemon: `eval $(minikube docker-env)`

**2. Database Connection Failed**
- **Symptom:** Microservice logs show "Connection refused" or "Unknown host"
- **Fix:** Check database service: `kubectl get svc -n k8s-program`
- **Fix:** Verify environment variables: `kubectl describe pod <pod-name> -n k8s-program`
- **Fix:** Check database logs: `kubectl logs resources-db-0 -n k8s-program`

**3. Init Script Not Running**
- **Symptom:** Tables don't exist in database
- **Fix:** Check ConfigMap mounted: `kubectl describe pod resources-db-0 -n k8s-program | grep -A5 Mounts`
- **Fix:** Verify init script in ConfigMap: `kubectl get configmap resources-db-init-sql -n k8s-program -o yaml`
- **Fix:** Check PostgreSQL logs: `kubectl logs resources-db-0 -n k8s-program | grep init`

**4. Persistent Volume Issues**
- **Symptom:** Pod stuck in `Pending` state
- **Fix:** Check PVC status: `kubectl get pvc -n k8s-program`
- **Fix:** Check storage class: `kubectl get storageclass`
- **Fix (Minikube):** Enable storage addon: `minikube addons enable storage-provisioner`

**5. Port Already in Use**
- **Symptom:** Cannot access NodePort services
- **Fix:** Check port conflicts: `netstat -an | grep -E "30080|30081"`
- **Fix:** Kill process using port or change NodePort in service YAML

## Notes

- **Image versions:** Currently using `v2` tags. Update if versions change.
- **Persistence:** Database data persists in PVCs across pod restarts.
- **Minikube:** If using Minikube, ensure sufficient resources: `minikube start --cpus=4 --memory=8192`
- **Docker Desktop:** Ensure Kubernetes is enabled in settings.
- **Hot reload:** Code changes require rebuild and pod restart.

## Post-Deployment

After successful deployment, remind user:
- Services are accessible at NodePorts 30080 and 30081
- Check CLAUDE.md for API endpoint documentation
- Monitor logs with `kubectl logs` for issues
- Use `kubectl describe` to troubleshoot specific pods
- Database data persists in PVCs (not deleted with pods)
