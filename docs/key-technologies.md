## Key Technologies

- **Spring Boot 3.1.4**: Core framework
- **Spring Cloud 2022.0.4**: Microservices stack (OpenFeign, Load Balancer); service discovery is delegated to Kubernetes DNS instead of Eureka
- **Spring Data JPA**: Database access
- **PostgreSQL**: Data persistence (deployed as in-cluster Pods backed by a `PersistentVolume` for the songs service)
- **Apache Tika 2.6.0**: MP3 metadata extraction (Resources Service only)
- **Lombok**: Boilerplate reduction
- **MapStruct 1.5.5**: DTO mapping
- **Docker**: Containerization (images `resources-image:v2`, `songs-image:v2` consumed by the cluster with `imagePullPolicy: IfNotPresent`)
- **Kubernetes**: Orchestration — `Namespace` (`k8s-program`), `Deployment`s (2 replicas per microservice), `NodePort` `Service`s (30080 / 30081), `ConfigMap`s, `Secret`s, and `PersistentVolume` / `PersistentVolumeClaim` for songs storage

