package de.svs.scheduling;

import de.svs.Namespace;
import de.svs.QuarkusMongoDbTestResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

@WithKubernetesTestServer
@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class DeleteDeactivatedNamespacesTest {

    @Inject
    DeleteDeactivatedNamespaces deleteDeactivatedNamespaces;

    @Inject
    KubernetesClient k8sClient;

    @BeforeEach
    public void beforeEach() {
        Namespace.deleteAll();
        k8sClient.namespaces().delete();
    }

    @Test
    void cleanUpInactiveNamespaces() {
        Instant instantToBeDeleted = Instant.now().minus(300, DAYS);
        Instant justNow = Instant.now();

        Namespace namespaceThatDoesNotExistInK8s = persistNamespaceWithActivatedUntil("namespaceThatDoesNotExistInK8s", instantToBeDeleted);
        Namespace namespaceThatExistInK8s = persistNamespaceWithActivatedUntil("namespaceThatExistInK8s", instantToBeDeleted);
        Namespace freshNamespace = persistNamespaceWithActivatedUntil("freshNamespace", justNow);

        createK8sNamespace(namespaceThatExistInK8s);
        createK8sNamespace(freshNamespace);

        deleteDeactivatedNamespaces.syncAndCleanup(scheduledExecution());

        assertThat(Namespace.findByIdOptional(namespaceThatDoesNotExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(namespaceThatExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(freshNamespace.id)).isNotEmpty();

        assertThat(k8sClient.namespaces().withName(namespaceThatExistInK8s.name).get()).isNull();
        assertThat(k8sClient.namespaces().withName(freshNamespace.name).get()).isNotNull();
    }

    @Test
    void syncDatabaseWithKubernetesNamespaces() {
        Namespace namespaceThatExistsInBothPlaces = persistNamespaceWithCustomOCreationDate("inBothPlacesAndOldEnough", Instant.now().minus(15, MINUTES));
        createK8sNamespace(namespaceThatExistsInBothPlaces);

        Namespace inBothPlacesButNotOldEnough = persistNamespaceWithCustomOCreationDate("inBothPlacesButNotOldEnough", Instant.now());
        createK8sNamespace(inBothPlacesButNotOldEnough);

        Namespace namespaceThatExistOnlyInK8s = new Namespace();
        namespaceThatExistOnlyInK8s.name = "namespaceThatExistOnlyInK8s";
        createK8sNamespace(namespaceThatExistOnlyInK8s);
        Namespace freshNamespaceOnlyInDb = persistNamespaceWithCustomOCreationDate("freshNamespaceOnlyInDb", Instant.now());
        persistNamespaceWithCustomOCreationDate("oldNamespaceOnlyInDb", Instant.now().minus(15, MINUTES));

        deleteDeactivatedNamespaces.syncAndCleanup(scheduledExecution());

        assertThat(Namespace.getAll())
                .extracting(namespace -> namespace.name)
                .containsExactlyInAnyOrder(namespaceThatExistsInBothPlaces.name,
                        inBothPlacesButNotOldEnough.name,
                        freshNamespaceOnlyInDb.name);

        assertThat(k8sClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(ns -> ns.getMetadata().getName()))
                .containsExactlyInAnyOrder(inBothPlacesButNotOldEnough.name,
                        namespaceThatExistsInBothPlaces.name,
                        namespaceThatExistOnlyInK8s.name);
    }

    private void createK8sNamespace(Namespace namespaceThatExistInK8s) {
        io.fabric8.kubernetes.api.model.Namespace k8sNamespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespaceThatExistInK8s.name)
                .and()
                .build();

        k8sClient.resource(k8sNamespace).create();
    }

    private static @NotNull ScheduledExecution scheduledExecution() {
        return new ScheduledExecution() {
            @Override
            public Trigger getTrigger() {
                return null;
            }

            @Override
            public Instant getFireTime() {
                return null;
            }

            @Override
            public Instant getScheduledFireTime() {
                return null;
            }
        };
    }

    private static int counter = 1;

    private static Namespace persistNamespaceWithCustomOCreationDate(String name, Instant creationDate) {
        Namespace namespace = Namespace.create(name, Instant.now());
        namespace.id = new ObjectId((int) creationDate.getEpochSecond(), ++counter);
        namespace.persist();
        return namespace;
    }

    private static Namespace persistNamespaceWithActivatedUntil(String name, Instant activatedUntil) {
        Namespace namespace = Namespace.create(name, activatedUntil);

        namespace.persist();
        return namespace;
    }
}