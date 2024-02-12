package de.svs;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import org.jboss.resteasy.reactive.RestMulti;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class NamespaceControllerTest {

    @Inject
    NamespaceController namespaceController;

    @BeforeAll
    public static void setup() {
        NamespaceActivationWaiter mock = Mockito.mock(NamespaceActivationWaiter.class);
        String namespace = any(String.class);
        when(mock.waitForNamespaceToBecomeAvailable(namespace, anyInt())).thenReturn(Multi.createFrom().empty());
        QuarkusMock.installMockForType(mock, NamespaceActivationWaiter.class);
    }

    @Test
    void createIfNotExistsAndWaitExistingNamespace() {
        String namespaceName = UUID.randomUUID().toString();
        Namespace namespace = new Namespace();
        namespace.name = namespaceName;
        namespace.activatedUntil = Instant.EPOCH;
        namespace.persist();

        RestMulti<OutboundSseEvent> multi = namespaceController.createIfNotExistsAndWait(namespaceDto(namespaceName));
        AssertSubscriber<OutboundSseEvent> subscriber = multi.subscribe().withSubscriber(AssertSubscriber.create(0));

        Optional<Namespace> nsByName = Namespace.findByName(namespaceName);
        assertThat(nsByName).isPresent();
        assertThat(nsByName.get().name).isEqualTo(namespaceName);
        assertThat(nsByName.get().activatedUntil).isEqualTo(Instant.EPOCH);
        assertThat(multi.getStatus()).isEqualTo(304);
        subscriber.assertCompleted().awaitItems(0).assertItems();
    }

    private static NamespaceDto namespaceDto(String namespaceName) {
        NamespaceDto dto = new NamespaceDto();
        dto.setName(namespaceName);
        return dto;
    }

    @Test
    void createIfNotExistsAndWaitNewNamespace() {
        String namespaceName = UUID.randomUUID().toString();
        RestMulti<OutboundSseEvent> multi = namespaceController.createIfNotExistsAndWait(namespaceDto(namespaceName));
        AssertSubscriber<OutboundSseEvent> subscriber = multi.subscribe().withSubscriber(AssertSubscriber.create(0));

        Optional<Namespace> nsByName = Namespace.findByName(namespaceName);
        assertThat(nsByName).isPresent();
        assertThat(nsByName.get().name).isEqualTo(namespaceName);
        assertThat(nsByName.get().activatedUntil).isCloseTo(Instant.now().plus(2, DAYS), within(1, SECONDS));
        assertThat(multi.getStatus()).isEqualTo(201);
        subscriber.assertCompleted().awaitItems(0).assertItems();
    }

    @Test
    void extendAndWaitNamespaceNotFound() {
        String namespaceName = UUID.randomUUID().toString();

        RestMulti<OutboundSseEvent> multi = namespaceController.extendAndWait(namespaceDto(namespaceName));
        AssertSubscriber<OutboundSseEvent> subscriber = multi.subscribe().withSubscriber(AssertSubscriber.create(0));

        Optional<Namespace> nsByName = Namespace.findByName(namespaceName);
        assertThat(nsByName).isEmpty();
        assertThat(multi.getStatus()).isEqualTo(404);
        subscriber.assertCompleted().awaitItems(0).assertItems();
    }


    @Test
    void extendAndWait() {
        String namespaceName = UUID.randomUUID().toString();
        Namespace namespace = new Namespace();
        namespace.name = namespaceName;
        namespace.activatedUntil = Instant.EPOCH;
        namespace.persist();

        RestMulti<OutboundSseEvent> multi = namespaceController.extendAndWait(namespaceDto(namespaceName));
        AssertSubscriber<OutboundSseEvent> subscriber = multi.subscribe().withSubscriber(AssertSubscriber.create(0));

        Optional<Namespace> nsByName = Namespace.findByName(namespaceName);
        assertThat(nsByName).isPresent();
        assertThat(nsByName.get().name).isEqualTo(namespaceName);
        assertThat(nsByName.get().activatedUntil).isCloseTo(Instant.now().plus(2, DAYS), within(1, SECONDS));
        assertThat(multi.getStatus()).isEqualTo(200);
        subscriber.assertCompleted().awaitItems(0).assertItems();
    }


    @Test
    void extendAndWaitWithLaterActivatedUntilAlreadySet() {
        String namespaceName = UUID.randomUUID().toString();
        Instant activatedUntil = Instant.now().plus(666, DAYS);
        Namespace namespace = new Namespace();
        namespace.name = namespaceName;
        namespace.activatedUntil = activatedUntil;
        namespace.persist();

        RestMulti<OutboundSseEvent> multi = namespaceController.extendAndWait(namespaceDto(namespaceName));
        AssertSubscriber<OutboundSseEvent> subscriber = multi.subscribe().withSubscriber(AssertSubscriber.create(0));

        Optional<Namespace> nsByName = Namespace.findByName(namespaceName);
        assertThat(nsByName).isPresent();
        assertThat(nsByName.get().name).isEqualTo(namespaceName);
        // nanos are lost in mongodb
        assertThat(nsByName.get().activatedUntil).isCloseTo(activatedUntil, within(1, MILLIS));
        assertThat(multi.getStatus()).isEqualTo(200);
        subscriber.assertCompleted().awaitItems(0).assertItems();
    }
}