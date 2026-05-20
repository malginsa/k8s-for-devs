# Kubernetes Cleanup Plan: Remove Obsolete kubectl Manifests

## Overview
With Helm now being the primary deployment method, the raw kubectl manifests in the `k8s/` directory are obsolete. This plan outlines the safe removal of these files and necessary documentation updates.

## Current State

### Helm Chart (Primary - Keep)
- **Location**: `k8s-helm-chart/`
- **Purpose**: Templated, parameterized deployments
- **Status**: Active and maintained
- **Benefits**: 
  - Values-based configuration
  - Easier upgrades and rollbacks
  - Template reusability
  - Version management

### kubectl Manifests (Obsolete - Remove)
- **Location**: `k8s/`
- **Purpose**: Original static deployment files
- **Status**: Obsolete (superseded by Helm)
- **Issue**: Duplication and maintenance burden

---

## Files to Remove

### k8s/ Directory - 12 YAML Files (DELETE ENTIRE DIRECTORY)

All files in `k8s/` are now duplicated in `k8s-helm-chart/templates/`:

| kubectl File | Helm Equivalent | Notes |
|--------------|-----------------|-------|
| `0-namespace.yaml` | `k8s-helm-chart/templates/0-namespace.yaml` | ✓ Helmified |
| `1.1-songs-storage.yaml` | `k8s-helm-chart/templates/1.1-songs-storage.yaml` | ✓ Helmified |
| `1.5-secrets.yaml` | `k8s-helm-chart/templates/1.5-secrets.yaml` | ✓ Helmified |
| `1.6-resources-config.yaml` | `k8s-helm-chart/templates/1.6-resources-config.yaml` | ✓ Helmified + Labels |
| `1.7-songs-config.yaml` | `k8s-helm-chart/templates/1.7-songs-config.yaml` | ✓ Helmified + Labels |
| `1.8-database-config.yaml` | `k8s-helm-chart/templates/1.8-database-config.yaml` | ✓ Helmified + Labels |
| `1.9.1-resources-db-init-config.yaml` | `k8s-helm-chart/templates/1.9.1-resources-db-init-config.yaml` | ✓ Helmified + Labels |
| `1.9.2-songs-db-init-config.yaml` | `k8s-helm-chart/templates/1.9.2-songs-db-init-config.yaml` | ✓ Helmified + Labels |
| `2-resource-db.yaml` | `k8s-helm-chart/templates/2-resource-db.yaml` | ✓ Helmified |
| `3-song-db.yaml` | `k8s-helm-chart/templates/3-song-db.yaml` | ✓ Helmified |
| `4-resource-ms.yaml` | `k8s-helm-chart/templates/4-resource-ms.yaml` | ✓ Helmified |
| `5-song-ms.yaml` | `k8s-helm-chart/templates/5-song-ms.yaml` | ✓ Helmified |

### k8s/ Directory - Keep These Files

| File | Reason to Keep |
|------|----------------|
| `notes.txt` | Development notes (move to docs/ or keep) |
| `screenshots_for_module_1.pdf` | Learning materials (move to docs/ or keep) |

---

## Recommended Actions

### Phase 1: Verify Helm Coverage

**Before deletion**, confirm all kubectl manifests are covered by Helm:

```bash
# Render Helm templates
helm template microservices-app k8s-helm-chart > rendered-helm.yaml

# Compare resource counts
echo "kubectl manifests:"
kubectl apply -f k8s/ --dry-run=client -o yaml | grep -c "kind:"

echo "Helm templates:"
grep -c "kind:" rendered-helm.yaml
```

**Success criteria**: Helm should produce the same number of Kubernetes resources as kubectl manifests (excluding duplicates from notes/PDFs).

### Phase 2: Backup (Optional but Recommended)

Create archive before deletion:

