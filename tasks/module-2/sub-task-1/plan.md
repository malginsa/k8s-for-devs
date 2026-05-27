# Module 2 Sub-Task 1: Kubernetes Configuration Implementation Plan

## Overview
This plan details the implementation steps for configuring Kubernetes Secrets, ConfigMaps, and SQL initialization scripts for the microservices deployment. The goal is to externalize configuration and credentials from the deployment manifests.

---

## Step 1: Add Secrets Object for Database Credentials

### Objective
Create a Kubernetes Secret to securely store database usernames and passwords instead of hardcoding them in StatefulSet and Deployment manifests.

### Implementation Details

#### 1.1 Create Secret Manifest File
**File:** `k8s/1.5-secrets.yaml` (numbered to deploy after namespace, before databases)

**Content Structure:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
  namespace: k8s-program
type: Opaque
data:
  # Base64 encoded values
  postgres-username: <base64-encoded-username>
  postgres-password: <base64-encoded-password>
```

**Actions:**
- Encode credentials using: `echo -n 'postgres' | base64` and `echo -n 'password' | base64`
- Create single Secret object for both databases (they share credentials)
- Use descriptive key names: `postgres-username` and `postgres-password`

#### 1.2 Security Considerations
- **DO NOT** commit plain-text passwords to version control
- Consider using `stringData` for development (automatically encodes)
- Document the encoding method in comments
- In production, use external secret management (e.g., Sealed Secrets, External Secrets Operator)

---

## Step 2: Add ConfigMaps for Environment Variables

### Objective
Store non-sensitive application configuration in ConfigMaps for easier management and reusability across deployments.

### Implementation Details

#### 2.1 Create ConfigMap for Resources Microservice
**File:** `k8s/1.6-resources-config.yaml`

**Content Structure:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resources-ms-config
  namespace: k8s-program
data:
  RESOURCES_DB_URL: "resources-db"
  RESOURCES_DB_PORT: "5432"
  SONGS_MS_URL: "songs-ms"
  SONGS_MS_PORT: "8081"
  RESOURCES_MS_PORT: "8080"
```

**Actions:**
- Extract all non-sensitive environment variables from `4-resource-ms.yaml`
- Use consistent naming with current environment variables
- Group by service for maintainability

#### 2.2 Create ConfigMap for Songs Microservice
**File:** `k8s/1.7-songs-config.yaml`

**Content Structure:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: songs-ms-config
  namespace: k8s-program
data:
  SONGS_DB_URL: "songs-db"
  SONGS_DB_PORT: "5432"
  SONGS_MS_PORT: "8081"
```

**Actions:**
- Extract all non-sensitive environment variables from `5-song-ms.yaml`
- Mirror the structure of resources-ms-config for consistency

#### 2.3 Create ConfigMap for Database Configuration
**File:** `k8s/1.8-database-config.yaml`

**Content Structure:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: database-config
  namespace: k8s-program
data:
  POSTGRES_DB_RESOURCES: "resources_db"
  POSTGRES_DB_SONGS: "songs_db"
```

**Actions:**
- Store database names for both PostgreSQL instances
- Keep separate from application configs for better separation of concerns

---

## Step 3: Add SQL Scripts to ConfigMaps for Database Initialization

### Objective
Create ConfigMaps containing SQL DDL scripts to initialize database schemas, replacing the current Hibernate `create-drop` strategy with explicit SQL initialization.

### Implementation Details

#### 3.1 Create SQL Script for Resources Database
**File:** `k8s/1.9.1-resources-db-init-config.yaml`

**SQL Script Content:**
```sql
-- Resources Database Initialization Script
-- Table: resources
-- Stores binary MP3 file data

CREATE TABLE IF NOT EXISTS resources (
    id SERIAL PRIMARY KEY,
    blob BYTEA NOT NULL
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_resources_id ON resources(id);

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE resources TO postgres;
GRANT USAGE, SELECT ON SEQUENCE resources_id_seq TO postgres;
```

**ConfigMap Structure:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resources-db-init-sql
  namespace: k8s-program
