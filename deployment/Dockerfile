# =========
# Etapa 1: Build
# =========
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

RUN apk add --no-cache bash sed

# Copia selectiva para aprovechar mejor la caché
COPY ./gradlew ./gradlew.bat /app/
COPY ./gradle /app/gradle
COPY ./settings.gradle ./build.gradle ./gradle.properties ./main.gradle /app/
COPY ./applications /app/applications
COPY ./domain /app/domain
COPY ./infrastructure /app/infrastructure

# Normaliza el wrapper para Linux
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew

# Build del jar
RUN ./gradlew clean :app-service:bootJar -x test -x validateStructure --no-daemon

# =========
# Etapa 2: Runtime
# =========
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN adduser -D -u 1000 appuser

COPY --from=builder /app/applications/app-service/build/libs/turismo-back.jar app.jar

RUN chown appuser:appuser app.jar

USER 1000

EXPOSE 7860

ENV SERVER_PORT=7860
ENV JAVA_OPTS="-Xms64m -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxDirectMemorySize=16m -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=25 -Xss256k -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]