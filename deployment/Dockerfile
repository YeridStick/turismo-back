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
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${SERVER_PORT} -jar app.jar"]