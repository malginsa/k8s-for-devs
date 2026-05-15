## Kubernetes Deployment

The `k8s/` directory contains Kubernetes manifests numbered for deployment order:
1. `0-namespace.yaml`: Namespace definition
2. `1-songs-storage.yaml`: Persistent storage for songs database
3. `2-resource-db.yaml`: Resources database deployment and service
4. `3-song-db.yaml`: Songs database deployment and service
5. `4-resource-ms.yaml`: Resources microservice deployment
6. `5-song-ms.yaml`: Songs microservice deployment

Deploy with: `kubectl apply -f k8s/`

