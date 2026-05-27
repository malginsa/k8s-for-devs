# Implementation Plan: Rolling Update Deployment with Genre Field

## Overview
This plan details the implementation of a new `genre` field in the Song service and demonstrates Kubernetes rolling update deployment strategy. The task involves code changes, Docker image versioning, and Kubernetes deployment configuration updates.

---

## Step 1: Add Genre Field to Song Service

### 1.1 Update Domain Entity (`SongData.java`)
**File**: `songs-service/src/main/java/com/ms/intro/domain/SongData.java`

**Action**: Add a new `genre` field to the `SongData` entity class.

```java
@Column(nullable = true)
String genre;
```

**Details**:
- Add after the `year` field (line ~40)
- Use `@Column(nullable = true)` to allow existing records without this field
- The field should be of type `String`
- Lombok's `@Data` annotation will automatically generate getters/setters

### 1.2 Update DTO (`SongDto.java`)
**File**: `songs-service/src/main/java/com/ms/intro/dto/SongDto.java`

**Action**: Add the `genre` field to the DTO class.

```java
String genre;
```

**Details**:
- Add after the `year` field (line ~28)
- No validation annotations needed (field is optional)
- The `@JsonInclude(JsonInclude.Include.NON_NULL)` class annotation ensures null genres are excluded from JSON responses

### 1.3 Update MapStruct Mapper (if needed)
**File**: `songs-service/src/main/java/com/ms/intro/mapper/SongDtoToDomainMapper.java`

**Action**: Verify the mapper interface handles the new field automatically.

**Details**:
- MapStruct automatically maps fields with matching names
- No manual changes should be needed
- After building, the generated implementation will include genre mapping

### 1.4 Controller Verification
**File**: `songs-service/src/main/java/com/ms/intro/controller/SongController.java`

**Action**: No changes needed.

**Details**:
- `POST /songs` endpoint (line 38-49): Will accept `genre` in the request body and save it
- `GET /songs/{id}` endpoint (line 30-36): Will return `genre` in the response
- The controller uses DTOs and mappers, so changes propagate automatically

### 1.5 Build and Test Locally (Optional)
**Commands**:
```bash
# Build the songs-service
./gradlew :songs-service:clean :songs-service:build

# Verify build success
ls songs-service/build/libs/*.jar
```

**Verification**:
- Build should complete without errors
- Generated MapStruct implementation should include genre field
- Check: `songs-service/build/generated/sources/annotationProcessor/java/main/com/ms/intro/mapper/SongDtoToDomainMapperImpl.java`

---

## Step 2: Build and Push New Docker Image

### 2.1 Build Docker Image with New Version Tag
**File**: `songs-service/Dockerfile` (no changes needed)

**Commands**:
```bash
# Navigate to songs-service directory
cd songs-service

# Build JAR file
../gradlew clean build

# Build Docker image with new version tag
docker build -t songs-image:v4 .

# Verify image was created
docker images | grep songs-image
```

**Expected Output**:
- You should see both `songs-image:v3` (old) and `songs-image:v4` (new)

### 2.2 Tag Image for Docker Hub
**Commands**:
```bash
# Tag the image with your Docker Hub username
docker tag songs-image:v4 <your-dockerhub-username>/songs-image:v4

# Example:
# docker tag songs-image:v4 johndoe/songs-image:v4
```

**Notes**:
- Replace `<your-dockerhub-username>` with your actual Docker Hub username
- This creates an alias for the local image pointing to the remote registry

### 2.3 Push Image to Docker Hub
**Commands**:
```bash
# Login to Docker Hub (if not already logged in)
docker login

# Push the tagged image
docker push <your-dockerhub-username>/songs-image:v4

# Example:
# docker push johndoe/songs-image:v4
```

**Verification**:
- The image should be uploaded to Docker Hub
- You can verify at: `https://hub.docker.com/r/<your-dockerhub-username>/songs-image/tags`

### 2.4 Local Image Availability in Rancher Desktop

**Good news**: In Rancher Desktop, images built with `docker build` are automatically available to Kubernetes without any additional loading step.

**How it works**:
- Rancher Desktop uses containerd as the Kubernetes runtime
- Docker images are stored in the containerd namespace that Kubernetes uses
- When you build with `docker build -t songs-image:v4 .`, the image is immediately available
- The deployment manifest uses `imagePullPolicy: IfNotPresent`, so Kubernetes will use the local image

