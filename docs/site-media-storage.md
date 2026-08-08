# Integración frontend: multimedia de lugares

Esta guía describe el contrato vigente del backend para cargar, consultar y
eliminar imágenes, videos y modelos GLB asociados a un lugar turístico.

> La alerta mostrada en la aplicación (“el backend actual aún no publica el
> endpoint...”) quedó desactualizada. El endpoint ya existe. El frontend debe
> retirar esa alerta y conectar el formulario con las rutas descritas aquí.

## Flujo recomendado

1. Crear el lugar con `POST /api/places` y obtener su `id` (`siteId`), o usar
   el `id` de un lugar existente.
2. Para cada archivo seleccionado, enviar un `POST` multipart a
   `/api/places/{siteId}/media`.
3. Guardar en el estado de la pantalla el objeto devuelto por cada carga.
4. Al abrir o editar el lugar, consultar `GET /api/places/{siteId}/media`.
5. Para quitar un archivo, llamar a
   `DELETE /api/places/{siteId}/media/{mediaId}`.
6. No enviar imágenes como base64 ni guardar `blob:`/`data:` URLs como
   referencias permanentes.

La carga debe hacerse después de que el lugar tenga un `siteId`. El endpoint de
creación/actualización del lugar todavía conserva los campos heredados
`imageUrls` y `model3dUrls`; esos campos son independientes del nuevo flujo
de multimedia.

## Autenticación y permisos

Todas las operaciones requieren el JWT de la sesión:

```http
Authorization: Bearer <access-token>
```

El usuario debe ser propietario del lugar o tener rol de administrador
(`ROLE_ADMIN`/`ADMIN`, según cómo lo exponga el token). El backend valida el
permiso con el usuario autenticado y el propietario real del lugar; no se debe
confiar únicamente en el rol o `siteId` almacenado en el frontend.

## Contrato de endpoints

### Cargar un archivo

```http
POST /api/places/{siteId}/media
Content-Type: multipart/form-data
Authorization: Bearer <access-token>
```

Campos multipart obligatorios:

| Campo | Tipo | Valores / descripción |
|---|---|---|
| `category` | texto | `images`, `videos` o `models-3d` |
| `file` | archivo | Un único archivo por solicitud |

`metadata` no forma parte del contrato actual y no reemplaza a `file`. Si en
el futuro se necesitan datos adicionales (por ejemplo, texto alternativo o
posición), se puede agregar como un campo multipart de texto independiente,
pero el backend debe implementarlo y persistirlo explícitamente.

Ejemplo con `curl`:

```bash
curl -X POST "$API_URL/api/places/123/media" \
  -H "Authorization: Bearer $TOKEN" \
  -F "category=images" \
  -F "file=@foto.jpg;type=image/jpeg"
```

Ejemplo en frontend web:

```ts
async function uploadMedia(siteId: number, file: File, token: string) {
  const form = new FormData();
  form.append("category", "images");
  form.append("file", file, file.name);

  const response = await fetch(`${API_URL}/api/places/${siteId}/media`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });

  const envelope = await response.json();
  if (!response.ok) throw new Error(envelope.message ?? "No se pudo cargar el archivo");
  return envelope.data;
}
```

No establecer manualmente `Content-Type` al usar `FormData`: el navegador debe
agregar el boundary multipart. En React Native se debe enviar el objeto `file`
con `uri`, `name` y `type` dentro de `FormData`, sin convertirlo a base64.

La documentación Scalar/OpenAPI de `POST /api/places/{siteId}/media` muestra
estos mismos dos campos y marca `file` como binario.

### Respuesta exitosa de carga

HTTP `201 Created`:

```json
{
  "status": 201,
  "message": "Created",
  "data": {
    "id": 45,
    "siteId": 123,
    "category": "images",
    "objectKey": "sites/123/images/550e8400-e29b-41d4-a716-446655440000-foto.jpg",
    "contentType": "image/jpeg",
    "originalFilename": "foto.jpg",
    "sizeBytes": 184320,
    "width": 1200,
    "height": 800,
    "checksum": "<sha256>",
    "url": "https://s3-presigned-url...",
    "urlExpiresAt": "2026-08-07T23:00:00Z"
  }
}
```

El objeto `data` tiene estos campos:

| Campo | Uso en frontend |
|---|---|
| `id` | Identificador para eliminar el archivo |
| `siteId` | Lugar al que pertenece |
| `category` | Tipo multimedia |
| `objectKey` | Referencia interna del objeto en S3 |
| `contentType` | MIME persistido |
| `originalFilename` | Nombre mostrado al usuario |
| `sizeBytes` | Tamaño final almacenado |
| `width`, `height` | Dimensiones; aplican principalmente a imágenes |
| `checksum` | Huella SHA-256; no es necesario mostrarla |
| `url` | URL prefirmada temporal para mostrar/descargar el archivo |
| `urlExpiresAt` | Fecha de expiración de `url` |

Las imágenes se validan, normalizan y almacenan como JPEG. Por eso una imagen
PNG aceptada puede responder con `contentType: "image/jpeg"` y un tamaño o
dimensiones diferentes a las del archivo original.

### Listar multimedia

```http
GET /api/places/{siteId}/media
Authorization: Bearer <access-token>
```

