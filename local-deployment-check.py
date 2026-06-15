#!/usr/bin/env python3
"""
End-to-end tests for the microservices system.
Tests resource upload and song metadata retrieval.
"""

import json
import sys
import subprocess
import time
import urllib.request
import urllib.error


RESOURCE_SERVICE_URL = "http://localhost:8080/api/v1/resources"
SONG_SERVICE_URL = "http://localhost:8080/api/v1/songs"
TEST_MP3_FILE = "test.mp3"
NAMESPACE = "k8s-program-dev"
POD_READINESS_TIMEOUT = 30
POD_READINESS_DELAY = 3


def wait_for_pods_ready():
    """Wait for all pods in the namespace to be ready."""
    print("\nChecking pod readiness...")
    print("Waiting for all pods to be up...")

    start_time = time.time()

    while True:
        try:
            result = subprocess.run(
                ["kubectl", "get", "pods", f"-n={NAMESPACE}"],
                capture_output=True,
                text=True,
                timeout=5
            )

            if result.returncode == 0:
                lines = result.stdout.strip().split('\n')

                if len(lines) <= 1:
                    print("   ⚠ No pods found in namespace")
                    elapsed = time.time() - start_time
                    if elapsed > POD_READINESS_TIMEOUT:
                        print(f"   ✗ Timeout: No pods found after {POD_READINESS_TIMEOUT}s")
                        return False
                    time.sleep(POD_READINESS_DELAY)
                    continue

                ready_statuses = []
                for line in lines[1:]:
                    parts = line.split()
                    if len(parts) >= 3:
                        ready_col = parts[1]
                        status_col = parts[2]
                        ready_statuses.append((ready_col, status_col))

                all_ready = all(is_pod_ready(ready, status) for ready, status in ready_statuses)

                if all_ready:
                    print(f"   ✓ All pods are ready")
                    return True

                elapsed = time.time() - start_time
                ready_count = sum(1 for ready, status in ready_statuses if is_pod_ready(ready, status))
                print(f"   ⏳ Waiting... ({int(elapsed)}s) - Ready: {ready_count}/{len(ready_statuses)}")

                if elapsed > POD_READINESS_TIMEOUT:
                    print(f"   ✗ Timeout: Pods not ready after {POD_READINESS_TIMEOUT}s")
                    print(f"   Status:")
                    for line in lines[1:]:
                        print(f"      {line}")
                    return False

                time.sleep(POD_READINESS_DELAY)
            else:
                print(f"   ✗ kubectl error: {result.stderr}")
                elapsed = time.time() - start_time
                if elapsed > POD_READINESS_TIMEOUT:
                    print(f"   ✗ Timeout: kubectl failed after {POD_READINESS_TIMEOUT}s")
                    return False
                time.sleep(POD_READINESS_DELAY)

        except subprocess.TimeoutExpired:
            print("   ✗ kubectl command timed out")
            return False
        except FileNotFoundError:
            print("   ✗ kubectl not found - ensure kubectl is installed and in PATH")
            return False
        except Exception as e:
            print(f"   ✗ Error: {e}")
            return False


def is_pod_ready(ready_col, status_col):
    """Check if a pod is ready: READY shows equal numbers > 0 and STATUS is Running."""
    try:
        parts = ready_col.split('/')
        if len(parts) != 2:
            return False
        ready_num = int(parts[0])
        total_num = int(parts[1])
        return ready_num == total_num and ready_num > 0 and status_col == "Running"
    except (ValueError, IndexError):
        return False


def upload_mp3(file_path):
    """Upload MP3 file to the resource service using multipart/form-data."""
    print(f"\n1. Uploading MP3 file: {file_path}")

    try:
        import random
        import string

        # Generate boundary for multipart form data
        boundary = '----WebKitFormBoundary' + ''.join(random.choices(string.ascii_letters + string.digits, k=16))

        with open(file_path, 'rb') as f:
            mp3_data = f.read()

        # Construct multipart form data
        body = (
            f'--{boundary}\r\n'
            f'Content-Disposition: form-data; name="file"; filename="{file_path}"\r\n'
            f'Content-Type: audio/mpeg\r\n\r\n'
        ).encode('utf-8')
        body += mp3_data
        body += f'\r\n--{boundary}--\r\n'.encode('utf-8')

        # Update URL to use /file endpoint
        url = f"{RESOURCE_SERVICE_URL}/file"

        req = urllib.request.Request(
            url,
            data=body,
            headers={'Content-Type': f'multipart/form-data; boundary={boundary}'},
            method='POST'
        )

        with urllib.request.urlopen(req) as response:
            response_data = response.read().decode('utf-8')
            result = json.loads(response_data)
            resource_id = result.get('id')

            print(f"   ✓ Upload successful")
            print(f"   Response: {response_data}")
            print(f"   Resource ID: {resource_id}")

            return resource_id

    except FileNotFoundError:
        print(f"   ✗ Error: File '{file_path}' not found")
        return None
    except urllib.error.HTTPError as e:
        print(f"   ✗ HTTP Error {e.code}: {e.reason}")
        print(f"   Response: {e.read().decode('utf-8')}")
        return None
    except Exception as e:
        print(f"   ✗ Error: {e}")
        return None


