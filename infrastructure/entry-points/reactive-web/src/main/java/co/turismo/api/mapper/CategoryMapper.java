package co.turismo.api.mapper;

import co.turismo.api.dto.category.CreateCategoryBody;
import co.turismo.api.dto.category.UpdateCategoryBody;
import co.turismo.model.category.CreateCategoryRequest;
import co.turismo.model.category.UpdateCategoryRequest;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static CreateCategoryRequest toCreateCategoryRequest(CreateCategoryBody body) {
        return CreateCategoryRequest.builder()
                .slug(body.slug())
                .name(body.name())
                .build();
    }

    public static UpdateCategoryRequest toUpdateCategoryRequest(UpdateCategoryBody body) {
        return UpdateCategoryRequest.builder()
                .slug(body.slug())
                .name(body.name())
                .build();
    }
}