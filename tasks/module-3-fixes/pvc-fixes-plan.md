# PVC Usage in Deployment - Fix Plan

## Problem Statement

**Feedback received**: "Using PVC directly in Deployment is not fine."

**Current Issue**: The `songs-ms` Deployment (file: `k8s-helm-chart/templates/5-song-ms.yaml:85-86`) is using a PersistentVolumeClaim (`songs-app-pvc`) directly. This violates Kubernetes best practices.

## Why This Is Problematic

### 1. **Shared Storage Across All Replicas**
- All pods in a Deployment share the **same PVC**
- With `replicas: {{ .Values.replicaCount }}`, multiple pods will mount the same volume
- This can cause:
  - **Data corruption** if multiple pods write to the same files simultaneously
  - **Race conditions** when handling MP3 file uploads
  - **Inconsistent state** across pod instances

### 2. **ReadWriteOnce Access Mode Limitations**
- The current PVC uses `accessModes: [ReadWriteOnce]` (file: `k8s-helm-chart/templates/1.1-songs-storage.yaml:23`)
- RWO means the volume can only be mounted by **one node** at a time
- Scaling issues:
  - If `replicas > 1` and pods land on different nodes, only the first pod can mount the volume
  - Other pods will be stuck in `Pending` state with volume attachment errors

### 3. **Pod Rescheduling Problems**
- If the node hosting the PVC fails, Kubernetes cannot reschedule the pod to another node
- The pod remains bound to the failed node until manual intervention

### 4. **No Per-Pod Identity**
- Deployments treat pods as interchangeable
- Using persistent storage requires pod identity to avoid conflicts
- Lack of stable network identity and persistent storage per pod

## Root Cause Analysis

Looking at the architecture:
- **Songs Service** stores and manages song metadata in a PostgreSQL database
- The `/app/data` mount point at `5-song-ms.yaml:82` suggests the service might be:
  - Caching data locally
  - Storing temporary processing files
  - OR incorrectly using local storage for persistent data that should be in the database

## Recommended Solutions

### **Option 1: Remove PVC Entirely** ✅ **RECOMMENDED**

**Rationale**: Songs Service already uses a PostgreSQL database for persistence. Local storage is likely unnecessary.

**Changes Required**:
1. Remove the `volumeMounts` and `volumes` sections from `5-song-ms.yaml` (lines 80-86)
2. Keep the PVC definition but mark it as deprecated/unused
3. Update application code if it writes to `/app/data` to use the database or in-memory caching

**Pros**:
- True stateless Deployment - can scale freely
- No node affinity issues
- Simpler configuration
- Follows microservices best practices

**Cons**:
- Requires code changes if the app writes to `/app/data`
- May need to implement alternative caching strategy

---

### **Option 2: Use emptyDir for Temporary Storage**

**Use Case**: If `/app/data` is only used for temporary processing (e.g., temporary MP3 processing files).

**Changes Required**:
```yaml
# In 5-song-ms.yaml, replace the volumes section:
      volumes:
        - name: songs-local-storage
          emptyDir:
            sizeLimit: 1Gi
```

**Pros**:
- No PVC needed
- Each pod gets its own temporary storage
- Automatically cleaned up when pod is deleted
- Works with any replica count

**Cons**:
- Data lost when pod restarts
- Not suitable for long-term storage

---

### **Option 3: Convert to StatefulSet** ⚠️ **NOT RECOMMENDED**

**Use Case**: ONLY if each pod truly needs its own persistent storage identity.

**Changes Required**:
1. Change `kind: Deployment` to `kind: StatefulSet` in `5-song-ms.yaml`
2. Replace the `volumes` section with `volumeClaimTemplates`:
```yaml
  volumeClaimTemplates:
    - metadata:
        name: songs-local-storage
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: manual
        resources:
          requests:
            storage: 1Gi
```
3. Update Service to use `clusterIP: None` for headless service
4. Each pod gets a stable identity (songs-ms-0, songs-ms-1, etc.)

**Pros**:
- Each pod gets its own PVC
- Stable pod identity and storage
- Proper solution for stateful workloads

