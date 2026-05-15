## API Endpoints

### Resources Service (port 8080)
- `POST /resources/file` - Upload MP3 file (multipart/form-data)
- `POST /resources` - Upload MP3 binary (Content-Type: audio/mpeg)
- `GET /resources/{id}` - Download MP3 file
- `DELETE /resources?ids=1,2,3` - Delete resources by IDs (max 200 comma-separated)

### Songs Service (port 8081)
- `GET /songs/{id}` - Get song metadata by resource ID
- `POST /songs` - Save song metadata (JSON)
- `DELETE /songs?ids=1,2,3` - Delete songs by song IDs
- `DELETE /songs/by-resource-id?ids=1,2,3` - Delete songs by resource IDs (used by Resources Service)

