### Service Communication

- Resources Service communicates with Songs Service via OpenFeign client
- Service discovery is optional and controlled by `ENABLE_EUREKA_CLIENT` environment variable (defaults to false)
- When Eureka is disabled, services communicate via direct URLs configured through `songs.url` and `songs.port` properties
- The Feign client (`SongServiceClient`) in Resources Service calls Songs Service endpoints to save/delete song metadata

