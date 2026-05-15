## Architecture

The system consists of three services:

1. **Eureka Server** (port 8761): Netflix Eureka service registry for service discovery
2. **Resources Service** (port 8080): Handles MP3 file storage and extraction of audio metadata using Apache Tika
3. **Songs Service** (port 8081): Stores and manages song metadata (artist, album, title, etc.)

