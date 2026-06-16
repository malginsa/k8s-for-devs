# Implementation Plan: Ingress-Nginx Setup with Path Rewriting

## Overview
Configure Ingress-Nginx Controller to route external traffic to the microservices (resources-ms and songs-ms) with path rewriting. External requests will include the service name in the path (`/api/v1/songs`, `/api/v1/resources`), but these will be rewritten to remove the service name before forwarding to the backend (`/api/v1`).

## Prerequisites
- Kubernetes cluster running (Rancher Desktop with K3s)
- Helm installed
- Current services deployed with NodePort type (resources-ms: 30080, songs-ms: 30081)
- kubectl configured with rancher-desktop context

## Path Rewriting Logic
- External: `http://localhost:8080/api/v1/songs/*` → Backend: `songs-ms:8081/api/v1/*`
- External: `http://localhost:8080/api/v1/resources/*` → Backend: `resources-ms:8080/api/v1/*`

The service name (`songs` or `resources`) is stripped from the path before forwarding.

## Implementation Steps

### Step 1: Install Ingress-Nginx Controller
**Objective:** Deploy the official Nginx Ingress Controller using Helm chart.

**Actions:**
1. Add the ingress-nginx Helm repository:
   ```bash
   helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
   helm repo update
   ```

2. Install ingress-nginx controller in the `k8s-program` namespace:
   ```bash
   helm install ingress-nginx ingress-nginx/ingress-nginx \
     --namespace k8s-program \
     --set controller.service.type=LoadBalancer \
     --set controller.admissionWebhooks.enabled=false
   ```

3. Verify the installation:
   ```bash
   kubectl get pods -n k8s-program -l app.kubernetes.io/name=ingress-nginx
   kubectl get svc -n k8s-program -l app.kubernetes.io/name=ingress-nginx
   ```

**Expected Result:** Ingress controller pod running and service created (LoadBalancer type will expose on localhost with Rancher Desktop).

---

### Step 2: Change Microservice Services to ClusterIP
**Objective:** Restrict external access to microservices by converting from NodePort to ClusterIP.

**Actions:**
1. Update `k8s-helm-chart/values.yaml`:
   - Change `microservices.resourcesMs.service.type` from `NodePort` to `ClusterIP`
   - Change `microservices.songsMs.service.type` from `NodePort` to `ClusterIP`
   - The `nodePort` fields (30080, 30081) will be ignored when type is ClusterIP

2. The service templates already support this change:
   - File: `k8s-helm-chart/templates/4-resource-ms.yaml`
   - File: `k8s-helm-chart/templates/5-song-ms.yaml`
   - The conditional `{{- if eq $ms.service.type "NodePort" }}` handles this properly

3. Apply the changes:
   ```bash
   helm upgrade microservices-app k8s-helm-chart -n k8s-program
   ```

4. Verify services are now ClusterIP:
   ```bash
   kubectl get svc -n k8s-program resources-ms songs-ms
   ```

**Expected Result:** 
- Services show TYPE=ClusterIP
- No external ports (30080, 30081) are exposed
- Services remain accessible within the cluster via their service names

---

### Step 3: Create Ingress Resource with Routing Rules
**Objective:** Define ingress routes with basic path-based routing (before adding rewrite).

**Actions:**
1. Create a new Helm template file: `k8s-helm-chart/templates/6-ingress.yaml`

2. Define ingress resource with path-based routing:
   ```yaml
   apiVersion: networking.k8s.io/v1
   kind: Ingress
   metadata:
     name: microservices-ingress
     namespace: {{ .Values.global.namespace }}
   spec:
     ingressClassName: nginx
     rules:
       - http:
           paths:
             # Resources Service
             - path: /api/v1/resources
               pathType: Prefix
               backend:
                 service:
                   name: resources-ms
                   port:
                     number: 8080
             # Songs Service
             - path: /api/v1/songs
               pathType: Prefix
               backend:
                 service:
                   name: songs-ms
                   port:
                     number: 8081
   ```

3. Apply the changes:
   ```bash
   helm upgrade microservices-app k8s-helm-chart -n k8s-program
   ```

4. Verify ingress is created:
   ```bash
   kubectl get ingress -n k8s-program
   kubectl describe ingress microservices-ingress -n k8s-program
   ```

