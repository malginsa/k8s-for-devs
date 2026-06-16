# Implementation Plan: ReadWriteOnce PVCs with StatefulSet for MP3 Storage

**Date:** 2026-06-10  
**Status:** Planning  
**Complexity:** High - involves storage architecture change, database migration, and StatefulSet conversion

## Current State Analysis

### Problems
1. **Unused PVC:** `songs-app-pvc` (ReadWriteMany) exists but is never mounted
2. **Database storage:** MP3 files stored as BLOBs in PostgreSQL (ResourceDomain.blob field)
3. **Scalability issues:** Database bloat, poor performance, large backups

### Current Architecture
- **resources-ms:** Deployment with 2 replicas
- **Storage:** byte[] blobs in PostgreSQL resources table
- **Service:** ResourcesService handles binary data in-memory

## Target Architecture

### Design Decisions
1. **StatefulSet for resources-ms:** Single-writer pattern with stable pod identity
2. **ReadWriteOnce PVC:** Each pod gets its own persistent volume
3. **Filesystem storage:** MP3 files stored on PVC, only file paths in database
4. **Load balancing:** Multiple StatefulSet pods can read from their local storage

### Architecture Components
```
┌─────────────────────────────────────┐
│  resources-ms-0 (StatefulSet Pod)   │
│  ┌──────────────┐  ┌─────────────┐ │
│  │ Application  │  │   PVC-0     │ │
│  │   (reads/    │──│ /data/songs │ │
│  │   writes)    │  │ (RWO)       │ │
│  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────┘
┌─────────────────────────────────────┐
│  resources-ms-1 (StatefulSet Pod)   │
│  ┌──────────────┐  ┌─────────────┐ │
│  │ Application  │  │   PVC-1     │ │
│  │   (reads/    │──│ /data/songs │ │
│  │   writes)    │  │ (RWO)       │ │
│  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────┘
         │                    │
         └────────┬───────────┘
                  │
         ┌────────▼────────┐
         │  PostgreSQL DB  │
         │  (file paths    │
         │   only)         │
         └─────────────────┘
```

## Implementation Steps

### Phase 1: Application Code Changes (resources-service)

#### 1.1 Update ResourceDomain Entity
**File:** `resources-service/src/main/java/com/ms/intro/domain/ResourceDomain.java`

**Changes:**
- Remove `@Lob byte[] blob` field
- Add `String filePath` field
- Add `String fileName` field (for original filename)
- Add `Long fileSize` field

**New schema:**
```java
@Entity
@Table(name = "resources")
public class ResourceDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_size")
    private Long fileSize;
}
```

#### 1.2 Create FileStorageService
**New File:** `resources-service/src/main/java/com/ms/intro/service/FileStorageService.java`

**Responsibilities:**
- Store MP3 bytes to filesystem
- Retrieve MP3 bytes from filesystem
- Delete files from filesystem
- Generate unique file paths
- Handle file I/O errors

**Key Methods:**
```java
String storeFile(byte[] bytes, String originalFilename)
byte[] loadFile(String filePath)
void deleteFile(String filePath)
```

#### 1.3 Update ResourcesService
**File:** `resources-service/src/main/java/com/ms/intro/service/ResourcesService.java`

**Changes:**
- Inject FileStorageService
- `saveResource()`: Store bytes to filesystem, save path to DB
- `getResource()`: Load file from filesystem using path from DB
- `deleteResources()`: Delete files from filesystem AND database

#### 1.4 Add Application Configuration
**File:** `resources-service/src/main/resources/application.properties`

**New properties:**
```properties
# File storage configuration
file.storage.path=/data/songs
file.storage.enabled=true
```

#### 1.5 Database Migration
**New File:** `resources-service/src/main/resources/db/migration/V2__add_file_storage.sql`

**Migration script:**
```sql
-- Add new columns
ALTER TABLE resources ADD COLUMN file_path VARCHAR(512);
ALTER TABLE resources ADD COLUMN file_name VARCHAR(255);
ALTER TABLE resources ADD COLUMN file_size BIGINT;

-- For existing records (if any), they will need manual migration
-- or a separate data migration script

-- Future: remove blob column after migration verified
-- ALTER TABLE resources DROP COLUMN blob;
```

### Phase 2: Kubernetes Resources Changes

#### 2.1 Convert Deployment to StatefulSet
**File:** `k8s-helm-chart/templates/4-resource-ms.yaml`

**Changes:**
- Change `kind: Deployment` → `kind: StatefulSet`
- Add `serviceName: resources-ms-headless`
- Add `volumeClaimTemplates` for persistent storage
- Add `volumeMounts` to container spec
- Add pod management policy: `podManagementPolicy: Parallel` (for faster scaling)

**StatefulSet template:**
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: resources-ms
  namespace: {{ .Values.global.namespace }}
spec:
  serviceName: resources-ms-headless
  replicas: {{ $ms.replicaCount }}
  podManagementPolicy: Parallel
  selector:
    matchLabels:
      app: resources-ms
  template:
    spec:
      containers:
        - name: resources-ms
          volumeMounts:
            - name: songs-storage
              mountPath: /data/songs
  volumeClaimTemplates:
    - metadata:
        name: songs-storage
      spec:
        accessModes: [ "ReadWriteOnce" ]
        resources:
          requests:
            storage: {{ .Values.storage.songsStorage.size }}
```

#### 2.2 Create Headless Service
**File:** `k8s-helm-chart/templates/4-resource-ms.yaml`

**Add before StatefulSet:**
```yaml
---
# Headless Service for StatefulSet
apiVersion: v1
kind: Service
metadata:
  name: resources-ms-headless
  namespace: {{ .Values.global.namespace }}
