package de.nonnull.hcu.adaxplugin.adax.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Home {
    public static final Home DEFAULT_HOME = builder().id(0).name("No Home").build();

    private final long id;
    private final String name;

    @JsonCreator
    public Home(@JsonProperty("id") long aId, @JsonProperty("name") String aName) {
        id = aId;
        name = aName;
    }
}
