package co.turismo.usecase.sitemedia;

import co.turismo.model.error.NotFoundException;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.sitemedia.SiteMedia;
import co.turismo.model.sitemedia.SiteMediaSettings;
import co.turismo.model.sitemedia.SiteMediaUpload;
import co.turismo.model.sitemedia.SiteMediaAccess;
import co.turismo.model.sitemedia.gateways.SiteMediaRepository;
import co.turismo.model.sitemedia.gateways.SiteMediaStorageGateway;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class SiteMediaUseCase {
    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(30);
    private static final Logger LOG = Logger.getLogger(SiteMediaUseCase.class.getName());
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final String MODEL_TYPE = "model/gltf-binary";

    private final PlaceRepository placeRepository;
    private final UserIdentityPort userIdentityPort;
    private final SiteMediaRepository mediaRepository;
    private final SiteMediaStorageGateway storageGateway;
    private final SiteMediaSettings properties;

    public Mono<SiteMedia> upload(String email, Set<String> roles, Long siteId, String category, SiteMediaUpload upload) {
        if (!properties.enabled()) {
            return Mono.error(new IllegalStateException("La carga multimedia no está habilitada"));
        }
        return authorize(email, roles, siteId)
                .then(validateRequest(siteId, category, upload))
                .then(mediaRepository.findBySiteId(siteId).count()
                        .filter(count -> count < properties.maxFilesPerSite())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Se alcanzó el límite de archivos del sitio"))))
                .then(collect(upload.getContent(), limitFor(category), upload.getDeclaredSize()))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(bytes -> normalize(category, upload.getFilename(), upload.getContentType(), bytes))
                .flatMap(media -> storeAndPersist(siteId, media));
    }

    public Flux<SiteMedia> findBySite(Long siteId) {
        return mediaRepository.findBySiteId(siteId);
    }

    public Flux<SiteMedia> findBySite(String email, Set<String> roles, Long siteId) {
        return authorize(email, roles, siteId).thenMany(mediaRepository.findBySiteId(siteId));
    }

    public Mono<Boolean> delete(String email, Set<String> roles, Long siteId, Long mediaId) {
        return authorize(email, roles, siteId)
                .then(mediaRepository.findByIdForSite(mediaId, siteId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Contenido no encontrado"))))
                .flatMap(media -> storageGateway.delete(media.getObjectKey())
                        .then(mediaRepository.deleteByIdForSite(mediaId, siteId)));
    }

    public Mono<SiteMediaAccess> presignedUrl(SiteMedia media) {
        return storageGateway.presignedGet(media.getObjectKey(), PRESIGNED_URL_DURATION);
    }

    private Mono<Void> authorize(String email, Set<String> roles, Long siteId) {
        if (siteId == null || siteId <= 0 || email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("siteId y usuario son obligatorios"));
        }
        boolean admin = roles != null && roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(role -> role.toLowerCase(Locale.ROOT))
                .anyMatch(role -> role.equals("admin") || role.equals("role_admin"));
        return userIdentityPort.getUserIdForEmail(email)
                .switchIfEmpty(Mono.error(new NotFoundException("Usuario no encontrado")))
                .zipWith(placeRepository.findByPlaces(siteId)
                        .switchIfEmpty(Mono.error(new NotFoundException("Sitio no encontrado"))))
                .flatMap(tuple -> {
                    Place place = tuple.getT2();
                    if (admin || tuple.getT1().id().equals(place.getOwnerUserId())) {
                        return Mono.empty();
                    }
                    return Mono.error(new AccessDeniedException("No tienes permiso sobre este sitio"));
                });
    }

    private Mono<Void> validateRequest(Long siteId, String category, SiteMediaUpload upload) {
        if (category == null || !Set.of("images", "videos", "models-3d").contains(category)) {
            return Mono.error(new IllegalArgumentException("Categoría multimedia no permitida"));
        }
        if (upload == null || upload.getContent() == null || upload.getFilename() == null || upload.getFilename().isBlank()) {
            return Mono.error(new IllegalArgumentException("Archivo requerido"));
        }
        if (upload.getDeclaredSize() != null && upload.getDeclaredSize() <= 0) {
            return Mono.error(new IllegalArgumentException("El archivo está vacío"));
        }
        if (upload.getDeclaredSize() != null && upload.getDeclaredSize() > limitFor(category)) {
            return Mono.error(new IllegalArgumentException("El archivo supera el límite configurado"));
        }
        String type = normalizeType(upload.getContentType());
        if (!allowedTypes(category).contains(type)) {
            return Mono.error(new IllegalArgumentException("Tipo multimedia no permitido"));
        }
        if (!allowedExtensions(category).contains(extension(upload.getFilename()))) {
            return Mono.error(new IllegalArgumentException("Extensión multimedia no permitida"));
        }
        return Mono.empty();
    }

    private Mono<byte[]> collect(Flux<ByteBuffer> content, long maxBytes, Long declaredSize) {
        return content
                .publishOn(Schedulers.boundedElastic())
                .reduce(new ByteArrayOutputStream(), (out, buffer) -> {
                    ByteBuffer copy = buffer.slice();
                    if ((long) out.size() + copy.remaining() > maxBytes) {
                        throw new IllegalArgumentException("El archivo supera el límite configurado");
                    }
                    byte[] chunk = new byte[copy.remaining()];
                    copy.get(chunk);
                    out.writeBytes(chunk);
                    return out;
                })
                .flatMap(out -> {
                    if (out.size() == 0 || (declaredSize != null && declaredSize == 0)) {
                        return Mono.error(new IllegalArgumentException("El archivo está vacío"));
                    }
                    return Mono.just(out.toByteArray());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<NormalizedMedia> normalize(String category, String filename, String contentType, byte[] bytes) {
        return Mono.fromCallable(() -> {
            String type = normalizeType(contentType);
            String safeName = sanitize(filename);
            if (category.equals("images")) {
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
                if (source == null) {
                    throw new IllegalArgumentException("La imagen no es válida");
                }
                int width = Math.min(source.getWidth(), properties.maxImageWidth());
                int height = Math.min(source.getHeight(), properties.maxImageHeight());
                double scale = Math.min((double) width / source.getWidth(), (double) height / source.getHeight());
                if (scale >= 1d) {
                    width = source.getWidth();
                    height = source.getHeight();
                } else {
                    width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                    height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                }
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = output.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
                graphics.dispose();
                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                if (!ImageIO.write(output, "jpg", encoded)) {
                    throw new IllegalArgumentException("No se pudo normalizar la imagen");
                }
                return new NormalizedMedia(encoded.toByteArray(), "image/jpeg", ".jpg", width, height, category, safeName);
            }
            if (category.equals("models-3d")) {
                validateGlb(bytes);
                return new NormalizedMedia(bytes, MODEL_TYPE, ".glb", null, null, category, safeName);
            }
            validateVideo(bytes, type);
            return new NormalizedMedia(bytes, type, extensionForVideo(type, safeName), null, null, category, safeName);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<SiteMedia> storeAndPersist(Long siteId, NormalizedMedia media) {
        String safeName = sanitize(media.originalFilename()).replaceFirst("\\.[^.]+$", "");
        String key = properties.keyPrefix() + "/" + siteId + "/" + media.categoryDirectory + "/"
                + UUID.randomUUID() + "-" + safeName + media.extension;
        return storageGateway.upload(key, media.contentType, Flux.just(ByteBuffer.wrap(media.bytes)), media.bytes.length)
                .flatMap(storedKey -> {
                    SiteMedia value = SiteMedia.builder()
                            .siteId(siteId)
                            .category(media.categoryDirectory)
                            .objectKey(storedKey)
                            .contentType(media.contentType)
                            .originalFilename(media.originalFilename)
                            .sizeBytes((long) media.bytes.length)
                            .width(media.width)
                            .height(media.height)
                            .checksum(sha256(media.bytes))
                            .createdAt(OffsetDateTime.now())
                            .build();
                    return mediaRepository.save(value)
                            .onErrorResume(error -> storageGateway.delete(storedKey)
                                    .onErrorResume(compensationError -> {
                                        LOG.severe("No se pudo compensar objeto multimedia key=" + storedKey);
                                        return Mono.empty();
                                    })
                                    .then(Mono.error(error)));
                });
    }

    private long limitFor(String category) {
        return switch (category) {
            case "images" -> properties.maxImageBytes();
            case "videos" -> properties.maxVideoBytes();
            case "models-3d" -> properties.maxModelBytes();
            default -> 0;
        };
    }

    private Set<String> allowedTypes(String category) {
        return switch (category) {
            case "images" -> IMAGE_TYPES;
            case "videos" -> VIDEO_TYPES;
            case "models-3d" -> Set.of(MODEL_TYPE, "application/octet-stream");
            default -> Set.of();
        };
    }

    private static Set<String> allowedExtensions(String category) {
        return switch (category) {
            case "images" -> Set.of("jpg", "jpeg", "png");
            case "videos" -> Set.of("mp4", "webm", "mov");
            case "models-3d" -> Set.of("glb");
            default -> Set.of();
        };
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String filename) {
        String safe = sanitize(filename).toLowerCase(Locale.ROOT);
        int dot = safe.lastIndexOf('.');
        return dot < 0 ? "" : safe.substring(dot + 1);
    }

    private static String sanitize(String filename) {
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^-+", "")
                .trim();
        return name.isBlank() ? "upload" : name.substring(0, Math.min(name.length(), 80));
    }

    private static String extensionForVideo(String type, String filename) {
        if (type.equals("video/webm")) return ".webm";
        if (type.equals("video/quicktime")) return ".mov";
        return ".mp4";
    }

    private static void validateVideo(byte[] bytes, String type) {
        if (bytes.length < 12) throw new IllegalArgumentException("El video está incompleto");
        boolean valid = switch (type) {
            case "video/mp4", "video/quicktime" -> new String(bytes, 4, 4, StandardCharsets.US_ASCII).equals("ftyp");
            case "video/webm" -> (bytes[0] & 0xff) == 0x1a && (bytes[1] & 0xff) == 0x45 && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("El contenido no coincide con el tipo de video");
    }

    private static void validateGlb(byte[] bytes) {
        if (bytes.length < 12) throw new IllegalArgumentException("El modelo GLB está incompleto");
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt() != 0x46546c67 || header.getInt() != 2) {
            throw new IllegalArgumentException("El modelo GLB no es válido");
        }
        long declaredLength = Integer.toUnsignedLong(header.getInt());
        if (declaredLength < 12 || declaredLength > bytes.length) {
            throw new IllegalArgumentException("La longitud declarada del GLB no es válida");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo calcular checksum", error);
        }
    }

    private record NormalizedMedia(
            byte[] bytes,
            String contentType,
            String extension,
            Integer width,
            Integer height,
            String categoryDirectory,
            String originalFilename
    ) {}
}
