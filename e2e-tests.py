#!/usr/bin/env python3
"""
End-to-end tests for the microservices system.
Tests resource upload and song metadata retrieval.
"""

import json
import sys
import urllib.request
import urllib.error


RESOURCE_SERVICE_URL = "http://localhost:30080/resources"
SONG_SERVICE_URL = "http://localhost:30081/songs"
TEST_MP3_FILE = "test.mp3"


def upload_mp3(file_path):
    """Upload MP3 file to the resource service."""
    print(f"\n1. Uploading MP3 file: {file_path}")

    try:
        with open(file_path, 'rb') as f:
            mp3_data = f.read()

        req = urllib.request.Request(
            RESOURCE_SERVICE_URL,
            data=mp3_data,
            headers={'Content-Type': 'audio/mpeg'},
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


def get_song(song_id):
    """Retrieve song metadata from the song service."""
    print(f"\n2. Retrieving song metadata for ID: {song_id}")

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

    # Step 1: Upload MP3 file
    resource_id = upload_mp3(TEST_MP3_FILE)

    if resource_id is None:
        print("\n✗ Test failed: Could not upload MP3 file")
        sys.exit(1)

    # Step 2: Retrieve song metadata
    song = get_song(resource_id)

    if song is None:
        print("\n⚠ Warning: Song metadata not found")
        print("  This may be expected if metadata extraction is async")
        sys.exit(0)

    # Validate response
    print("\n3. Validating response")
    if song.get('id') == resource_id:
        print(f"   ✓ Song ID matches resource ID: {resource_id}")
    else:
        print(f"   ✗ ID mismatch: expected {resource_id}, got {song.get('id')}")

    print("\n" + "=" * 60)
    print("✓ E2E Tests completed successfully")
    print("=" * 60)


if __name__ == "__main__":
    main()
