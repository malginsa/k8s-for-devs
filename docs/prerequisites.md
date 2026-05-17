### Prerequisites
- Java 17
- Gradle (wrapper included)
- Docker (for building the `resources-image:v2` and `songs-image:v2` container images consumed by the cluster with `imagePullPolicy: IfNotPresent`)
- A local Kubernetes cluster (Docker Desktop, minikube, kind, or similar) — manifests in `k8s/` target a single-node cluster and use a `hostPath` `PersistentVolume` at `/data/songs-app`
- `kubectl` CLI configured against that cluster, with permission to create the `k8s-program` namespace and the resources within it (Deployments, Services, ConfigMaps, Secrets, PV/PVC)
- PostgreSQL is **not** required on the host — the database runs in-cluster as Pods (`resources-db`, `songs-db`) seeded by the init-script ConfigMaps