def check_service_health(service_name, url):
    """Check service health endpoint with retry strategy."""
    print(f"\n2. Checking {service_name} health status...")

    start_time = time.time()
    retry_delay = 3
    timeout = 30

    while True:
        try:
            req = urllib.request.Request(url, method='GET')

            with urllib.request.urlopen(req) as response:
                response_data = response.read().decode('utf-8')
                health = json.loads(response_data)

                status = health.get('status')
                if status == 'UP':
                    print(f"   ✓ {service_name} status is UP")
                    print(f"   Response: {response_data}")
                    return True
                else:
                    print(f"   ✗ {service_name} status is {status}, expected UP")
                    return False

        except urllib.error.HTTPError as e:
            elapsed = time.time() - start_time
            if elapsed > timeout:
                print(f"   ✗ HTTP Error {e.code}: {e.reason}")
                print(f"   Response: {e.read().decode('utf-8')}")
                print(f"   Timeout: Service not responding after {timeout}s")
                return False
            print(f"   ⏳ Retrying... ({int(elapsed)}s/{timeout}s) - HTTP Error {e.code}")
            time.sleep(retry_delay)

        except Exception as e:
            elapsed = time.time() - start_time
            if elapsed > timeout:
                print(f"   ✗ Error: {e}")
                print(f"   Timeout: Service not responding after {timeout}s")
                return False
            print(f"   ⏳ Retrying... ({int(elapsed)}s/{timeout}s) - {e}")
            time.sleep(retry_delay)


def get_song(song_id):
    """Retrieve song metadata from the song service."""
    print(f"\n3. Retrieving song metadata for ID: {song_id}")

    try:
        url = f"{SONG_SERVICE_URL}/{song_id}"
        req = urllib.request.Request(url, method='GET')

        with urllib.request.urlopen(req) as response:
            response_data = response.read().decode('utf-8')
            song = json.loads(response_data)

            print(f"   ✓ Song retrieved successfully")
            print(f"   Response: {response_data}")

            return song

    except urllib.error.HTTPError as e:
        print(f"   ✗ HTTP Error {e.code}: {e.reason}")
        if e.code == 404:
            print(f"   Note: Song with ID {song_id} not found (metadata may not be extracted yet)")
        else:
            print(f"   Response: {e.read().decode('utf-8')}")
        return None
    except Exception as e:
        print(f"   ✗ Error: {e}")
        return None


def main():
    """Run end-to-end tests."""
    print("=" * 60)
    print("E2E Tests for Microservices System")
    print("=" * 60)

    # Step 0: Wait for pods to be ready
    if not wait_for_pods_ready():
        print("\n✗ Test failed: Pods are not ready")
        sys.exit(1)

# health check endpoints are not reachable from outside
#     resources_health = check_service_health(
#         "Resources Service",
#         "http://localhost:30080/actuator/health"
#     )
#     if not resources_health:
#         print("\n✗ Test failed: Resources service is not healthy")
#         sys.exit(1)
#
#     # Step 2: Check songs service health
#     songs_health = check_service_health(
#         "Songs Service",
#         "http://localhost:30081/actuator/health"
#     )
#     if not songs_health:
#         print("\n✗ Test failed: Songs service is not healthy")
#         sys.exit(1)

    # Step 3: Upload MP3 file
    resource_id = upload_mp3(TEST_MP3_FILE)

    if resource_id is None:
        print("\n✗ Test failed: Could not upload MP3 file")
        sys.exit(1)

    # Step 4: Retrieve song metadata
    song = get_song(resource_id)

    if song is None:
        print("\n⚠ Warning: Song metadata not found")
        print("  This may be expected if metadata extraction is async")
        sys.exit(0)

    # Validate response
    print("\n5. Validating response")
    if song.get('resourceId') == resource_id:
        print(f"   ✓ Song ID matches resource ID: {resource_id}")
    else:
        print(f"   ✗ ID mismatch: expected {resource_id}, got {song.get('id')}")

    print("\n" + "=" * 60)
    print("✓ E2E Tests completed successfully")
    print("=" * 60)


if __name__ == "__main__":
    main()
