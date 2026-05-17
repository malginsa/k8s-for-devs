## Kubernetes Deployment

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

Deploy with: `kubectl apply -f k8s/`

The `kubectl apply -f <directory>` command applies manifests in lexicographic filename order, which is why the prerequisite resources use lower numeric prefixes (`0-`, `1.1-`, `1.5-` … `1.9.2-`) before the workloads (`2-` through `5-`).
