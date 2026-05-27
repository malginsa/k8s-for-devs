### Service Communication

- Resources Service communicates with Songs Service via an OpenFeign client (`SongServiceClient`) that calls Songs Service endpoints to save and delete song metadata.
- Service discovery is delegated to **Kubernetes DNS** — there is no Eureka server in the cluster. The Feign client resolves the target Songs Service through the in-cluster `Service` DNS name.
- The target host and port are injected into `resources-ms` from the `resources-ms-config` ConfigMap (`k8s/1.6-resources-config.yaml`):
  - `SONGS_MS_URL`: `songs-ms` (matches the `Service` name in `k8s/5-song-ms.yaml`)
  - `SONGS_MS_PORT`: `8081`
- Load balancing across the Songs Service Pods (the Deployment runs 2 replicas) is handled by the Kubernetes `Service` itself — kube-proxy distributes requests to ready Pod endpoints, so no client-side load balancer (e.g. Spring Cloud LoadBalancer with Eureka) is required.
- External traffic reaches the microservices via `NodePort` Services: Resources at nodePort `30080` (container port `8080`) and Songs at nodePort `30081` (container port `8081`).
