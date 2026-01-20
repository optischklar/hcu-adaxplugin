package de.nonnull.hcu.adaxplugin.adax;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ControlStatus {
    OK("OK"), NO_ACCESS("NoAccess"), INVALID_PARAMS("InvalidParams"), INTERNAL_ERROR("InternalError");

    private final String jsonValue;

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }
}