HTTP `200 OK`:

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "id": 45,
      "siteId": 123,
      "category": "images",
      "objectKey": "sites/123/images/...-foto.jpg",
      "contentType": "image/jpeg",
      "originalFilename": "foto.jpg",
      "sizeBytes": 184320,
      "width": 1200,
      "height": 800,
      "checksum": "<sha256>"
    }
  ]
}
```

### Eliminar multimedia

```http
DELETE /api/places/{siteId}/media/{mediaId}
Authorization: Bearer <access-token>
```

HTTP `200 OK` devuelve `data: true` cuando se eliminan el objeto de S3 y el
registro de PostgreSQL.

## Archivos aceptados y límites predeterminados

| Categoría | MIME aceptados | Extensiones | Límite predeterminado |
|---|---|---|---:|
| `images` | `image/jpeg`, `image/png` | `.jpg`, `.jpeg`, `.png` | 10 MiB |
| `videos` | `video/mp4`, `video/webm`, `video/quicktime` | `.mp4`, `.webm`, `.mov` | 100 MiB |
| `models-3d` | `model/gltf-binary`, `application/octet-stream` | `.glb` | 25 MiB |

El máximo predeterminado de una imagen es `2400 x 2400` px y el sitio puede
tener hasta `50` archivos. Los límites efectivos pueden cambiar por ambiente;
el frontend debe reflejar los límites entregados por configuración del entorno
del backend, no duplicar valores como reglas de seguridad.

El backend también valida contenido real: cambiar solamente la extensión o el
MIME declarado no hace válido un archivo corrupto o de otro tipo.

## URLs y visualización

El bucket S3 es privado. `objectKey` **no es una URL HTTP** y no debe asignarse
directamente a `<img src>`, `<video src>` o un visor GLB. El backend genera una
URL prefirmada temporal y la devuelve como `url` junto con `urlExpiresAt`.

La URL tiene una vigencia de 30 minutos. Cuando expire, el frontend debe volver
a consultar `GET /api/places/{siteId}/media` para recibir URLs nuevas. La vista
previa local (`URL.createObjectURL`) sigue siendo válida para mostrar el archivo
antes de terminar la carga, pero no debe persistirse.

## Errores que debe manejar la interfaz

Todas las respuestas de error usan el envelope:

```json
{ "status": 400, "message": "mensaje para diagnóstico", "data": null }
```

| HTTP | Causa habitual | Comportamiento recomendado |
|---:|---|---|
| `400` | Campo faltante, categoría inválida, extensión/MIME/contenido inválido, archivo vacío o límite excedido | Mostrar validación y permitir corregir |
| `401` | Token ausente, inválido o expirado | Renovar sesión o redirigir a login |
| `403` | Usuario sin permiso sobre el lugar | Mostrar acceso denegado |
| `404` | Usuario, lugar o multimedia inexistente | Refrescar contexto y notificar |
| `409` | Conflicto de persistencia | Informar y permitir reintentar con cuidado |
| `415` | Content-Type HTTP no soportado | Verificar que la solicitud sea multipart |
| `500` | Falla de S3 o PostgreSQL | No marcar como cargado; permitir reintento |

Si S3 carga el objeto pero PostgreSQL falla, el backend intenta eliminar el
objeto como compensación. El frontend debe considerar la operación fallida
cuando no recibe `201` y no agregar una referencia provisional a la lista.

## Checklist de implementación

- [ ] El botón de guardar crea primero el lugar y conserva su `id`.
- [ ] Cada archivo se envía como `FormData` con exactamente `category` y `file`.
- [ ] Se envía `Authorization: Bearer ...` en POST, GET y DELETE.
- [ ] Se valida tamaño, extensión y MIME antes de enviar para mejorar UX, sin
      reemplazar la validación del backend.
- [ ] La interfaz muestra progreso y evita doble envío del mismo archivo.
- [ ] La respuesta se considera exitosa solamente con HTTP `201`.
- [ ] Se agrega a la lista únicamente `response.data` recibido del backend.
- [ ] Se maneja la ausencia de URL visualizable: `objectKey` no se usa como
      `src`.
- [ ] Se muestra el mensaje de error del envelope sin asumir que siempre es un
      objeto de validación por campo.
- [ ] Se prueba el flujo con imagen JPG/PNG, video MP4 y modelo GLB, además de
      archivo inválido, token vencido, usuario sin permiso y archivo excedido.

## Configuración del backend

La aplicación toma estos valores desde variables de runtime; no deben
configurarse en el frontend ni incluirse en el repositorio:

- `SITE_MEDIA_ENABLED`
- `SITE_MEDIA_S3_BUCKET`
- `SITE_MEDIA_S3_REGION` (o `AWS_REGION`)
- `SITE_MEDIA_KEY_PREFIX` (predeterminado: `sites`)
- `SITE_MEDIA_MAX_IMAGE_BYTES`
- `SITE_MEDIA_MAX_VIDEO_BYTES`
- `SITE_MEDIA_MAX_MODEL_BYTES`
- `SITE_MEDIA_MAX_IMAGE_WIDTH`
- `SITE_MEDIA_MAX_IMAGE_HEIGHT`
- `SITE_MEDIA_MAX_FILES_PER_SITE`

El frontend solo necesita conocer la URL base de la API, el token de sesión y
el contrato anterior. Las credenciales AWS nunca deben llegar al cliente.
