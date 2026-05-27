### Build Commands

```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :songs-service:build
./gradlew :resources-service:build
./gradlew :eureka:build

# Clean build
./gradlew clean build

# Run specific service locally (requires PostgreSQL)
./gradlew :songs-service:bootRun
./gradlew :resources-service:bootRun
./gradlew :eureka:bootRun
```

### Helm Commands

```bash
# Validate Helm chart
helm lint k8s-helm-chart

# Preview generated manifests (dry-run)
helm install microservices-app k8s-helm-chart --dry-run --debug

# Install with default values
helm install microservices-app k8s-helm-chart

# Install with custom values
helm install microservices-app k8s-helm-chart \
  --set namespace=k8s-program-test \
  --set replicaCount=3

# List releases
helm list

# Get release status
helm status microservices-app

# Get deployed values
helm get values microservices-app

# Upgrade release
helm upgrade microservices-app k8s-helm-chart

# Rollback to previous version
helm rollback microservices-app

# Uninstall release
helm uninstall microservices-app

# Package chart
helm package k8s-helm-chart
```