```bash
# Create backup
tar -czf k8s-manifests-backup-$(date +%Y%m%d).tar.gz k8s/

# Move to archive location
mkdir -p archive
mv k8s-manifests-backup-*.tar.gz archive/
```

### Phase 3: Remove kubectl Manifests

```bash
# Remove all YAML files from k8s/
rm k8s/*.yaml

# Move non-manifest files to appropriate locations
mv k8s/notes.txt tasks/ # or docs/development-notes.md
mv k8s/screenshots_for_module_1.pdf docs/

# Remove empty k8s/ directory
rmdir k8s/
```

**Alternative approach (safer)**: Rename directory first:

```bash
# Rename to mark as deprecated
mv k8s/ k8s-deprecated/

# After validation period, delete
rm -rf k8s-deprecated/
```

### Phase 4: Update Documentation

#### Files that need updates:

1. **docs/local-deployment.md** (Lines 32, 74)
   - Remove: `kubectl apply -f ../k8s/5-song-ms.yaml`
   - Remove: `kubectl apply -f k8s/`
   - Keep only: Helm deployment instructions

2. **docs/kubernetes.md**
   - Remove: `kubectl apply -f k8s/` references
   - Remove: Section about kubectl manifest ordering
   - Add: Helm as primary deployment method
   - Add: Reference to Helm chart structure

3. **README.md** (if exists)
   - Update deployment instructions to use Helm only
   - Remove kubectl deployment examples

4. **CLAUDE.md** (if references exist)
   - Update deployment guidance to use Helm

---

## Documentation Updates (Detailed)

### docs/local-deployment.md

**Remove lines 32 and 74**:
```yaml
# OLD - DELETE
kubectl apply -f ../k8s/5-song-ms.yaml
kubectl apply -f k8s/
```

**Update deployment section (lines 68-78)**:
```markdown
# 3. Deploy to Kubernetes
helm install microservices-app k8s-helm-chart

# 4. Verify deployment
kubectl get all -n k8s-program
kubectl get pods -n k8s-program -w
```

**Remove "Option B: Using kubectl" section** (lines 73-74).

### docs/kubernetes.md

**Remove kubectl-specific content**:
- Remove: `kubectl apply -f k8s/` command examples
- Remove: Filename ordering explanation (no longer relevant)

**Add Helm-first approach**:
```markdown
## Deployment Method

This project uses **Helm** for Kubernetes deployments, providing:
- Template-based configurations
- Values-driven customization
- Version management
- Easy upgrades and rollbacks

See `k8s-helm-chart/` for Helm chart structure.

## Deployment Commands

**Install**:
```bash
helm install microservices-app k8s-helm-chart
```

**Upgrade**:
```bash
helm upgrade microservices-app k8s-helm-chart
```

**Uninstall**:
```bash
helm uninstall microservices-app
```

**Custom values**:
```bash
helm install microservices-app k8s-helm-chart -f k8s-helm-chart/values-custom.yaml
```
```

---

## Benefits of Cleanup

### 1. **Single Source of Truth**
- Only `k8s-helm-chart/` needs maintenance
- No confusion about which manifests to use
- Clear deployment path for new developers

### 2. **Reduced Maintenance Burden**
- Changes made once in Helm templates
- No need to sync kubectl and Helm versions
- Easier to track changes via Helm releases

### 3. **Prevents Deployment Confusion**
- Users won't accidentally deploy with kubectl
- Forces best practice (Helm deployment)
- Clearer learning path for project

### 4. **Repository Cleanliness**
- Less clutter in root directory
- Clear project structure
- Better organization

---

## Risk Assessment

### Low Risk ✅

**Reason**: Helm templates are complete and tested.

**Mitigation**:
1. Create backup before deletion
2. Verify Helm coverage in Phase 1
3. Test deployment after cleanup
4. Git history preserves original files

### Rollback Plan

If issues arise after deletion:

