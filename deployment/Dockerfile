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

# ============================================
# Variables de entorno para configuración JVM
# Configurables para escalar desde 480MB hasta 2GB+
# ============================================

# Puerto del servidor
ENV SERVER_PORT=7860

# Configuración de Memoria Heap (ajustar según RAM del contenedor)
# Para 480MB RAM: JVM_HEAP_MAX=240m (default)
# Para 1GB RAM:   JVM_HEAP_MAX=512m
# Para 2GB RAM:   JVM_HEAP_MAX=1200m
ENV JVM_HEAP_MIN=${JVM_HEAP_MIN:-120m}
ENV JVM_HEAP_MAX=${JVM_HEAP_MAX:-240m}

# Metaspace (clases, típicamente 64-128m es suficiente)
ENV JVM_METASPACE_MAX=${JVM_METASPACE_MAX:-96m}

# Direct Memory (buffers de Netty/R2DBC)
# Para 480MB: 32m | Para 1GB: 64m | Para 2GB: 128m
ENV JVM_DIRECT_MEMORY=${JVM_DIRECT_MEMORY:-32m}

# Stack size por thread (256k es suficiente para WebFlux)
ENV JVM_STACK_SIZE=${JVM_STACK_SIZE:-256k}

# GC y optimizaciones
ENV JVM_GC_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=25 -XX:+OptimizeStringConcat"
ENV JVM_SAFETY_OPTS="-XX:+ExitOnOutOfMemoryError -XX:ReservedCodeCacheSize=32m"

# Netty optimizaciones
ENV NETTY_OPTS="-Dspring.netty.leak-detection=disabled -Dio.netty.allocator.type=pooled -Dio.netty.noPreferDirect=false"

# JAVA_OPTS construido dinámicamente
ENV JAVA_OPTS="-Xms${JVM_HEAP_MIN} -Xmx${JVM_HEAP_MAX} -XX:MaxMetaspaceSize=${JVM_METASPACE_MAX} -XX:MaxDirectMemorySize=${JVM_DIRECT_MEMORY} -Xss${JVM_STACK_SIZE} ${JVM_GC_OPTS} ${JVM_SAFETY_OPTS} ${NETTY_OPTS}"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]