data:
  init.sql: |
    <SQL script content here>
```

**Actions:**
- Derive schema from `ResourceDomain.java` entity:
  - `id`: SERIAL PRIMARY KEY (auto-increment integer)
  - `blob`: BYTEA (binary data for MP3 files)
- Add `IF NOT EXISTS` clauses for idempotency
- Include proper permissions and sequence grants
- Add comments for documentation

#### 3.2 Create SQL Script for Songs Database
**File:** `k8s/1.9.2-songs-db-init-config.yaml`

**SQL Script Content:**
```sql
-- Songs Database Initialization Script
-- Table: songs
-- Stores MP3 metadata (title, artist, album, etc.)

CREATE TABLE IF NOT EXISTS songs (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    artist VARCHAR(255),
    album VARCHAR(255),
    length VARCHAR(50),
    resource_id INTEGER UNIQUE NOT NULL,
    year VARCHAR(10)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_songs_resource_id ON songs(resource_id);
CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist);
CREATE INDEX IF NOT EXISTS idx_songs_album ON songs(album);

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE songs TO postgres;
GRANT USAGE, SELECT ON SEQUENCE songs_id_seq TO postgres;
```

**ConfigMap Structure:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: songs-db-init-sql
  namespace: k8s-program
data:
  init.sql: |
    <SQL script content here>
```

**Actions:**
- Derive schema from `SongData.java` entity:
  - `id`: SERIAL PRIMARY KEY (auto-increment)
  - `name`, `artist`, `album`, `length`, `year`: VARCHAR (nullable)
  - `resource_id`: INTEGER UNIQUE NOT NULL (foreign reference)
- Add indexes for common query patterns (resource_id, artist, album)
- Ensure idempotency with `IF NOT EXISTS`
- Match column names to JPA entity fields (snake_case for `resource_id`)

#### 3.3 Schema Mapping Details

**From JPA Entities to PostgreSQL:**
- `@Id @GeneratedValue` → `SERIAL PRIMARY KEY`
- `@Column(nullable = true)` → no `NOT NULL` constraint
- `@Column(unique = true, nullable = false)` → `UNIQUE NOT NULL`
- `@Lob @JdbcType(VarbinaryJdbcType.class)` → `BYTEA`
- String fields → `VARCHAR(255)` (standard length, adjust if needed)

---

## Step 4: Modify Deployments and StatefulSets to Use Secrets and ConfigMaps

### Objective
Update all Deployment and StatefulSet manifests to consume Secrets and ConfigMaps instead of hardcoded values.

### Implementation Details

#### 4.1 Update Resources Database StatefulSet
**File:** `k8s/2-resource-db.yaml`

**Changes Required:**

1. **Replace hardcoded credentials with Secret references:**
```yaml
env:
  - name: POSTGRES_DB
    valueFrom:
      configMapKeyRef:
        name: database-config
        key: POSTGRES_DB_RESOURCES
  - name: POSTGRES_USER
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-username
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-password
```

2. **Add SQL initialization script volume mount:**
```yaml
volumeMounts:
  - name: db-data
    mountPath: /var/lib/postgresql
  - name: init-script
    mountPath: /docker-entrypoint-initdb.d
    readOnly: true
```

3. **Add ConfigMap volume:**
```yaml
volumes:
  - name: init-script
    configMap:
      name: resources-db-init-sql
```

**Actions:**
- Remove all hardcoded `value:` fields for credentials
- Add `valueFrom` references to Secret and ConfigMap
- Mount init SQL script to `/docker-entrypoint-initdb.d` (PostgreSQL auto-execution path)
- Set `readOnly: true` for security

#### 4.2 Update Songs Database StatefulSet
**File:** `k8s/3-song-db.yaml`

**Changes Required:**

1. **Replace hardcoded credentials with Secret references:**
```yaml
env:
  - name: POSTGRES_DB
    valueFrom:
      configMapKeyRef:
        name: database-config
        key: POSTGRES_DB_SONGS
  - name: POSTGRES_USER
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-username
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-password
```