```bash
# Restore from git history
git checkout HEAD~1 -- k8s/

# Or restore from backup
tar -xzf archive/k8s-manifests-backup-*.tar.gz

# Or recreate from Helm templates
mkdir k8s-restored
helm template microservices-app k8s-helm-chart --output-dir k8s-restored/
```

---

## Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Phase 1: Verify Helm Coverage | 10 min | ⏸️ Not Started |
| Phase 2: Create Backup | 5 min | ⏸️ Not Started |
| Phase 3: Remove kubectl Manifests | 5 min | ⏸️ Not Started |
| Phase 4: Update Documentation | 20 min | ⏸️ Not Started |
| Testing & Validation | 15 min | ⏸️ Not Started |
| **Total** | **~55 min** | |

---

## Testing After Cleanup

### Validation Steps

1. **Verify Helm deployment works**:
   ```bash
   # Fresh install
   helm install microservices-app k8s-helm-chart
   
   # Check all resources created
   kubectl get all -n k8s-program
   ```

2. **Verify documentation accuracy**:
   ```bash
   # Follow updated deployment instructions
   # Ensure all commands work as documented
   ```

3. **Verify no broken references**:
   ```bash
   # Search for k8s/ references in codebase
   grep -r "k8s/" --include="*.md" docs/
   grep -r "kubectl apply -f k8s" . --include="*.md"
   ```

4. **Test full deployment lifecycle**:
   ```bash
   # Install
   helm install microservices-app k8s-helm-chart
   
   # Upgrade
   helm upgrade microservices-app k8s-helm-chart
   
   # Rollback
   helm rollback microservices-app
   
   # Uninstall
   helm uninstall microservices-app
   ```

---

## Success Criteria

- [ ] `k8s/` directory removed (YAML files deleted)
- [ ] Non-manifest files (notes.txt, PDF) relocated
- [ ] docs/local-deployment.md updated (no kubectl apply references)
- [ ] docs/kubernetes.md updated (Helm-first approach)
- [ ] All documentation references to kubectl deployment removed
- [ ] Helm deployment tested and working
- [ ] All pods running successfully after Helm deployment
- [ ] No broken documentation links or references
- [ ] Git commit with clear message: "Remove obsolete kubectl manifests, Helm is now primary deployment method"

---

## Execution Commands (Summary)

**Quick cleanup** (after verification):

```bash
# 1. Backup
tar -czf k8s-manifests-backup-$(date +%Y%m%d).tar.gz k8s/
mkdir -p archive && mv k8s-manifests-backup-*.tar.gz archive/

# 2. Move non-manifest files
mv k8s/notes.txt tasks/development-notes.txt
mv k8s/screenshots_for_module_1.pdf docs/

# 3. Remove kubectl manifests
rm k8s/*.yaml
rmdir k8s/

# 4. Test Helm deployment
helm uninstall microservices-app 2>/dev/null || true
helm install microservices-app k8s-helm-chart
kubectl get all -n k8s-program

# 5. Update documentation (manual step - see Phase 4)

# 6. Commit changes
git add -A
git commit -m "Remove obsolete kubectl manifests

- Deleted k8s/ directory YAML files (superseded by Helm)
- Moved k8s/notes.txt to tasks/development-notes.txt
- Moved k8s/screenshots_for_module_1.pdf to docs/
- Updated deployment documentation to use Helm only
- Helm is now the single source of truth for deployments"
```

---

## Notes

1. **Git History Preservation**: Original kubectl manifests remain in git history and can be restored if needed.

2. **Learning Materials**: If `screenshots_for_module_1.pdf` contains kubectl-specific instructions, consider updating or archiving it.

3. **Module Tasks**: Check if any task files (e.g., `tasks/module-1/`) reference the `k8s/` directory and update accordingly.

4. **CI/CD Impact**: If any CI/CD pipelines reference `k8s/` directory, update them to use Helm commands.

5. **Developer Onboarding**: Update any onboarding documentation to reflect Helm-only deployment approach.
