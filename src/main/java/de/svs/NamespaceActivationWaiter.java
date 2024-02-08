package de.svs;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.svs.status.NamespaceStatus;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class NamespaceActivationWaiter {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    Sse sse;

    @ConfigProperty(name = "baseDomain")
    String baseDomain;

    Multi<OutboundSseEvent> waitForNamespaceToBecomeAvailable(String namespace) {
        AtomicBoolean finalMessageReceived = new AtomicBoolean();

        return Multi.createBy()
                .repeating()
                .supplier(Unchecked.supplier(() -> new NamespaceStatus(objectMapper, baseDomain).get(namespace)))
                .withDelay(Duration.ofSeconds(2))
                .until(outboundSseEvent -> finalMessageReceived.getAndSet(outboundSseEvent.finalMessage()))
                .map(Unchecked.function(statusDto -> sse.newEventBuilder()
                        .name("namespace-status")
                        .data(String.class, objectMapper.writeValueAsString(statusDto))
                        .build()))
                .select()
                .first(10);
    }
}
