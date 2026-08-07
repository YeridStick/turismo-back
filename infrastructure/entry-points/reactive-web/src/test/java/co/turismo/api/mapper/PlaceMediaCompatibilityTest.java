package co.turismo.api.mapper;

import co.turismo.api.dto.place.PlaceCreateRequest;
import co.turismo.api.dto.place.UpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PlaceMediaCompatibilityTest {
    @Test
    void keepsExistingImageAndModelArraysInCreateContract() {
        PlaceCreateRequest request = new PlaceCreateRequest(
                "site", "description", 1L, 4.7, -74.1, "address", "phone", "web",
                new String[]{"https://legacy.example/image.jpg"},
                new String[]{"https://legacy.example/model.glb"},
                new String[]{"wifi"});

        var mapped = PlaceMapper.toCreatePlaceRequest("owner@example.com", request);

        assertArrayEquals(request.imageUrls(), mapped.getImageUrls());
        assertArrayEquals(request.model3dUrls(), mapped.getModel3dUrls());
    }

    @Test
    void keepsExistingImageAndModelArraysInPatchContract() {
        UpdateRequest request = new UpdateRequest(
                "site", "description", 1L, 4.7, -74.1, "address", "phone", "web",
                List.of("https://legacy.example/image.jpg"),
                List.of("https://legacy.example/model.glb"),
                List.of("wifi"));

        var mapped = PlaceMapper.toUpdatePlaceRequest(request);

        assertArrayEquals(new String[]{"https://legacy.example/image.jpg"}, mapped.getImageUrls());
        assertArrayEquals(new String[]{"https://legacy.example/model.glb"}, mapped.getModel3dUrls());
    }
}
