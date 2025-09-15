package co.turismo.model.visits;

import lombok.Value;

@Value
public class TopPlace {
    Long placeId;
    String name;
    Integer visits;
}