**Verification**:
```bash
# Verify image exists in Docker
docker images | grep songs-image

# Verify image exists in containerd (Kubernetes namespace)
nerdctl -n k8s.io images | grep songs-image

# Both should show songs-image:v4
```

**Note**: Unlike Minikube or kind, Rancher Desktop does **not** require an explicit image load command.

---

## Step 3: Add Rolling Update Strategy to Deployment Manifest

### 3.1 Update Songs Microservice Deployment
**File**: `k8s/5-song-ms.yaml`

**Current Image Version**: `songs-image:v3` (line 34)

**Action**: Add rolling update strategy configuration to the `Deployment` spec.

**Insert After** line 22 (after `spec:` in Deployment, before `replicas:`):

```yaml
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1  # Maximum pods that can be unavailable during update
      maxSurge: 1        # Maximum additional pods created during update
```

**Complete Deployment Spec Structure**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: songs-ms
  namespace: k8s-program
spec:
  replicas: 2
  strategy:                    # ADD THIS SECTION
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  selector:
    matchLabels:
      app: songs-ms
  template:
    # ... rest of template
```

**Details**:
- `type: RollingUpdate`: Specifies rolling update strategy (default, but explicit is better)
- `maxUnavailable: 1`: Maximum 1 pod can be unavailable during the update (out of 2 replicas)
- `maxSurge: 1`: Kubernetes can create 1 extra pod temporarily (total 3 pods during rollout)
- This ensures zero-downtime deployment with at least 1 pod always available

### 3.2 Apply Manifest with OLD Version
**Purpose**: Deploy the application with the old version (v3) and rolling update strategy in place.

**Commands**:
```bash
# Apply the updated manifest (still with v3 image)
kubectl apply -f k8s/5-song-ms.yaml

# Verify deployment status
kubectl get deployments -n k8s-program songs-ms

# Check pods
kubectl get pods -n k8s-program -l app=songs-ms

# Verify rolling update strategy is configured
kubectl describe deployment songs-ms -n k8s-program | grep -A 5 "StrategyType"
```

**Expected Output**:
- Deployment should show `2/2` ready replicas
- Both pods should be running with `songs-image:v3`
- Strategy should show `RollingUpdate` with correct maxUnavailable/maxSurge values

---

## Step 4: Update Image Version and Apply Rolling Update

### 4.1 Update Image Version in Manifest
**File**: `k8s/5-song-ms.yaml`

**Action**: Change the image version from `v3` to `v4` (or to your Docker Hub image).

**Line 34** - Change from:
```yaml
image: songs-image:v3
```

To:
```yaml
# If using local image
image: songs-image:v4

# OR if using Docker Hub
image: <your-dockerhub-username>/songs-image:v4
```

**Also update `imagePullPolicy`** (line 35):
```yaml
# For local images
imagePullPolicy: IfNotPresent

# For Docker Hub images
imagePullPolicy: Always
```

### 4.2 Apply Updated Manifest
**Commands**:
```bash
# Apply the manifest with new image version
kubectl apply -f k8s/5-song-ms.yaml

# Watch the rolling update in real-time
kubectl rollout status deployment/songs-ms -n k8s-program
```

**Expected Behavior**:
1. Kubernetes creates a new ReplicaSet with the v4 image
2. One new pod with v4 is created (maxSurge: 1) → total 3 pods temporarily
3. Once new pod is ready, one old pod (v3) is terminated
4. Process repeats for the second pod
5. Old ReplicaSet scales down to 0

### 4.3 Monitor Rolling Update Process
**Commands**:
```bash
# Watch pods during rollout (run in separate terminal)
kubectl get pods -n k8s-program -l app=songs-ms -w

# Check rollout status
kubectl rollout status deployment/songs-ms -n k8s-program

# View rollout history
kubectl rollout history deployment/songs-ms -n k8s-program

# Check deployment events
kubectl describe deployment songs-ms -n k8s-program

# Verify all pods are running new version
kubectl get pods -n k8s-program -l app=songs-ms -o jsonpath='{.items[*].spec.containers[0].image}'
```

**Expected Output**:
- Output should show: `songs-image:v4 songs-image:v4` (or your Docker Hub image path)
- Both pods should be in `Running` state
- Deployment should show `2/2` ready replicas

### 4.4 Test New Genre Field
**Commands**:
```bash
# Get the NodePort service URL (assuming local Kubernetes)
# Songs service is exposed on nodePort 30081

