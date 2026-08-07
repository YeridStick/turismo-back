# Site media storage

La carga multimedia usa un bucket S3 privado. Los binarios se almacenan bajo
`<SITE_MEDIA_S3_PREFIX>/<siteId>/<category>/` y PostgreSQL conserva únicamente
la clave S3, tipo, nombre sanitizado, tamaño, checksum y metadatos seguros.

Variables de runtime, sin valores reales en el repositorio:

- `SITE_MEDIA_ENABLED`: habilita el caso de uso.
- `SITE_MEDIA_S3_BUCKET`: bucket privado provisionado por infraestructura.
- `SITE_MEDIA_S3_REGION`: región AWS; coincide con `aws_region` de Terraform.
- `SITE_MEDIA_KEY_PREFIX`: prefijo lógico, por defecto `sites`.
- `SITE_MEDIA_MAX_IMAGE_BYTES`, `SITE_MEDIA_MAX_VIDEO_BYTES`, `SITE_MEDIA_MAX_MODEL_BYTES`.
- `SITE_MEDIA_MAX_IMAGE_WIDTH`, `SITE_MEDIA_MAX_IMAGE_HEIGHT`.
- `SITE_MEDIA_MAX_FILES_PER_SITE`.

El SDK utiliza la cadena estándar de credenciales AWS y no requiere access keys
permanentes en la aplicación. La infraestructura existente mantiene el bucket
privado, el bloqueo público, `BucketOwnerEnforced`, AES256, versionado y
lifecycle. Cualquier permiso IAM adicional para el runtime debe proponerse en
`turismo-infra` por separado.
