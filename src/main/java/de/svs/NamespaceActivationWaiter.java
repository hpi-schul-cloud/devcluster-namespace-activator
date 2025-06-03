package de.svs;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.svs.status.NamespaceStatus;
import de.svs.status.StatusDto;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class NamespaceActivationWaiter {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    Sse sse;

    @ConfigProperty(name = "baseDomain")
    String baseDomain;

    @ConfigProperty(name = "waiter.delayInSeconds", defaultValue = "2")
    int delayInSeconds;

//    Multi<OutboundSseEvent> waitForNamespaceToBecomeAvailable(String namespace, int maxWaitTimeInSeconds) {
//        int delayInSeconds = 2;
//        int maxTries = maxWaitTimeInSeconds / delayInSeconds;
//
//        return Multi.createBy()
//                .repeating()
//                .supplier(() -> new NamespaceStatus(objectMapper, baseDomain).get(namespace))
//                .withDelay(Duration.ofSeconds(delayInSeconds))
//                .until(StatusDto::finalMessage)
//                .map(Unchecked.function(statusDto -> sse.newEventBuilder()
//                        .name("namespace-status")
//                        .data(String.class, objectMapper.writeValueAsString(statusDto))
//                        .build()))
//                .select()
//                .first(maxTries);
//    }

//    Multi<OutboundSseEvent> waitForNamespaceToBecomeAvailable2(String namespace, int maxWaitTimeInSeconds) {
//        int delayInSeconds = 2;
//        int maxTries = maxWaitTimeInSeconds / delayInSeconds;
//
//        return Multi.createBy()
//                .repeating()
//                .supplier(() -> new NamespaceStatus(objectMapper, baseDomain).get(namespace))
//                .withDelay(Duration.ofSeconds(delayInSeconds))
//                .until(StatusDto::finalMessage) // ⬅️ terminator that makes it a Multi
//                .map(Unchecked.function(status -> sse.newEventBuilder()
//                            .name(status.finalMessage() ? "namespace-status" : "keepalive")
//                            .data(String.class, objectMapper.writeValueAsString(status))
//                            .comment(status.finalMessage() ? null : "still waiting")
//                            .build())
//                )
//                .select()
//                .first(maxTries);
//    }

    Multi<StatusDto> waitForNamespaceToBecomeAvailable3(String namespace, int maxWaitTimeInSeconds) {
        int delayInSeconds = 2;
        int maxTries = maxWaitTimeInSeconds / delayInSeconds;

        return Multi.createBy()
                .repeating()
                .supplier(() -> new NamespaceStatus(objectMapper, baseDomain).get(namespace))
                .withDelay(Duration.ofSeconds(delayInSeconds))
                .until(StatusDto::finalMessage)
                .select()
                .first(maxTries);
    }
}
