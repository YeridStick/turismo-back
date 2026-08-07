## 1. Contrato y configuración

- [x] 1.1 Confirmar el contrato multipart, categorías, límites por tipo, política de URL pública/firmada, retención y compatibilidad con `imageUrls`/`model3dUrls` existentes.
- [x] 1.2 Añadir configuración externa para bucket, región, prefijo, credenciales, límites, tipos permitidos y habilitación de la característica sin secretos en el repositorio.
- [x] 1.3 Añadir la dependencia y el puerto reactivo de almacenamiento con una implementación compatible con WebFlux.

## 2. Modelo y persistencia

- [x] 2.1 Diseñar y agregar la migración PostgreSQL de referencias multimedia por sitio, incluyendo categoría, clave/URL, metadatos, checksum y timestamps, sin binarios.
- [x] 2.2 Implementar modelo, gateway y adaptador R2DBC para crear, consultar y eliminar referencias de forma idempotente.
- [ ] 2.3 Definir la compatibilidad de lectura con los arrays multimedia actuales y probar los casos de sitio sin media y con varias referencias.

## 3. Validación, procesamiento y S3

- [x] 3.1 Implementar autorización de propietario/administrador y generación de claves exclusivamente con `siteId`, categoría y nombre seguro generado por servidor.
- [x] 3.2 Implementar límites de tamaño/cantidad, allowlist, detección de contenido real y validación específica de imagen, video y GLB.
- [x] 3.3 Implementar redimensionamiento/normalización de imágenes y extracción acotada de metadatos en scheduler bounded, sin bloquear el event loop.
- [x] 3.4 Implementar subida reactiva a S3, manejo de errores y devolución de referencia sin exponer credenciales ni rutas controlables por el cliente.
- [x] 3.5 Implementar compensación de S3 cuando falle PostgreSQL y señal segura de reconciliación cuando falle también la eliminación.

## 4. API y pruebas

- [x] 4.1 Añadir DTOs, handler y rutas WebFlux para carga multipart, respuestas de éxito y errores de validación/autorización.
- [x] 4.2 Añadir pruebas para imagen, video, GLB, formato/tamaño/contenido inválido, clave por `siteId`, persistencia de URL/clave y usuario no autorizado.
- [x] 4.3 Añadir pruebas simuladas de fallo S3, fallo PostgreSQL posterior y compensación.
- [x] 4.4 Añadir pruebas de backpressure/concurrencia que demuestren que el procesamiento CPU-bound no bloquea el event loop.

## 5. Infraestructura y validación

- [x] 5.1 Documentar permisos mínimos del bucket, CORS/política de acceso, lifecycle y variables de despliegue; proponer cualquier cambio Terraform en `turismo-infra` por separado.
- [x] 5.2 Ejecutar pruebas Gradle de módulos afectados y validar los contratos de API y migración.
- [x] 5.3 Comparar la implementación con todos los escenarios de `specs/site-media-storage/spec.md` y ejecutar validación OpenSpec antes de archivar.
