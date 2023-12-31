package de.svs.status;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.LocalDateTime;

@RegisterForReflection
public record StatusDto(String message, String namespaceBaseUri, java.time.LocalDateTime date, boolean success, boolean finalMessage) {
    public StatusDto(String message, String namespaceBaseUri, boolean success, boolean finalMessage) {
        this(message, namespaceBaseUri, LocalDateTime.now(), success, finalMessage);
    }
}
