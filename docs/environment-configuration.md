### Environment Configuration

In the Kubernetes deployment, environment configuration comes from one Secret and three ConfigMaps in the `k8s-program` namespace. Pods consume them via `envFrom` (bulk) or `env.valueFrom` (per-key).

#### Secret: `db-credentials` (`k8s-helm-chart/templates/1.5-secrets.yaml`)

PostgreSQL credentials, base64-encoded. Defaults: `postgres` / `postgres`.

- `postgres-username`
- `postgres-password`

Consumers:
- `resources-db`, `songs-db` — mapped to `POSTGRES_USER` and `POSTGRES_PASSWORD`
- `resources-ms`, `songs-ms` — mapped to `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`

#### ConfigMap: `database-config` (`k8s-helm-chart/templates/1.8-database-config.yaml`)

Per-database names, consumed by the postgres deployments and mapped to `POSTGRES_DB`:

- `POSTGRES_DB_RESOURCES`: `resources_db` → used by `resources-db`
- `POSTGRES_DB_SONGS`: `songs_db` → used by `songs-db`

#### ConfigMap: `resources-ms-config` (`k8s-helm-chart/templates/1.6-resources-config.yaml`)

Loaded into `resources-ms` via `envFrom`:

- `RESOURCES_DB_URL`: `resources-db`
- `RESOURCES_DB_PORT`: `5432`
- `DATABASE_NAME`: `resources_db` — must match `database-config.POSTGRES_DB_RESOURCES`; consumed by `application.properties` as `${DATABASE_NAME}` in the JDBC URL
- `RESOURCES_MS_PORT`: `8080`
- `SONGS_MS_URL`: `songs-ms`
- `SONGS_MS_PORT`: `8081`

#### ConfigMap: `songs-ms-config` (`k8s-helm-chart/templates/1.7-songs-config.yaml`)

Loaded into `songs-ms` via `envFrom`:

- `SONGS_DB_URL`: `songs-db`
- `SONGS_DB_PORT`: `5432`
- `DATABASE_NAME`: `songs_db` — must match `database-config.POSTGRES_DB_SONGS`; consumed by `application.properties` as `${DATABASE_NAME}` in the JDBC URL
- `SONGS_MS_PORT`: `8081`

#### Database init ConfigMaps

- `k8s-helm-chart/templates/1.9.1-resources-db-init-config.yaml` and `k8s-helm-chart/templates/1.9.2-songs-db-init-config.yaml` hold SQL DDL mounted into `/docker-entrypoint-initdb.d` of their respective postgres containers. They contain schema, not environment variables.

#### Consumption pattern

- Microservice deployments (`k8s-helm-chart/templates/4-resource-ms.yaml`, `k8s-helm-chart/templates/5-song-ms.yaml`) use `envFrom: configMapRef` for non-secret values and `env: valueFrom: secretKeyRef` for credentials.
- Database deployments (`k8s-helm-chart/templates/2-resource-db.yaml`, `k8s-helm-chart/templates/3-song-db.yaml`) use individual `env: valueFrom` entries against `database-config` and `db-credentials`.