**Cons**:
- **Overkill for this use case** - Songs Service is designed as a stateless microservice
- More complex to manage
- Slower rollouts (sequential updates)
- Database already provides persistence

---

### **Option 4: Use ReadWriteMany (RWX) PVC** ⚠️ **NOT RECOMMENDED**

**Changes Required**:
- Change PV/PVC to use `accessModes: [ReadWriteMany]`
- Requires NFS or similar shared filesystem storage class

**Pros**:
- Multiple pods can mount the same volume on different nodes

**Cons**:
- **Doesn't solve the data corruption issue** - still shared storage
- Not available with `hostPath` storage (requires NFS/CephFS/etc.)
- Performance bottleneck with concurrent writes
- Wrong pattern for stateless microservices

---

## Implementation Recommendation

### **Preferred Approach: Option 1 (Remove PVC)**

**Step 1: Verify Application Usage**
```bash
# Check if songs-ms code actually uses /app/data
grep -r "/app/data" songs-service/src/
```

**Step 2: Remove Volume Configuration**
Edit `k8s-helm-chart/templates/5-song-ms.yaml`:
- Remove lines 80-86 (volumeMounts and volumes sections)

**Step 3: Update Application (if needed)**
- If the app writes to `/app/data`, refactor to:
  - Store all persistent data in PostgreSQL
  - Use in-memory caching (e.g., Caffeine, Spring Cache)
  - Use distributed cache (Redis) if needed across pods

**Step 4: Deprecate PVC**
Add comment to `1.1-songs-storage.yaml`:
```yaml
# DEPRECATED: This PVC is no longer used by songs-ms as of [date]
# Can be safely deleted after verifying no data loss
```

---

## Comparison with Resources Service

**Current State**:
- `resources-ms` Deployment (file: `4-resource-ms.yaml`) does **NOT** use any PVC ✅
- It's a proper stateless Deployment
- Uses PostgreSQL for persistence

**Databases**:
- Both `resource-db` and `song-db` correctly use `StatefulSet` with `volumeClaimTemplates` (not in scope for this fix)

---

## Migration Path

1. **Low-Risk Migration**:
   - Deploy with emptyDir first (Option 2) to validate no persistent data is needed
   - Monitor for 1-2 weeks
   - If stable, fully remove volume (Option 1)

2. **Validation Tests**:
   - Test with `replicas: 2` to ensure no conflicts
   - Test pod restarts to verify data loss is acceptable
   - Monitor application logs for file I/O errors

---

## Decision Matrix

| Criterion | Option 1<br>(Remove PVC) | Option 2<br>(emptyDir) | Option 3<br>(StatefulSet) | Option 4<br>(RWX) |
|-----------|--------------------------|------------------------|---------------------------|-------------------|
| **Best Practice** | ✅ Yes | ✅ Yes | ⚠️ Overkill | ❌ No |
| **Scalability** | ✅ Perfect | ✅ Perfect | ⚠️ Limited | ❌ Poor |
| **Complexity** | ✅ Low | ✅ Low | ❌ High | ❌ High |
| **Data Safety** | ✅ DB-backed | ⚠️ Temporary | ✅ Persistent | ⚠️ Corruption risk |
| **Effort** | Low-Medium | Low | High | High |
| **Recommendation** | ✅ **BEST** | ✅ Safe fallback | ❌ Wrong pattern | ❌ Avoid |

---

## Next Steps

1. **Investigate**: Check songs-service source code for `/app/data` usage
2. **Choose**: Decide between Option 1 (ideal) or Option 2 (safe fallback)
3. **Implement**: Update `5-song-ms.yaml` according to chosen option
4. **Test**: Deploy with multiple replicas and verify behavior
5. **Document**: Update architecture docs to clarify storage strategy
6. **Cleanup**: Remove or deprecate unused PVC after successful migration

---

## References

- Kubernetes Docs: [StatefulSet vs Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- Best Practice: Use Deployments for stateless apps, StatefulSets for stateful apps
- Current PVC: `k8s-helm-chart/templates/1.1-songs-storage.yaml`
- Affected Deployment: `k8s-helm-chart/templates/5-song-ms.yaml`
