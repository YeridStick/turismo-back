## Why

Los sitios turísticos actualmente exponen listas de URLs multimedia, pero el backend no ofrece carga, validación ni almacenamiento controlado de imágenes, videos o modelos 3D. Se necesita un flujo seguro y reactivo que almacene los objetos en S3 y persista en PostgreSQL únicamente la URL o clave resultante.

## What Changes

- Añadir carga autenticada y autorizada de imágenes, videos y modelos GLB asociados a un `siteId`.
- Validar tipo, extensión, contenido real y tamaño; redimensionar imágenes antes de almacenarlas cuando aplique.
- Generar claves S3 seguras y no controlables por el cliente, organizadas por sitio y tipo de contenido.
- Persistir en PostgreSQL solo la URL o clave del objeto, sin guardar binarios.
- Definir compensación/eliminación segura si falla PostgreSQL después de cargar en S3.
- Mantener el procesamiento compatible con WebFlux sin bloquear el event loop y documentar límites, autorización, errores y configuración requerida.

## Capabilities

### New Capabilities

- `site-media-storage`: Contrato de carga, validación, procesamiento, almacenamiento S3, persistencia, autorización y compensación del contenido multimedia de sitios.

### Modified Capabilities

- Ninguna. No existen especificaciones principales previas en `openspec/specs/`.

## Impact

- Nuevos puertos, casos de uso, adaptadores S3 y persistencia R2DBC/PostgreSQL.
- Handlers, DTOs y rutas WebFlux para multipart de imágenes, videos y GLB.
- Modelo y esquema de datos de `places`/sitios para referencias multimedia, si el diseño confirma que las columnas actuales no son suficientes.
- Dependencia oficial del SDK AWS S3, configuración de bucket, región, prefijo y límites de carga.
- Pruebas de validación, autorización, compensación, errores y backpressure/reactividad.
- No incluye cambios de Terraform ni inicialización de `turismo-infra`; cualquier cambio de infraestructura se propondrá separadamente.