spec:
  clusterIP: None
  selector:
    app: resources-ms
  ports:
    - port: {{ $ms.service.port }}
      targetPort: {{ $ms.service.targetPort }}
```

**Note:** Keep existing ClusterIP service for load balancing

#### 2.3 Remove Unused PVC
**File:** `k8s-helm-chart/templates/1.1-songs-storage.yaml`

**Action:** Delete this entire file (PV and PVC are unused)

#### 2.4 Update Helm Values
**File:** `k8s-helm-chart/values.yaml`

**Add storage configuration:**
```yaml
# Storage configuration
storage:
  songsStorage:
    size: 5Gi  # Adjust based on expected MP3 volume
    storageClass: ""  # Use default, or specify (e.g., "standard", "gp2")
```

### Phase 3: Testing Strategy

#### 3.1 Local Testing (before K8s deployment)
1. Run resources-service locally with filesystem storage
2. Test upload → verify file created on disk
3. Test download → verify file read from disk
4. Test delete → verify file removed from disk
5. Verify songs-ms still receives metadata correctly

#### 3.2 StatefulSet Testing
1. Deploy to K8s
2. Verify StatefulSet creates pods: `resources-ms-0`, `resources-ms-1`
3. Verify PVCs created: `songs-storage-resources-ms-0`, `songs-storage-resources-ms-1`
4. Check pod logs for filesystem access
5. Test upload to each pod and verify persistence after pod restart

#### 3.3 Data Distribution Testing
**Challenge:** With StatefulSet, each pod has its own PVC. Files uploaded to pod-0 won't be visible to pod-1.

**Solutions to test:**
1. **Sticky sessions:** Use session affinity in Service (clientIP)
2. **Consistent hashing:** Route requests based on resource ID
3. **Shared lookup:** Query all pods to find file (performance concern)

**Recommended approach for testing:**
```yaml
# Add to resources-ms Service
spec:
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 3600
```

### Phase 4: Migration Strategy

#### 4.1 Backward Compatibility Phase
**Goal:** Support both storage methods during transition

1. Keep `blob` column in database temporarily
2. FileStorageService checks: if `filePath` exists, use it; else use `blob`
3. Allows gradual migration of existing data

#### 4.2 Data Migration Script (if needed)
If production has existing MP3 data:

```sql
-- Query to identify records needing migration
SELECT id, LENGTH(blob) as size 
FROM resources 
WHERE blob IS NOT NULL 
  AND file_path IS NULL;
```

Migration job (separate script):
- Read blob from database
- Write to filesystem
- Update `file_path`, `file_name`, `file_size`
- Nullify `blob` after verification

### Phase 5: Deployment & Rollback

#### 5.1 Deployment Order
1. Deploy application changes (with backward compatibility)
2. Verify application works with existing DB schema
3. Run database migration (add columns)
4. Deploy StatefulSet (replaces Deployment)
5. Verify PVCs are created and mounted
6. Test upload/download operations
7. Monitor for 24-48 hours
8. Remove `blob` column if all verified

#### 5.2 Rollback Plan
**If issues occur:**

1. **Application rollback:**
   - Revert to previous image tag
   - Application still works with `blob` column

2. **StatefulSet rollback:**
   ```bash
   # Convert back to Deployment
   kubectl delete statefulset resources-ms -n k8s-program
   helm upgrade k8s-helm-chart ./k8s-helm-chart --values values-dev.yaml
   ```

3. **Database rollback:**
   - DO NOT drop new columns immediately
   - Keep both `blob` and `file_path` for 1-2 weeks
   - Only drop after confirming stability

## Open Questions & Decisions Needed

### 1. File Distribution Strategy
**Problem:** StatefulSet pods each have separate PVC. File uploaded to pod-0 not accessible from pod-1.

**Options:**
- **A. Session Affinity:** Sticky sessions route same client to same pod (simple, but not perfect)
- **B. Consistent Hashing:** Route by resource ID hash (requires custom logic)
- **C. Replicate files:** Write to all pods (complex, storage waste)
- **D. Reduce to 1 replica:** Single pod, simple but no HA (maybe for learning project)

**Recommendation:** Start with session affinity + 1-2 replicas for learning project.

### 2. Storage Class
**Decision needed:** Which storage class to use?
- Default (`""`) - works but may not be production-ready
- `standard` - typically available in most K8s clusters
- `fast-ssd` - better performance but higher cost
- Custom storage class with specific provisioner

### 3. Storage Size
**Decision needed:** How much storage per pod?
- Current plan: 5Gi per pod
- Adjust based on expected usage (100 songs × 5MB = 500MB minimum)

### 4. Replica Count
**Current:** 2 replicas  
**Consider:** 1 replica for simplicity in learning project (avoids distribution complexity)

## Validation Checklist

- [ ] ResourceDomain updated to use file paths
- [ ] FileStorageService created and tested
- [ ] ResourcesService refactored to use FileStorageService
- [ ] Database migration script created
- [ ] StatefulSet YAML created with volumeClaimTemplates
- [ ] Headless service created
- [ ] Helm values updated with storage config
- [ ] Unused PVC files removed
- [ ] Local testing completed
- [ ] StatefulSet deployed successfully
- [ ] PVCs created and mounted
- [ ] Upload test successful
- [ ] Download test successful
- [ ] Delete test successful
- [ ] Pod restart persistence verified
- [ ] File distribution strategy tested
- [ ] Documentation updated

## References

- Kubernetes StatefulSets: https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/
- PVC with StatefulSet: https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/#using-statefulsets
- Spring Boot file upload: https://spring.io/guides/gs/uploading-files/

## Notes

- This is a significant architectural change
- Consider if object storage (MinIO/S3) would be simpler for production
- For learning purposes, StatefulSet demonstrates important K8s patterns
- File distribution across pods remains a challenge - may need architectural discussion
