# Module 3 Sub-Task 2: Helm Helpers and ConfigMap Labels Implementation Plan

## Overview
This plan details the implementation of Helm helper templates in `_helpers.tpl` to define reusable labels (current date and version) and apply them to ConfigMaps.

## Prerequisites
- Completed Module 3 Sub-Task 1 (Helm chart structure created)
- Understanding of Helm template functions and Go templates
- Familiarity with Kubernetes labels

---

## Step 1: Create Helper Templates in _helpers.tpl

### 1.1 Understanding _helpers.tpl
The `_helpers.tpl` file in Helm charts contains named template definitions that can be reused across multiple templates. These are Go template functions that:
- Start with `{{- define "template.name" -}}`
- End with `{{- end }}`
- Are invoked using `{{ include "template.name" . }}`
- Help maintain DRY (Don't Repeat Yourself) principle

**Current _helpers.tpl location**: `k8s-helm-chart/templates/_helpers.tpl`

### 1.2 Define Current Date Label Helper

Add a new helper template to generate the current date label.

**Template to add**:
```yaml
{{/*
Current deployment date label
*/}}
{{- define "k8s-helm-chart.deploymentDate" -}}
{{- now | date "2006-01-02" }}
{{- end }}
```

**Explanation**:
- `now`: Helm built-in function that returns the current timestamp
- `date "2006-01-02"`: Formats the timestamp as YYYY-MM-DD (ISO 8601 format)
- The format string uses Go's reference time: Mon Jan 2 15:04:05 MST 2006
- Result example: `2026-05-20`

**Alternative date formats**:
- `"2006-01-02T15:04:05Z"` - ISO 8601 with time: `2026-05-20T14:30:45Z`
- `"20060102"` - Compact format: `20260520`
- `"2006-01-02-150405"` - Date and time: `2026-05-20-143045`

**Recommended format**: `"2006-01-02"` (date only, human-readable)

### 1.3 Define Version Label Helper

Add a helper template to retrieve the chart version.

**Template to add**:
```yaml
{{/*
Chart version label
*/}}
{{- define "k8s-helm-chart.version" -}}
{{- .Chart.Version }}
{{- end }}
```

**Explanation**:
- `.Chart.Version`: References the `version` field in `Chart.yaml`
- Current value: `0.1.0`
- Automatically updates when Chart.yaml version changes
- No need to manually update in multiple places

**Alternative: Use AppVersion**:
```yaml
{{- define "k8s-helm-chart.appVersion" -}}
{{- .Chart.AppVersion }}
{{- end }}
```
- `.Chart.AppVersion`: References the `appVersion` field in `Chart.yaml`
- Current value: `"1.0"`
- Use this if you want application version instead of chart version

**Decision**: Use `.Chart.Version` as specified in requirements (chart version, not app version)

### 1.4 Define Combined Labels Helper

Create a helper that combines deployment date and version labels for easy reuse.

**Template to add**:
```yaml
{{/*
Custom labels for ConfigMaps
*/}}
{{- define "k8s-helm-chart.configMapLabels" -}}
deployment-date: {{ include "k8s-helm-chart.deploymentDate" . | quote }}
version: {{ include "k8s-helm-chart.version" . | quote }}
{{- end }}
```

**Explanation**:
- Combines both labels in a single template
- `| quote`: Ensures values are properly quoted (important for date strings)
- Can be included in any resource's `metadata.labels` section
- Provides consistent labeling across all ConfigMaps

### 1.5 Complete _helpers.tpl Structure

After adding the new helpers, the `_helpers.tpl` file should contain:

**Existing helpers** (keep these):
- `k8s-helm-chart.name`
- `k8s-helm-chart.fullname`
- `k8s-helm-chart.chart`
- `k8s-helm-chart.labels`
- `k8s-helm-chart.selectorLabels`
- `k8s-helm-chart.serviceAccountName`

**New helpers** (add these):
- `k8s-helm-chart.deploymentDate`
- `k8s-helm-chart.version`
- `k8s-helm-chart.configMapLabels`

**Location in file**: Add new helpers at the end of `_helpers.tpl` after existing templates

---

## Step 2: Apply Labels to ConfigMaps

### 2.1 Identify ConfigMap Files

ConfigMaps that need labels added:
1. `1.6-resources-config.yaml` - Resources MS configuration
2. `1.7-songs-config.yaml` - Songs MS configuration
3. `1.8-database-config.yaml` - Database configuration
4. `1.9.1-resources-db-init-config.yaml` - Resources DB init scripts
5. `1.9.2-songs-db-init-config.yaml` - Songs DB init scripts

**Total**: 5 ConfigMap files

### 2.2 Current ConfigMap Structure

Example from `1.6-resources-config.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resources-ms-config
  namespace: {{ .Values.namespace }}
data:
  RESOURCES_DB_URL: "resources-db"
  RESOURCES_DB_PORT: "5432"
  DATABASE_NAME: "resources_db"
  SONGS_MS_URL: "songs-ms"
  SONGS_MS_PORT: "8081"
  RESOURCES_MS_PORT: "8080"
```

**Missing**: `labels` section in `metadata`

### 2.3 Add Labels to ConfigMaps

Add the `labels` section to each ConfigMap's metadata.

**Updated structure**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resources-ms-config
  namespace: {{ .Values.namespace }}
  labels:
    {{- include "k8s-helm-chart.configMapLabels" . | nindent 4 }}
data:
  RESOURCES_DB_URL: "resources-db"
  # ... rest of data fields
```

**Explanation**:
- `labels:` - Kubernetes metadata labels section
- `{{- include "k8s-helm-chart.configMapLabels" . | nindent 4 }}` - Includes the helper template
- `| nindent 4` - Adds proper indentation (4 spaces for labels under metadata)
- `-` in `{{-` removes preceding whitespace
- `.` passes the current context to the helper template

**Rendered output** (what Kubernetes will see):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resources-ms-config
  namespace: k8s-program
  labels:
    deployment-date: "2026-05-20"
    version: "0.1.0"
data:
  # ... data fields
```

### 2.4 Apply to All ConfigMaps

Repeat the label addition for all 5 ConfigMap files:

1. **1.6-resources-config.yaml**:
   - Add labels after `namespace: {{ .Values.namespace }}`
   - Before `data:` section

2. **1.7-songs-config.yaml**:
   - Add labels after `namespace: {{ .Values.namespace }}`
   - Before `data:` section

3. **1.8-database-config.yaml**:
   - Add labels after `namespace: {{ .Values.namespace }}`
   - Before `data:` section

4. **1.9.1-resources-db-init-config.yaml**:
   - Add labels after `namespace: {{ .Values.namespace }}`
   - Before `data:` section (contains SQL init script)

5. **1.9.2-songs-db-init-config.yaml**:
   - Add labels after `namespace: {{ .Values.namespace }}`
   - Before `data:` section (contains SQL init script)

**Consistency**: All ConfigMaps should have identical label structure using the same helper

---

## Step 3: Validate and Test

### 3.1 Validate Helm Template Syntax

Check for template syntax errors:

```bash
helm lint k8s-helm-chart
```

Expected output:
```
==> Linting k8s-helm-chart
[INFO] Chart.yaml: icon is recommended

1 chart(s) linted, 0 chart(s) failed
```

**No errors**: Templates are syntactically correct

### 3.2 Render Templates (Dry Run)

Preview generated manifests to verify label rendering:

```bash
helm template microservices-app k8s-helm-chart --debug > rendered-manifests.yaml
```

**Check rendered ConfigMaps**:
```bash
grep -A 10 "kind: ConfigMap" rendered-manifests.yaml
```

Expected output should show:
```yaml
kind: ConfigMap
metadata:
  name: resources-ms-config
  namespace: k8s-program
  labels:
    deployment-date: "2026-05-20"
    version: "0.1.0"
```

### 3.3 Verify Date Format

Check that the date is correctly formatted:

```bash
grep "deployment-date:" rendered-manifests.yaml
```

Expected output (5 occurrences, one per ConfigMap):
```
    deployment-date: "2026-05-20"
    deployment-date: "2026-05-20"
    deployment-date: "2026-05-20"
    deployment-date: "2026-05-20"
    deployment-date: "2026-05-20"
```

### 3.4 Verify Version Label

Check that the version matches Chart.yaml:

```bash
grep "version:" rendered-manifests.yaml | grep -v "apiVersion"
```

Expected output (5 occurrences):
```
    version: "0.1.0"
    version: "0.1.0"
    version: "0.1.0"
    version: "0.1.0"
    version: "0.1.0"
```

### 3.5 Deploy and Verify Labels in Cluster

If previous deployment exists, uninstall first:
```bash
helm uninstall microservices-app
```

Install with new labels:
```bash
helm install microservices-app k8s-helm-chart
```

Verify labels are applied in the cluster:

```bash
# Check labels on all ConfigMaps
kubectl get configmap -n k8s-program --show-labels

# Describe specific ConfigMap to see labels
kubectl describe configmap resources-ms-config -n k8s-program

# Query by label selector
kubectl get configmap -n k8s-program -l version=0.1.0
kubectl get configmap -n k8s-program -l deployment-date
```

Expected output from `--show-labels`:
```
NAME                        DATA   AGE   LABELS
database-config             2      30s   deployment-date=2026-05-20,version=0.1.0
resources-db-init-config    1      30s   deployment-date=2026-05-20,version=0.1.0
resources-ms-config         6      30s   deployment-date=2026-05-20,version=0.1.0
songs-db-init-config        1      30s   deployment-date=2026-05-20,version=0.1.0
songs-ms-config             2      30s   deployment-date=2026-05-20,version=0.1.0
```

### 3.6 Test Label Queries

Verify labels can be used for filtering:

```bash
# Get all ConfigMaps deployed on specific date
kubectl get configmap -n k8s-program -l deployment-date=2026-05-20

# Get all ConfigMaps with specific version
kubectl get configmap -n k8s-program -l version=0.1.0

# Combine multiple label selectors
kubectl get configmap -n k8s-program -l version=0.1.0,deployment-date=2026-05-20
```

All queries should return the 5 ConfigMaps.

---

## Step 4: Test Version Changes

### 4.1 Update Chart Version

Test that version label updates when Chart.yaml changes:

```bash
# Edit Chart.yaml
# Change: version: 0.1.0
# To:     version: 0.2.0
```

### 4.2 Upgrade Deployment

```bash
helm upgrade microservices-app k8s-helm-chart
```

### 4.3 Verify Updated Labels

```bash
kubectl get configmap -n k8s-program --show-labels
```

Expected: Version label should now show `version=0.2.0`

### 4.4 Revert Version (Optional)

Restore original version:
```bash
# Edit Chart.yaml back to version: 0.1.0
helm upgrade microservices-app k8s-helm-chart
```

---

## Success Criteria

### Step 1 Completion
- [ ] `_helpers.tpl` contains `k8s-helm-chart.deploymentDate` helper
- [ ] `_helpers.tpl` contains `k8s-helm-chart.version` helper
- [ ] `_helpers.tpl` contains `k8s-helm-chart.configMapLabels` helper
- [ ] Date helper uses `now | date "2006-01-02"` format
- [ ] Version helper uses `.Chart.Version`
- [ ] Chart passes `helm lint`

### Step 2 Completion
- [ ] All 5 ConfigMap files have labels section added
- [ ] Labels section uses `{{- include "k8s-helm-chart.configMapLabels" . | nindent 4 }}`
- [ ] Labels are placed in `metadata` section
- [ ] Labels appear before `data` section
- [ ] Indentation is correct (4 spaces)

### Step 3 Completion
- [ ] `helm template` renders without errors
- [ ] Rendered manifests show `deployment-date` label with correct date format
- [ ] Rendered manifests show `version` label with correct version from Chart.yaml
- [ ] All 5 ConfigMaps have both labels
- [ ] Deployed ConfigMaps show labels when queried with `kubectl get`
- [ ] Label selectors work to filter ConfigMaps

### Step 4 Completion
- [ ] Changing Chart.yaml version updates the version label
- [ ] `helm upgrade` applies the new version label
- [ ] Old and new versions are distinguishable via labels

---

## Troubleshooting

### Template Rendering Errors

**Error**: `function "now" not defined`
- **Cause**: Older Helm version (< 3.0)
- **Solution**: Update Helm to 3.x or use `{{ .Release.Time }}` instead

**Error**: `error calling include: template: no template`
- **Cause**: Helper template name mismatch
- **Solution**: Ensure helper name in `define` matches name in `include`

### Label Format Issues

**Issue**: Date appears as timestamp instead of formatted date
- **Cause**: Missing `| date` filter
- **Solution**: Ensure `{{ now | date "2006-01-02" }}` format is used

**Issue**: Labels not properly quoted in YAML
- **Cause**: Missing `| quote` filter
- **Solution**: Add `| quote` in helper template or during inclusion

### Indentation Problems

**Error**: YAML parse error during deployment
- **Cause**: Incorrect indentation in `nindent`
- **Solution**: Use `| nindent 4` for labels under metadata (4 spaces)

### Labels Not Appearing

**Issue**: ConfigMaps deployed but labels missing
- **Cause**: Template include syntax incorrect
- **Solution**: Verify syntax: `{{- include "k8s-helm-chart.configMapLabels" . | nindent 4 }}`

**Issue**: Only some ConfigMaps have labels
- **Cause**: Forgot to add labels to all ConfigMap files
- **Solution**: Verify all 5 ConfigMap files have been updated

---

## Key Implementation Notes

1. **Date Immutability**: The `deployment-date` label captures the date at template rendering time (during `helm install/upgrade`), not the date the resource was created in Kubernetes

2. **Version Source**: The `version` label comes from `Chart.yaml`'s `version` field (chart version), not `appVersion` (application version)

3. **Label Naming**: Use lowercase with hyphens (kebab-case) for label keys, as per Kubernetes conventions

4. **Quote Values**: Always quote label values to avoid YAML parsing issues with special characters

5. **Indentation**: Use `nindent 4` for labels under metadata to maintain correct YAML structure

6. **Reusability**: The `configMapLabels` helper can be extended to other resources (Deployments, Services) by creating similar helpers

7. **Date Format**: ISO 8601 date format (YYYY-MM-DD) is recommended for human readability and sortability

8. **Helper Location**: All helpers should be defined in `_helpers.tpl` to maintain organization

9. **Context Passing**: Always pass `.` to `include` to ensure helpers have access to Chart, Release, and Values

10. **Testing**: Always use `helm template` before `helm install` to preview generated manifests

---

## Additional Helm Template Functions

Useful functions for extending labels in the future:

```yaml
# Release name
{{ .Release.Name }}

# Release namespace
{{ .Release.Namespace }}

# Release timestamp
{{ .Release.Time }}

# Chart name
{{ .Chart.Name }}

# Lowercase conversion
{{ .Chart.Name | lower }}

# Replace characters
{{ .Chart.Version | replace "." "-" }}

# Trim whitespace
{{ .Values.someValue | trim }}

# Default value
{{ .Values.optionalValue | default "default-value" }}
```

---

## Example: Extended Labels Helper

For reference, here's how to create a more comprehensive labels helper:

```yaml
{{/*
Extended ConfigMap labels with additional metadata
*/}}
{{- define "k8s-helm-chart.extendedConfigMapLabels" -}}
deployment-date: {{ include "k8s-helm-chart.deploymentDate" . | quote }}
version: {{ include "k8s-helm-chart.version" . | quote }}
release: {{ .Release.Name | quote }}
chart: {{ .Chart.Name | quote }}
managed-by: {{ .Release.Service | quote }}
{{- end }}
```

**Note**: For this task, only implement `deployment-date` and `version` as specified in requirements.
