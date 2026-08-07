package co.turismo.usecase.sitemedia;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.sitemedia.SiteMedia;
import co.turismo.model.sitemedia.SiteMediaSettings;
import co.turismo.model.sitemedia.SiteMediaUpload;
import co.turismo.model.sitemedia.gateways.SiteMediaRepository;
import co.turismo.model.sitemedia.gateways.SiteMediaStorageGateway;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import co.turismo.model.userIdentityPort.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteMediaUseCaseTest {
    @Mock private PlaceRepository placeRepository;
    @Mock private UserIdentityPort userIdentityPort;
    @Mock private SiteMediaRepository mediaRepository;
    @Mock private SiteMediaStorageGateway storageGateway;

    private SiteMediaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SiteMediaUseCase(
                placeRepository,
                userIdentityPort,
                mediaRepository,
                storageGateway,
                new SiteMediaSettings(true, "sites", 2_000_000, 2_000_000, 2_000_000, 100, 100, 10));
        when(userIdentityPort.getUserIdForEmail("owner@example.com"))
                .thenReturn(Mono.just(new UserSummary(7L, "owner@example.com")));
        when(placeRepository.findByPlaces(123L))
                .thenReturn(Mono.just(Place.builder().id(123L).ownerUserId(7L).build()));
        lenient().when(mediaRepository.findBySiteId(123L)).thenReturn(Flux.empty());
        lenient().when(storageGateway.upload(any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> Mono.just((String) invocation.getArgument(0)));
        lenient().when(mediaRepository.save(any()))
                .thenAnswer(invocation -> Mono.just((SiteMedia) invocation.getArgument(0)));
    }

    @Test
    void acceptsImageVideoAndGlb() throws Exception {
        StepVerifier.create(upload("images", "photo.png", "image/png", imageBytes()))
                .assertNext(media -> assertTrue(media.getObjectKey().startsWith("sites/123/images/")))
                .verifyComplete();
        StepVerifier.create(upload("videos", "movie.mp4", "video/mp4", mp4Bytes()))
                .assertNext(media -> assertTrue(media.getObjectKey().contains("/videos/")))
                .verifyComplete();
        StepVerifier.create(upload("models-3d", "model.glb", "model/gltf-binary", glbBytes()))
                .assertNext(media -> assertTrue(media.getObjectKey().contains("/models-3d/")))
                .verifyComplete();
    }

    @Test
    void rejectsInvalidContentAndUnauthorizedUser() throws Exception {
        doReturn(Mono.empty()).when(userIdentityPort).getUserIdForEmail("intruder@example.com");
        StepVerifier.create(upload("images", "photo.exe", "image/png", imageBytes()))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(upload("models-3d", "danger/../../model.glb", "model/gltf-binary", new byte[]{1, 2, 3}))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(useCase.upload("intruder@example.com", Set.of(), 123L, "images",
                        upload("bad.png", "image/png", imageBytes())))
                .expectError()
                .verify();
        verify(storageGateway, never()).upload(any(), any(), any(), anyLong());
    }

    @Test
    void deletesOnlyMediaBelongingToTheSite() {
        SiteMedia media = SiteMedia.builder().id(9L).siteId(123L).objectKey("sites/123/images/file.jpg").build();
        when(mediaRepository.findByIdForSite(9L, 123L)).thenReturn(Mono.just(media));
        when(storageGateway.delete(media.getObjectKey())).thenReturn(Mono.empty());
        when(mediaRepository.deleteByIdForSite(9L, 123L)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.delete("owner@example.com", Set.of(), 123L, 9L))
                .expectNext(true)
                .verifyComplete();
        verify(mediaRepository).deleteByIdForSite(9L, 123L);
    }

    @Test
    void compensatesObjectWhenPostgresFails() throws Exception {
        doReturn(Mono.error(new IllegalStateException("postgres"))).when(mediaRepository).save(any());
        when(storageGateway.delete(any())).thenReturn(Mono.empty());

        StepVerifier.create(upload("images", "photo.png", "image/png", imageBytes()))
                .expectError(IllegalStateException.class)
                .verify();
        verify(storageGateway).delete(any());
    }

    @Test
    void preservesPostgresErrorWhenCompensationAlsoFailsAndLogsOnlyTheKey() throws Exception {
        doReturn(Mono.error(new IllegalStateException("postgres-primary-failure"))).when(mediaRepository).save(any());
        doReturn(Mono.error(new IllegalStateException("s3-delete-failure"))).when(storageGateway).delete(any());
        Logger logger = Logger.getLogger(SiteMediaUseCase.class.getName());
        AtomicReference<String> logMessage = new AtomicReference<>();
        AtomicReference<Throwable> loggedThrowable = new AtomicReference<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                logMessage.set(record.getMessage());
                loggedThrowable.set(record.getThrown());
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try {
            StepVerifier.create(upload("images", "safe.png", "image/png", imageBytes()))
                    .expectErrorMatches(error -> error instanceof IllegalStateException
                            && "postgres-primary-failure".equals(error.getMessage()))
                    .verify();
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(logMessage.get() != null && logMessage.get().contains("key="));
        assertFalse(logMessage.get().contains("postgres-primary-failure"));
        assertFalse(logMessage.get().contains("s3-delete-failure"));
        assertFalse(logMessage.get().contains("X-Amz-Signature"));
        assertTrue(loggedThrowable.get() == null);
    }

    @Test
    void doesNotPersistWhenS3Fails() throws Exception {
        doReturn(Mono.error(new IllegalStateException("s3")))
                .when(storageGateway).upload(any(), any(), any(), anyLong());

        StepVerifier.create(upload("images", "photo.png", "image/png", imageBytes()))
                .expectError(IllegalStateException.class)
                .verify();
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void rejectsEmptyFile() {
        StepVerifier.create(upload("models-3d", "model.glb", "model/gltf-binary", new byte[0]))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void imageProcessingAndStorageSubscriptionUseBoundedElasticAndPropagateErrors() throws Exception {
        AtomicReference<String> uploadThread = new AtomicReference<>();
        doReturn(Mono.error(new IllegalStateException("s3-stream-failure")))
                .when(storageGateway).upload(any(), any(), any(), anyLong());
        when(storageGateway.upload(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            uploadThread.set(Thread.currentThread().getName());
            return Mono.error(new IllegalStateException("s3-stream-failure"));
        });

        StepVerifier.create(upload("images", "photo.png", "image/png", imageBytes()))
                .expectErrorMessage("s3-stream-failure")
                .verify();

        assertTrue(uploadThread.get() != null && uploadThread.get().contains("boundedElastic"));
    }

    @Test
    void cancellationDoesNotPublishAnIncompleteUpload() {
        StepVerifier.create(useCase.upload("owner@example.com", Set.of("OWNER"), 123L, "images",
                        SiteMediaUpload.builder()
                                .filename("never.png")
                                .contentType("image/png")
                                .declaredSize(null)
                                .content(Flux.never())
                                .build()))
                .thenCancel()
                .verify();
        verify(storageGateway, never()).upload(any(), any(), any(), anyLong());
    }

    @Test
    void concurrentUploadsRemainReactiveAndUseTheConfiguredPerFileBound() throws Exception {
        AtomicInteger uploads = new AtomicInteger();
        byte[] bytes = imageBytes();
        doAnswer(invocation -> {
            uploads.incrementAndGet();
            return Mono.just((String) invocation.getArgument(0));
        }).when(storageGateway).upload(any(), any(), any(), anyLong());

        StepVerifier.create(Flux.range(1, 4)
                        .flatMap(index -> upload("images", "photo-" + index + ".png", "image/png", bytes), 2)
                        .count())
                .expectNext(4L)
                .verifyComplete();
        assertTrue(uploads.get() == 4);
    }

    private Mono<SiteMedia> upload(String category, String filename, String contentType, byte[] bytes) {
        return useCase.upload("owner@example.com", Set.of("OWNER"), 123L, category,
                upload(filename, contentType, bytes));
    }

    private static SiteMediaUpload upload(String filename, String contentType, byte[] bytes) {
        return SiteMediaUpload.builder()
                .filename(filename)
                .contentType(contentType)
                .declaredSize((long) bytes.length)
                .content(Flux.just(ByteBuffer.wrap(bytes)))
                .build();
    }

    private static byte[] imageBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] mp4Bytes() {
        byte[] bytes = new byte[16];
        bytes[4] = 'f'; bytes[5] = 't'; bytes[6] = 'y'; bytes[7] = 'p';
        return bytes;
    }

    private static byte[] glbBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x46546c67).putInt(2).putInt(12);
        return buffer.array();
    }
}
