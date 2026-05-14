# Smaller OS footprint using JRE
FROM amazoncorretto:21
WORKDIR /app

ADD build/libs/*.jar /app/app.jar

CMD ["java", "-jar", "app.jar"]