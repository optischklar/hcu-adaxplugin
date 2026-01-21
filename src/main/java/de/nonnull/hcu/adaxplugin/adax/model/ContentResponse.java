package de.nonnull.hcu.adaxplugin.adax.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentResponse {
    @NonNull
    private final List<Home> homes;
    @NonNull
    private final List<Room> rooms;

    @JsonCreator
    public ContentResponse(@JsonProperty("homes") @NonNull List<Home> aHomes,
            @JsonProperty("rooms") @NonNull List<Room> aRooms) {
        homes = aHomes;
        rooms = aRooms;
    }

}