# Test POST with genre field
curl -X POST http://localhost:30081/songs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bohemian Rhapsody",
    "artist": "Queen",
    "album": "A Night at the Opera",
    "length": "5:55",
    "resourceId": 999,
    "year": "1975",
    "genre": "Rock"
  }'

# Expected response: {"id": <generated-id>}

# Test GET to verify genre is returned
curl http://localhost:30081/songs/999

# Expected response should include: "genre": "Rock"
```

**Alternative Test** (if you have a resource uploaded):
```bash
# First upload a resource to get a resourceId
# Then save song metadata with genre
# Retrieve and verify genre is present in response
```

---

## Step 5: Verification and Rollback Plan

### 5.1 Verify Deployment Success
**Checklist**:
- [ ] Both pods are running with the new image version (v4)
- [ ] All health probes are passing (startup, liveness, readiness)
- [ ] Service endpoints are correctly routing traffic
- [ ] POST requests accept `genre` field
- [ ] GET requests return `genre` field
- [ ] No errors in pod logs

**Commands**:
```bash
# Check all aspects
kubectl get all -n k8s-program | grep songs-ms

# Check pod logs for errors
kubectl logs -n k8s-program -l app=songs-ms --tail=50

# Test health endpoints
curl http://localhost:30081/actuator/health
curl http://localhost:30081/actuator/health/readiness
curl http://localhost:30081/actuator/health/liveness
```

### 5.2 Rollback Procedure (if needed)
If the rolling update fails or introduces issues:

```bash
# Rollback to previous version (v3)
kubectl rollout undo deployment/songs-ms -n k8s-program

# Check rollout status
kubectl rollout status deployment/songs-ms -n k8s-program

# Verify rollback completed
kubectl get pods -n k8s-program -l app=songs-ms -o jsonpath='{.items[*].spec.containers[0].image}'
```

**Expected Output after Rollback**:
- Image should be back to `songs-image:v3`
- Pods should be running the old version

### 5.3 Database Schema Consideration
**Note**: The new `genre` field is nullable, so:
- Existing records in the database won't be affected
- Old records without `genre` will return `null` or omit the field (due to `@JsonInclude`)
- No database migration is required
- Rolling updates are safe since old pods can read records created by new pods (they'll just ignore the genre column)

---

## Summary

### Files Modified:
1. `songs-service/src/main/java/com/ms/intro/domain/SongData.java` - Added `genre` field
2. `songs-service/src/main/java/com/ms/intro/dto/SongDto.java` - Added `genre` field
3. `k8s/5-song-ms.yaml` - Added rolling update strategy and updated image version

### Docker Images:
- Old version: `songs-image:v3`
- New version: `songs-image:v4` (pushed to Docker Hub)

### Kubernetes Changes:
- Rolling update strategy configured with `maxUnavailable: 1` and `maxSurge: 1`
- Zero-downtime deployment achieved
- Both replicas updated sequentially

### Testing:
- Verify genre field in POST/GET operations
- Confirm rolling update completed successfully
- Validate all health probes passing

---

## Additional Resources

### Rolling Update Strategy Documentation
- **RollingUpdate vs Recreate**: RollingUpdate gradually replaces old pods with new ones (zero downtime). Recreate terminates all old pods before creating new ones (brief downtime).
- **maxUnavailable**: Controls how many pods can be unavailable during the update (absolute number or percentage)
- **maxSurge**: Controls how many extra pods can be created temporarily (absolute number or percentage)

### Best Practices:
1. Always test the new image locally before pushing to production
2. Use specific version tags (avoid `latest` tag)
3. Monitor pod logs and events during rollout
4. Have a rollback plan ready
5. Set appropriate health probe configurations to ensure pods are truly ready before receiving traffic
6. For production: Consider using smaller maxUnavailable (e.g., 0) to ensure maximum availability

### Troubleshooting:
- **Rollout stuck**: Check pod events with `kubectl describe pod <pod-name> -n k8s-program`
- **Image pull errors**: Verify image exists in Docker Hub and credentials are correct
- **Health probe failures**: Check application logs and actuator endpoints
- **Database connection issues**: Verify ConfigMap and Secret configurations are correct
