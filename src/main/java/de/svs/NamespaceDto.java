package de.svs;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class NamespaceDto {

    private String name;
    private Instant activatedUntil;

}