**Expected Result:**
- Ingress resource created with routing rules
- Traffic routes to correct services (though paths not yet rewritten)

---

### Step 4: Configure Path Rewriting with Annotations
**Objective:** Implement path rewriting to strip service names from paths.

**Reference Documentation:** https://kubernetes.github.io/ingress-nginx/examples/rewrite/#rewrite-target

**How Path Rewriting Works:**
- Use regex capture groups in the path pattern
- The annotation `nginx.ingress.kubernetes.io/rewrite-target` specifies the target path
- Captured groups are referenced as `$1`, `$2`, etc.

**Actions:**
1. Update `k8s-helm-chart/templates/6-ingress.yaml` with rewrite annotations and regex paths:

   ```yaml
   apiVersion: networking.k8s.io/v1
   kind: Ingress
   metadata:
     name: microservices-ingress
     namespace: {{ .Values.global.namespace }}
     annotations:
       nginx.ingress.kubernetes.io/rewrite-target: /api/v1/$2
   spec:
     ingressClassName: nginx
     rules:
       - http:
           paths:
             # Resources Service with path rewrite
             # Matches: /api/v1/resources/<anything>
             # Rewrites to: /api/v1/<anything>
             - path: /api/v1/resources(/|$)(.*)
               pathType: ImplementationSpecific
               backend:
                 service:
                   name: resources-ms
                   port:
                     number: 8080
             
             # Songs Service with path rewrite
             # Matches: /api/v1/songs/<anything>
             # Rewrites to: /api/v1/<anything>
             - path: /api/v1/songs(/|$)(.*)
               pathType: ImplementationSpecific
               backend:
                 service:
                   name: songs-ms
                   port:
                     number: 8081
   ```

2. Apply the configuration:
   ```bash
   helm upgrade microservices-app k8s-helm-chart -n k8s-program
   ```

3. Verify the ingress configuration:
   ```bash
   kubectl describe ingress microservices-ingress -n k8s-program
   ```

**How the Regex Works:**
- Pattern: `/api/v1/songs(/|$)(.*)`
  - Matches `/api/v1/songs` followed by either `/` or end-of-string
  - First capture group `$1`: the `/` or empty string
  - Second capture group `$2`: everything after that
- Rewrite target: `/api/v1/$2`
  - Takes only the second capture group
  - Effectively removes `/songs` from the path

**Examples:**
- Request: `/api/v1/songs/123`
  - Regex matches: `$1 = /`, `$2 = 123`
  - Rewrites to: `/api/v1/123`
  - Forwards to: `songs-ms:8081/api/v1/123`

- Request: `/api/v1/songs`
  - Regex matches: `$1 = ` (empty), `$2 = ` (empty)
  - Rewrites to: `/api/v1/`
  - Forwards to: `songs-ms:8081/api/v1/`

- Request: `/api/v1/resources/file`
  - Regex matches: `$1 = /`, `$2 = file`
  - Rewrites to: `/api/v1/file`
  - Forwards to: `resources-ms:8080/api/v1/file`

**Expected Result:**
- Request: `http://localhost:8080/api/v1/songs/123`
  - Ingress routes to: `songs-ms:8081/api/v1/123`
- Request: `http://localhost:8080/api/v1/resources/456`
  - Ingress routes to: `resources-ms:8080/api/v1/456`

---

### Step 5: Testing and Verification
**Objective:** Validate the complete ingress setup and path rewriting.

**Actions:**
1. Get the ingress controller external IP/port:
   ```bash
   kubectl get svc -n k8s-program ingress-nginx-controller
   ```
   Note: With Rancher Desktop, this will be available on localhost (typically port 80 or a high port)

2. Test resources service endpoints:
   ```bash
   # Upload resource with multipart form
   curl -X POST -F "file=@test.mp3" http://localhost:8080/api/v1/resources/file
   
   # Upload resource with binary
   curl -X POST -H "Content-Type: audio/mpeg" --data-binary @test.mp3 http://localhost:8080/api/v1/resources
   
   # Get specific resource
   curl http://localhost:8080/api/v1/resources/{id}
   
   # Delete resources
   curl -X DELETE "http://localhost:8080/api/v1/resources?ids=1,2"
   ```