2. **Add SQL initialization script volume mount:**
```yaml
volumeMounts:
  - name: db-data
    mountPath: /var/lib/postgresql
  - name: init-script
    mountPath: /docker-entrypoint-initdb.d
    readOnly: true
```

3. **Add ConfigMap volume:**
```yaml
volumes:
  - name: init-script
    configMap:
      name: songs-db-init-sql
```

**Actions:**
- Mirror changes from resources-db StatefulSet
- Use `songs-db-init-sql` ConfigMap for initialization
- Ensure consistent volume naming across both databases

#### 4.3 Update Resources Microservice Deployment
**File:** `k8s/4-resource-ms.yaml`

**Changes Required:**

1. **Replace individual env vars with ConfigMap reference:**
```yaml
envFrom:
  - configMapRef:
      name: resources-ms-config
```

2. **Add Secret references for database credentials:**
```yaml
env:
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-username
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-password
```

**Actions:**
- Use `envFrom` to load entire ConfigMap as environment variables
- Keep database credentials as explicit `env` entries with Secret references
- Remove all hardcoded `value:` fields for non-sensitive config
- Maintain existing variable names for application compatibility

#### 4.4 Update Songs Microservice Deployment
**File:** `k8s/5-song-ms.yaml`

**Changes Required:**

1. **Replace individual env vars with ConfigMap reference:**
```yaml
envFrom:
  - configMapRef:
      name: songs-ms-config
```

2. **Add Secret references for database credentials:**
```yaml
env:
  - name: SPRING_DATASOURCE_USERNAME
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-username
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgres-password
```

**Actions:**
- Mirror structure of resources-ms Deployment
- Use `songs-ms-config` ConfigMap for service-specific configuration
- Maintain consistent Secret reference pattern

---

## Deployment Order and Dependencies

### Updated File Numbering
1. `0-namespace.yaml` - Namespace (unchanged)
2. `1-songs-storage.yaml` - PersistentVolume (unchanged)
3. `1.5-secrets.yaml` - **NEW** - Database credentials
4. `1.6-resources-config.yaml` - **NEW** - Resources MS config
5. `1.7-songs-config.yaml` - **NEW** - Songs MS config
6. `1.8-database-config.yaml` - **NEW** - Database names
7. `1.9.1-resources-db-init-config.yaml` - **NEW** - Resources SQL init
8. `1.9.2-songs-db-init-config.yaml` - **NEW** - Songs SQL init
9. `2-resource-db.yaml` - Resources DB (modified)
10. `3-song-db.yaml` - Songs DB (modified)
11. `4-resource-ms.yaml` - Resources MS (modified)
12. `5-song-ms.yaml` - Songs MS (modified)

### Dependency Graph
```
namespace → secrets, configmaps → databases (with init) → microservices
```

**Critical Dependencies:**
- Secrets and ConfigMaps must exist before StatefulSets/Deployments reference them
- Databases must be ready before microservices start
- Init scripts execute only on first pod creation (empty volume)

---

## Testing and Validation

### 5.1 Pre-Deployment Validation
**Commands:**
```bash
# Validate YAML syntax
kubectl apply --dry-run=client -f k8s/

# Check base64 encoding
echo -n 'postgres' | base64
echo -n 'cG9zdGdyZXM=' | base64 -d  # Verify decode

# Validate Secret creation
kubectl create secret generic test-secret --from-literal=key=value --dry-run=client -o yaml
```

### 5.2 Deployment Steps
```bash
# Deploy in order
kubectl apply -f k8s/0-namespace.yaml
kubectl apply -f k8s/1-songs-storage.yaml
kubectl apply -f k8s/1.5-secrets.yaml
kubectl apply -f k8s/1.6-resources-config.yaml
kubectl apply -f k8s/1.7-songs-config.yaml
kubectl apply -f k8s/1.8-database-config.yaml
kubectl apply -f k8s/1.9.1-resources-db-init-config.yaml
kubectl apply -f k8s/1.9.2-songs-db-init-config.yaml
kubectl apply -f k8s/2-resource-db.yaml
kubectl apply -f k8s/3-song-db.yaml

# Wait for databases to be ready
kubectl wait --for=condition=ready pod -l app=resources-db -n k8s-program --timeout=120s
kubectl wait --for=condition=ready pod -l app=songs-db -n k8s-program --timeout=120s

# Deploy microservices
kubectl apply -f k8s/4-resource-ms.yaml
kubectl apply -f k8s/5-song-ms.yaml
```

