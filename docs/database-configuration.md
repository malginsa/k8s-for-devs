## Database Configuration

In the Kubernetes deployment both PostgreSQL instances run in-cluster as `StatefulSet`s with their own `PersistentVolumeClaim` (1 Gi each, mounted at `/var/lib/postgresql`):

- **Resources Service** → StatefulSet `resources-db`, `Service` `resources-db:5432`, database **`resources_db`**
- **Songs Service** → StatefulSet `songs-db`, `Service` `songs-db:5432`, database **`songs_db`**

Database names come from the `database-config` ConfigMap (`POSTGRES_DB_RESOURCES`, `POSTGRES_DB_SONGS`) and are mirrored into each microservice's ConfigMap as `DATABASE_NAME` so the application's JDBC URL resolves to the same name. Credentials come from the shared `db-credentials` Secret.

Schemas are seeded by the init-script ConfigMaps (`k8s/1.9.1-resources-db-init-config.yaml`, `k8s/1.9.2-songs-db-init-config.yaml`) mounted into `/docker-entrypoint-initdb.d`. These run only once per fresh PVC — if you need to re-init, delete the PVC.

The microservices still use `spring.jpa.hibernate.ddl-auto=create-drop`; over time the SQL init scripts should become the schema source of truth and Hibernate switched to `validate` (see `tasks/module-2/sub-task-1/plan.md`). Separate databases maintain service independence.

### Health probing

Each Postgres Pod declares a `tcpSocket` `startupProbe` on `5432` and `exec pg_isready -U postgres -d <db> -h 127.0.0.1` for `livenessProbe` / `readinessProbe`. See `docs/kubernetes.md` for full timing details.

