# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy files required for the build
COPY ./gradlew ./gradlew.bat /app/
COPY ./gradle /app/gradle
COPY ./settings.gradle ./build.gradle ./gradle.properties ./main.gradle /app/
COPY ./applications /app/applications
COPY ./domain /app/domain
COPY ./infrastructure /app/infrastructure

# Normalize line endings for Linux containers and make wrapper executable
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew

# Build JAR
RUN ./gradlew :app-service:bootJar -x test -x validateStructure

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user (Hugging Face uses UID 1000)
RUN adduser -D -u 1000 hfuser
USER 1000

# Copy built artifact from builder stage
COPY --from=builder /app/applications/app-service/build/libs/turismo-back.jar app.jar

# Hugging Face Spaces expected port
EXPOSE 7860

# Default runtime vars
ENV SERVER_PORT=7860
ENV JAVA_OPTS="-Xmx1536m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]
