# ============ Etapa de build ============
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Gradle wrapper y configs
COPY ./gradlew ./gradlew.bat /app/
COPY ./gradle /app/gradle
COPY ./settings.gradle ./build.gradle ./gradle.properties /app/
# COPY ./main.gradle /app/   # (descomenta si lo usas)

# Código
COPY ./applications /app/applications
COPY ./domain /app/domain
COPY ./infrastructure /app/infrastructure

RUN chmod +x ./gradlew && apk add --no-cache bash

# Construye el bootJar del servicio y evita validateStructure/test
# Si tu módulo está como :applications:app-service usa esa tarea.
RUN ./gradlew :applications:app-service:bootJar -x test -x check -x validateStructure -Pproduction

# Copia el jar al nombre esperado
RUN cp applications/app-service/build/libs/*jar /app/turismo-back.jar

# ============ Etapa de runtime ============
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crea usuario no-root (tu Dockerfile usaba 'appuser' pero no lo creaba)
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /app/turismo-back.jar app.jar

EXPOSE 8080
# Nota: -XX:+UseContainerSupport ya no es necesario en Java 21 (y puede advertir/ser obsoleto)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