### 5.3 Post-Deployment Verification

**Check Secrets:**
```bash
kubectl get secrets -n k8s-program
kubectl describe secret db-credentials -n k8s-program
```

**Check ConfigMaps:**
```bash
kubectl get configmaps -n k8s-program
kubectl describe configmap resources-ms-config -n k8s-program
kubectl describe configmap songs-ms-config -n k8s-program
kubectl describe configmap resources-db-init-sql -n k8s-program
kubectl describe configmap songs-db-init-sql -n k8s-program
```

**Verify Database Initialization:**
```bash
# Connect to resources-db
kubectl exec -it resources-db-0 -n k8s-program -- psql -U postgres -d resources_db -c "\dt"
kubectl exec -it resources-db-0 -n k8s-program -- psql -U postgres -d resources_db -c "\d resources"

# Connect to songs-db
kubectl exec -it songs-db-0 -n k8s-program -- psql -U postgres -d songs_db -c "\dt"
kubectl exec -it songs-db-0 -n k8s-program -- psql -U postgres -d songs_db -c "\d songs"
```

**Expected Output:**
- Tables `resources` and `songs` should exist
- Correct column types and constraints
- Indexes created successfully
- No errors in PostgreSQL logs

**Check Environment Variables in Pods:**
```bash
# Resources MS
kubectl exec -it deployment/resources-ms -n k8s-program -- env | grep -E 'RESOURCES|SONGS|SPRING'

# Songs MS
kubectl exec -it deployment/songs-ms -n k8s-program -- env | grep -E 'SONGS|SPRING'
```

**Verify Application Logs:**
```bash
kubectl logs -l app=resources-ms -n k8s-program --tail=50
kubectl logs -l app=songs-ms -n k8s-program --tail=50
```

**Look for:**
- Successful database connection messages
- No authentication failures
- Hibernate schema validation (not creation)
- Application startup completion

### 5.4 Functional Testing
```bash
# Test Resources Service
curl -X POST http://localhost:30080/resources -H "Content-Type: audio/mpeg" --data-binary @test.mp3

# Test Songs Service
curl http://localhost:30081/songs/1
```

**Verify:**
- Resource upload creates entry in `resources` table
- Song metadata stored in `songs` table with `resource_id` reference
- Cross-service communication works (resources-ms → songs-ms)

---

## Rollback Strategy

### If Deployment Fails:

**Quick Rollback:**
```bash
# Delete new resources
kubectl delete -f k8s/1.5-secrets.yaml
kubectl delete -f k8s/1.6-resources-config.yaml
kubectl delete -f k8s/1.7-songs-config.yaml
kubectl delete -f k8s/1.8-database-config.yaml
kubectl delete -f k8s/1.9.1-resources-db-init-config.yaml
kubectl delete -f k8s/1.9.2-songs-db-init-config.yaml

# Restore previous deployments
git checkout HEAD~1 k8s/2-resource-db.yaml
git checkout HEAD~1 k8s/3-song-db.yaml
git checkout HEAD~1 k8s/4-resource-ms.yaml
git checkout HEAD~1 k8s/5-song-ms.yaml

kubectl apply -f k8s/
```

### Common Issues and Fixes:

**Issue 1: Secret not found**
- **Cause:** Deployment references Secret before it's created
- **Fix:** Check Secret exists: `kubectl get secret db-credentials -n k8s-program`
- **Fix:** Verify namespace matches

**Issue 2: ConfigMap not found**
- **Cause:** Similar to Secret issue
- **Fix:** Verify ConfigMap name matches reference in Deployment

