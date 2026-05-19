### Prerequisites
- Java 17
- Gradle (wrapper included)
- **Rancher Desktop** — provides both Docker and Kubernetes locally. Configured to use the containerd runtime for Kubernetes
- Docker (bundled with Rancher Desktop) for building the `resources-image:<version>` and `songs-image:<version>` container images consumed by the cluster with `imagePullPolicy: IfNotPresent`
- A local Kubernetes cluster via **Rancher Desktop** — manifests in `k8s/` target a single-node cluster and use a `hostPath` `PersistentVolume` at `/data/songs-app`
- `kubectl` CLI (bundled with Rancher Desktop) configured against the local cluster, with permission to create the `k8s-program` namespace and the resources within it (Deployments, Services, ConfigMaps, Secrets, PV/PVC)
- **Helm 3.x** (optional but recommended) — for deploying via Helm chart in `k8s-helm-chart/` directory instead of raw manifests
- PostgreSQL is **not** required on the host — the database runs in-cluster as Pods (`resources-db`, `songs-db`) seeded by the init-script ConfigMaps
