FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario de ejecución para seguridad
RUN adduser -D -u 1000 appuser

# Copiar JAR ANTES de cambiar de usuario (root puede copiar)
COPY applications/app-service/build/libs/*.jar app.jar

# Dar permisos al usuario
RUN chown appuser:appuser app.jar

# Cambiar a usuario no-root
USER 1000

EXPOSE 7860

ENV SERVER_PORT=7860
ENV JAVA_OPTS="-Xms64m -Xmx160m -XX:MaxMetaspaceSize=80m -XX:ReservedCodeCacheSize=64m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxDirectMemorySize=24m -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=25 -Xss384k -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]