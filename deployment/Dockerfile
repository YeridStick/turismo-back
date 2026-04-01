# Etapa de construcción
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copiar archivos necesarios para el build
COPY ./gradlew ./gradlew.bat /app/
COPY ./gradle /app/gradle
COPY ./settings.gradle ./build.gradle ./gradle.properties ./main.gradle /app/
COPY ./applications /app/applications
COPY ./domain /app/domain
COPY ./infrastructure /app/infrastructure

RUN chmod +x ./gradlew
RUN apk add --no-cache bash

# Generar el JAR de la aplicación
RUN ./gradlew :app-service:bootJar -x test -x validateStructure

# Etapa de ejecución
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear un usuario no-root para seguridad (Hugging Face usa UID 1000)
RUN adduser -D -u 1000 hfuser
USER 1000

# Copiar el JAR desde la etapa de construcción
COPY --from=builder /app/applications/app-service/build/libs/turismo-back.jar app.jar

# Hugging Face Spaces requiere exponer el puerto 7860
EXPOSE 7860

# Configurar variables por defecto para el puerto y memoria
ENV SERVER_PORT=7860
ENV JAVA_OPTS="-Xmx1536m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# ENTRYPOINT para ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]
