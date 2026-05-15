### Data Flow

1. Client uploads MP3 file to Resources Service (`POST /resources/file`)
2. Resources Service stores the binary data in its PostgreSQL database
3. Resources Service extracts metadata using Apache Tika (`SongDataParserService`)
4. Resources Service sends metadata to Songs Service via Feign client (`POST /songs`)
5. Songs Service stores metadata in its separate PostgreSQL database
6. When deleting resources, Resources Service also deletes corresponding song metadata via Feign client

