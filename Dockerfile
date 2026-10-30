# Spring Boot 1.5 needs Java 8 at runtime (same as java.version in pom.xml)
FROM eclipse-temurin:8-jre-alpine

# Run the application as a non-root user
RUN adduser -D -u 10001 appuser

WORKDIR /usr/app

# Jar built by `mvn package` (target/shopping-cart-<version>.jar)
COPY target/shopping-cart-*.jar app.jar

USER 10001

# Port of the application (server.port in application.properties)
EXPOSE 8070

ENTRYPOINT ["java", "-jar", "/usr/app/app.jar"]
