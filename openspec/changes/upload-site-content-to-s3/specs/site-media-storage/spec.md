## Purpose

Define a secure reactive API for validating, processing and storing site images, videos and GLB models in S3 while PostgreSQL retains only object references.

## ADDED Requirements

### Requirement: Authorized users can upload supported site content
The system SHALL accept authenticated multipart uploads associated with a valid `siteId` for an owner or administrator authorized to modify that site, and SHALL support image, video and GLB model media categories.

#### Scenario: Carga válida de imagen, video y modelo GLB
- **WHEN** an authorized user uploads a valid supported image, video or GLB file within the configured limits
- **THEN** the API stores the object, persists its URL or key for that site and media type, and returns the persisted reference and media metadata

#### Scenario: Usuario no autorizado
- **WHEN** an unauthenticated user or an authenticated user without ownership/administrative permission uploads content for a site
- **THEN** the API rejects the request with an authorization error and neither S3 nor PostgreSQL receives a new media record

### Requirement: Upload content is validated before publication
The system SHALL enforce configured per-type size limits, allowlisted media types/extensions, real-content/signature validation and upload count limits, and SHALL reject malformed, empty, deceptive or unsupported content without publishing a reference.

#### Scenario: Rechazo de formato, tamaño o contenido inválido
- **WHEN** a file has an unsupported type, exceeds its limit, has a mismatched extension/content signature, is empty or is not a valid GLB/image/video
- **THEN** the API returns a client validation error and does not persist a public reference

#### Scenario: Redimensionamiento seguro de imagen
- **WHEN** a valid image exceeds the configured display dimensions or requires normalization
- **THEN** the image is decoded and resized/normalized within resource limits before S3 publication, without blocking the reactive event loop

### Requirement: Object keys are generated and organized by site and media type
The system SHALL ignore client-provided object paths and generate a normalized key containing the authorized numeric `siteId`, a permitted media category and a server-generated collision-resistant filename; path traversal, arbitrary buckets and cross-site prefixes SHALL be impossible.

#### Scenario: Generación segura de la clave por siteId
- **WHEN** a valid upload is accepted for site `123`
- **THEN** its key is generated under the configured bucket/prefix and the `123` site/category namespace, with no user-controlled path segments

### Requirement: PostgreSQL stores only the object reference
The system SHALL persist only the S3 URL or object key, media type and required non-binary metadata in PostgreSQL, and SHALL return the same reference in the site API response.

#### Scenario: Persistencia de URL o clave
- **WHEN** S3 completes an upload and PostgreSQL persistence succeeds
- **THEN** the media reference is available from the site resource and no binary payload is stored in PostgreSQL

### Requirement: Storage failures are safe and observable
The system SHALL return a server error when S3 fails, SHALL compensate by deleting an uploaded object when PostgreSQL persistence fails afterward, and SHALL not expose an apparently successful reference for an incomplete operation.

#### Scenario: Fallo de S3
- **WHEN** S3 rejects or times out during upload
- **THEN** the API returns a server error, no PostgreSQL reference is created, and the failure is logged without secrets or file contents

#### Scenario: Fallo de PostgreSQL después de cargar
- **WHEN** S3 succeeds but PostgreSQL fails while persisting the reference
- **THEN** the API returns a server error and attempts an idempotent deletion/compensation of the uploaded object

#### Scenario: Fallo de compensación
- **WHEN** PostgreSQL fails and S3 deletion also fails
- **THEN** the API still reports the operation as failed, records a safe reconciliation signal with the object key, and never returns the object as persisted media

### Requirement: API limits and configuration are explicit
The system SHALL expose documented multipart contracts, status codes, configurable bucket/region/prefix/limits/content policy and safe error responses; credentials and provider secrets SHALL come from protected runtime configuration rather than source-controlled defaults.

#### Scenario: Contrato de API inválido
- **WHEN** the request omits `siteId`, media category or file, or violates the multipart contract
- **THEN** the API returns a validation error with field-level information and does not contact S3

### Requirement: Media processing is compatible with WebFlux
The system SHALL process uploads through non-blocking reactive publishers, bound memory and data-buffer usage, and offload unavoidable CPU-heavy image/GLB inspection work to a bounded scheduler so the WebFlux event loop is not blocked.

#### Scenario: Procesamiento reactivo sin bloquear el event loop
- **WHEN** concurrent valid uploads are received
- **THEN** backpressure and configured limits bound resource usage, S3 and PostgreSQL calls remain reactive, and CPU-bound processing does not execute on the Netty event-loop scheduler
