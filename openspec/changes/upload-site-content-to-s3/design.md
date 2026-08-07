## Context

The current `Place` model already exposes `imageUrls` and `model3dUrls`, but the existing create/patch routes consume JSON and there is no S3 adapter, media table/contract, upload policy, or video field. The application uses Spring WebFlux and R2DBC PostgreSQL. `turismo-infra` is intentionally outside this change until its Terraform needs are reviewed separately.

## Goals / Non-Goals

**Goals:**

- Add a bounded multipart upload path for images, videos and GLB models.
- Keep binary data in S3 and references/metadata in PostgreSQL.
- Enforce ownership/admin authorization, server-generated keys, validation, compensation and reactive execution.
- Define the configuration and deployment contract without changing Terraform in this repository.

**Non-Goals:**

- Public anonymous uploads, client-selected buckets/keys, or storing binary data in PostgreSQL.
- Transcoding video or rendering/optimizing 3D models beyond validation and bounded metadata extraction.
- Initializing or modifying `~/Proyectos/turismo-infra`.

## Decisions

- **Dedicated media resource and endpoint.** Use a site-scoped multipart endpoint such as `POST /api/places/{siteId}/media` with a constrained media type field. A single JSON URL patch is rejected for new uploads because it cannot validate or own binary handling.
- **Dedicated media persistence.** Prefer a `site_media` table with site id, category, object key/URL, content type, size, dimensions/duration where available, checksum and timestamps. This preserves multiple items and supports safe deletion; encoding more arrays into the existing places row would make lifecycle and compensation ambiguous.
- **S3 streaming with bounded processing.** Stream the request to S3 through the AWS reactive/non-blocking integration or a bounded bridge approved by the project dependencies. Image decode/resize and GLB signature inspection run on a bounded scheduler with explicit buffer limits. No `block()`, filesystem staging of unbounded uploads, or event-loop CPU work.
- **Key policy.** Generate `sites/{siteId}/{category}/{uuid}.{safe-extension}` from server-controlled values. The bucket, public/private mode and URL strategy are configuration-driven; the client cannot supply bucket or key.
- **Two-phase compensation.** Upload first, persist the reference second, then delete the object if persistence fails. Record a reconciliation signal if deletion fails. PostgreSQL transactions cannot include S3, so idempotency and cleanup are explicit rather than pretending to be atomic.
- **Content validation.** Check declared metadata plus magic bytes/parseability, size before and during streaming, extension allowlist and category-specific limits. GLB requires a valid GLB header/version and bounded declared lengths; images require decodable dimensions; video is validated by safe container/type checks within the selected dependency constraints.
- **Authorization at the boundary and use case.** Resolve the site and verify owner/admin permission before reading or storing the upload. Never trust a client-provided owner id or site path.

## Risks / Trade-offs

- [Risk] Large media consumes buffers or CPU → enforce request, per-file and aggregate limits, stream to S3, and isolate CPU-heavy work on a bounded scheduler.
- [Risk] S3 succeeds while PostgreSQL fails → delete the object and emit a reconciliation record when deletion fails.
- [Risk] Public URLs expose media unexpectedly → make URL/public-read policy explicit in configuration and return a key or signed URL according to the approved deployment policy.
- [Risk] Existing `imageUrls`/`model3dUrls` clients expect arrays → introduce the media resource compatibly and define read/write migration behavior before changing those fields.

## Migration Plan

1. Confirm the API shape, media retention policy and S3 URL visibility policy before implementation.
2. Add the dependency/configuration, media schema and reactive ports/adapters.
3. Deploy with uploads disabled or behind configuration until bucket permissions and limits are verified.
4. Migrate existing URL arrays only if an inventory proves they are compatible; otherwise preserve read compatibility and migrate separately.
5. Roll back by disabling the upload feature and retaining references; remove objects only through an approved retention/cleanup process.
