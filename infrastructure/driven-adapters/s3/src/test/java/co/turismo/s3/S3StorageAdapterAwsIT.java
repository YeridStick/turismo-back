package co.turismo.s3;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "RUN_AWS_S3_INTEGRATION", matches = "true")
class S3StorageAdapterAwsIT {
    private static S3AsyncClient client;
    private static S3StorageAdapter adapter;
    private static String bucket;
    private static String key;

    @BeforeAll
    static void setUp() {
        bucket = required("SITE_MEDIA_S3_BUCKET");
        String region = required("SITE_MEDIA_S3_REGION");
        client = S3AsyncClient.builder().region(Region.of(region)).build();
        adapter = new S3StorageAdapter(client, new S3Properties(bucket, region));
        key = "sites/integration-tests/" + UUID.randomUUID() + "/s3-adapter-connectivity.txt";
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void adapterUsesStandardCredentialsAndCanRoundTripAndDelete() {
        byte[] expected = "turismo-back S3 connectivity test".getBytes(StandardCharsets.UTF_8);

        assertEquals(key, adapter.upload(key, "text/plain", Flux.just(ByteBuffer.wrap(expected)), expected.length).block());

        var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).join();
        assertEquals(expected.length, head.contentLength());
        assertEquals("text/plain", head.contentType());

        byte[] actual = client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(key).build(),
                        AsyncResponseTransformer.toBytes())
                .join()
                .asByteArray();
        assertArrayEquals(expected, actual);

        adapter.delete(key).block();
        assertThrows(CompletionException.class, () -> client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build()).join());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for AWS integration");
        }
        return value;
    }
}
