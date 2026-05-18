## Local Deployment with Rancher Desktop

### Overview
This project uses **Rancher Desktop** for local Kubernetes development. Rancher Desktop provides:
- Local Kubernetes cluster (K3s distribution)
- Docker/containerd runtime
- `kubectl` CLI
- `nerdctl` CLI (Docker-compatible CLI for containerd)
- Built-in image management

### Image Management in Rancher Desktop

Docker images built locally are automatically available to the Kubernetes cluster without needing to push to Docker Hub or explicitly load images.

**Build Process**:
1. Build Docker images using `docker build` or `nerdctl build`
2. Images are stored in the containerd namespace used by Rancher's Kubernetes
3. Deploy manifests with `imagePullPolicy: IfNotPresent` to use local images

**Example Workflow**:
```bash
# Navigate to service directory
cd songs-service

# Build JAR
../gradlew clean build

# Build Docker image
docker build -t songs-image:v3 .

# Image is automatically available to Kubernetes
kubectl apply -f ../k8s/5-song-ms.yaml
```

### Accessing Services

Rancher Desktop exposes Kubernetes services on `localhost`:

- **Resources Service**: `http://localhost:30080` (NodePort 30080)
- **Songs Service**: `http://localhost:30081` (NodePort 30081)

### Kubernetes Context

Verify you're using the Rancher Desktop context:
```bash
kubectl config current-context
# Should show: rancher-desktop
```

### Storage Considerations

The manifests use `hostPath` PersistentVolumes pointing to `/data/songs-app`. In Rancher Desktop on Windows, this path is created within the Rancher Desktop VM.

### Building and Deploying

**Complete Local Deployment**:
```bash
# 1. Build all services
./gradlew clean build

# 2. Build Docker images
cd resources-service
docker build -t resources-image:v2 .
cd ../songs-service
docker build -t songs-image:v3 .
cd ..

# 3. Deploy to Kubernetes
kubectl apply -f k8s/

# 4. Verify deployment
kubectl get all -n k8s-program
kubectl get pods -n k8s-program -w
```

### Troubleshooting

**Images not found**:
- Ensure `imagePullPolicy: IfNotPresent` in deployment manifests
- Check image exists: `docker images` or `nerdctl -n k8s.io images`
- Rancher Desktop uses the `k8s.io` namespace for Kubernetes images

**Port conflicts**:
- Ensure no other services are using NodePort 30080 or 30081
- Check Windows firewall settings if services are unreachable

**PersistentVolume issues**:
- The `/data/songs-app` directory is created within the Rancher Desktop VM
- Use `kubectl describe pv` and `kubectl describe pvc -n k8s-program` to troubleshoot binding issues