3. Test songs service endpoints:
   ```bash
   # Get song metadata by resource ID
   curl http://localhost:8080/api/v1/songs/{id}
   
   # Create song
   curl -X POST -H "Content-Type: application/json" \
     -d '{"name":"Test Song","artist":"Test Artist"}' \
     http://localhost:8080/api/v1/songs
   
   # Delete songs by song IDs
   curl -X DELETE "http://localhost:8080/api/v1/songs?ids=1,2"
   ```

4. Verify path rewriting by checking service logs:
   ```bash
   kubectl logs -n k8s-program -l app=songs-ms --tail=50
   kubectl logs -n k8s-program -l app=resources-ms --tail=50
   ```
   - Logs should show incoming requests to `/api/v1/*` (NOT `/api/v1/songs/*` or `/api/v1/resources/*`)
   - This confirms the service name was stripped

5. Test that direct NodePort access no longer works (after Step 2):
   ```bash
   curl http://localhost:30080/resources  # Should fail
   curl http://localhost:30081/songs      # Should fail
   ```

**Expected Result:**
- All requests through ingress work correctly
- Path rewriting is transparent to clients
- Backend services receive paths without service names
- Direct NodePort access is blocked
- Services are only accessible through the ingress controller

---

## Rollback Plan
If issues occur during implementation:

1. **If Step 2 breaks connectivity:**
   ```bash
   helm upgrade microservices-app k8s-helm-chart \
     --set microservices.resourcesMs.service.type=NodePort \
     --set microservices.songsMs.service.type=NodePort \
     -n k8s-program
   ```

2. **If ingress causes issues:**
   ```bash
   kubectl delete ingress microservices-ingress -n k8s-program
   ```

3. **Complete rollback:**
   ```bash
   helm uninstall ingress-nginx -n k8s-program
   # Restore values.yaml to original NodePort configuration
   helm upgrade microservices-app k8s-helm-chart -n k8s-program
   ```

---

## Files to be Modified/Created

1. **New files:**
   - `k8s-helm-chart/templates/6-ingress.yaml` - Ingress resource definition with path rewriting

2. **Modified files:**
   - `k8s-helm-chart/values.yaml` - Change service types from NodePort to ClusterIP

3. **Documentation updates (optional):**
   - `docs/kubernetes.md` - Document ingress configuration
   - `docs/api-endpoints.md` - Update with new ingress-based URLs

---

## Success Criteria

1. ✅ Ingress-nginx controller installed and running
2. ✅ Microservice services changed to ClusterIP type
3. ✅ No direct external access via NodePort (30080, 30081)
4. ✅ Ingress resource created with routing rules
5. ✅ Path rewriting configured correctly using regex and annotations
6. ✅ All API endpoints accessible through ingress controller
7. ✅ Path rewriting verified: `http://localhost:8080/api/v1/songs/X` → `songs-ms:8081/api/v1/X`
8. ✅ Service logs confirm paths without service names are received
9. ✅ Health checks still functioning

---

## Notes and Considerations

1. **Backend Service Endpoints:** The actual backend services expose endpoints at `/resources/*` and `/songs/*` (not `/api/v1/*`). This means the services will need to be updated to match the rewritten paths, OR the rewrite target needs to be adjusted to match the actual backend paths.

2. **Possible Adjustment Needed:** If services cannot be modified, change the rewrite target to:
   - For songs: `nginx.ingress.kubernetes.io/rewrite-target: /songs/$2`
   - For resources: `nginx.ingress.kubernetes.io/rewrite-target: /resources/$2`
   
   But this requires separate ingress resources for each service since they need different rewrite targets.

3. **Port Changes:** After ingress setup, the main entry point will be the ingress controller's port (typically 80), not the NodePorts (30080/30081).

4. **Health Probes:** Kubernetes health probes will continue to work as they access the pod directly, not through the ingress.

5. **Internal Communication:** The resources-ms to songs-ms communication remains unchanged (uses Kubernetes DNS: `songs-ms:8081`).

6. **Rancher Desktop:** With Rancher Desktop, the LoadBalancer service type will automatically expose on localhost.

7. **PathType:** Using `ImplementationSpecific` for pathType when using regex patterns. This is required for the regex to work properly.

8. **Multiple Ingress Resources Alternative:** If a single ingress with one rewrite-target doesn't work, create separate ingress resources for each service with service-specific rewrite targets.
