@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Kubernetes Deployment Script
echo ========================================
echo.

REM Validate YAML syntax
echo Step 1: Validating YAML syntax...
kubectl apply --dry-run=client -f k8s/
if errorlevel 1 (
    echo [ERROR] YAML validation failed
    exit /b 1
)
echo [OK] YAML validation passed
echo.

REM Delete all running resources
echo Step 2: Deleting running resources...
kubectl delete --ignore-not-found=true -f k8s/
if errorlevel 1 (
    echo [ERROR] Failed to delete resources
    exit /b 1
)
echo [OK] Resources deleted
echo.

REM Launch the resources
echo Step 3: Launching resources...
kubectl apply -f k8s/
if errorlevel 1 (
    echo [ERROR] Failed to apply resources
    exit /b 1
)
echo [OK] Resources launched
echo.

REM Perform end-to-end tests
echo Step 7: Running local deployment checks...
python local-deployment-check.py
if errorlevel 1 (
    echo [ERROR] local deployment checks failed
    exit /b 1
)
echo [OK] E2E local deployment checks passed
echo.

echo ========================================
echo [SUCCESS] Deployment completed successfully!
echo ========================================
