FROM eclipse-temurin:21-jre

WORKDIR /app

# Local development / CI image. Production runs app.jar as a Windows service.
COPY target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