**Issue 3: SQL script not executing**
- **Cause:** Volume mount path incorrect
- **Fix:** Ensure `/docker-entrypoint-initdb.d` path is exact
- **Fix:** Check ConfigMap contains `init.sql` key (not `script.sql` or other)

**Issue 4: Database connection failures**
- **Cause:** Credential mismatch or encoding error
- **Fix:** Verify base64 encoding: `echo 'cG9zdGdyZXM=' | base64 -d`
- **Fix:** Check Secret keys match env var references

**Issue 5: Schema already exists errors**
- **Cause:** Init script runs on restart with existing data
- **Fix:** Use `CREATE TABLE IF NOT EXISTS` (already in plan)
- **Fix:** Delete PVC to force re-initialization: `kubectl delete pvc -l app=resources-db -n k8s-program`

---

## Configuration Updates Required

### Application Configuration Changes

**Update:** `resources-service/src/main/resources/application.properties`
```properties
# Disable Hibernate auto-DDL (we manage schema via SQL scripts)
spring.jpa.hibernate.ddl-auto=validate

# Optional: Enable SQL logging for debugging
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

**Update:** `songs-service/src/main/resources/application.properties`
```properties
# Disable Hibernate auto-DDL
spring.jpa.hibernate.ddl-auto=validate

# Optional: Enable SQL logging
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

**Rationale:**
- Change from `create-drop` to `validate`
- `validate`: Hibernate validates schema matches entities but doesn't modify it
- Prevents data loss on pod restarts
- SQL scripts now own schema creation

### Docker Images Rebuild
After changing `application.properties`:
```bash
# Rebuild resources service
cd resources-service
./mvnw clean package
docker build -t resources-image:v3 .

# Rebuild songs service
cd ../songs-service
./mvnw clean package
docker build -t songs-image:v3 .

# Update image tags in k8s manifests
# k8s/4-resource-ms.yaml: image: resources-image:v3
# k8s/5-song-ms.yaml: image: songs-image:v3
```

---

## Documentation Updates

### Files to Update:

**1. `docs/kubernetes.md`**
- Add section on Secrets and ConfigMaps
- Document new numbered manifests
- Update deployment order

**2. `docs/database-configuration.md`**
- Document SQL initialization approach
- Explain schema management strategy
- Note change from `create-drop` to `validate`

**3. `docs/environment-configuration.md`**
- List all ConfigMap keys and purposes
- Document Secret structure (without values!)
- Explain how to update configuration

**4. `README.md`**
- Update quick start guide with new deployment steps
- Add troubleshooting section for Secrets/ConfigMaps

---

## Security Best Practices

### Implemented:
✅ Secrets for sensitive data (credentials)
✅ ConfigMaps for non-sensitive configuration
✅ Base64 encoding for Secret values
✅ Read-only volume mounts for init scripts
✅ Namespace isolation

### Recommended for Production:
- Use Kubernetes RBAC to restrict Secret access
- Enable Secret encryption at rest
- Use external secret management (Vault, AWS Secrets Manager)
- Implement Sealed Secrets for GitOps workflows
- Rotate credentials regularly
- Use different credentials per environment
- Audit Secret access logs

---

## Summary

This plan transforms the deployment from hardcoded configuration to a proper Kubernetes-native approach:

1. **Secrets**: Secure credential storage
2. **ConfigMaps**: Externalized non-sensitive configuration
3. **SQL Init Scripts**: Explicit schema management replacing Hibernate auto-DDL
4. **Idempotent Deployments**: `IF NOT EXISTS` clauses allow safe re-runs
5. **Separation of Concerns**: Config separate from application code

**Benefits:**
- Easier configuration updates without redeploying pods
- Better security with proper credential management
- Explicit schema control (no accidental data loss)
- Environment-agnostic deployments (dev/staging/prod)
- GitOps-friendly (except Secret values)

**Next Steps After Implementation:**
- Test full application flow end-to-end
- Verify persistence across pod restarts
- Document configuration change procedures
- Set up CI/CD pipeline with proper Secret injection
