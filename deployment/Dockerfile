# Etapa de construcción
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY ./gradlew ./gradlew.bat /app/
COPY ./gradle /app/gradle
COPY ./settings.gradle ./build.gradle ./gradle.properties ./main.gradle /app/
COPY ./applications /app/applications
COPY ./domain /app/domain
COPY ./infrastructure /app/infrastructure

RUN chmod +x ./gradlew
RUN apk add --no-cache bash

RUN ./gradlew :app-service:bootJar -x test -x validateStructure && \
    ls -la applications/app-service/build/libs/ && \
    if [ ! -f applications/app-service/build/libs/turismo-back.jar ]; then echo "JAR no encontrado" && exit 1; fi

# Etapa de ejecución
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/applications/app-service/build/libs/turismo-back.jar app.jar

# Exponer el puerto configurado desde Railway (SERVER_PORT)
EXPOSE 8080

# Usar la variable JAVA_OPTS y otras variables de entorno en tiempo de ejecución
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
