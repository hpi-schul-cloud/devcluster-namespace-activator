package de.svs.scheduling;

import de.svs.Namespace;
import de.svs.QuarkusMongoDbTestResource;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class DeleteDeactivatedNamespacesTest {

    @Inject
    DeleteDeactivatedNamespaces deleteDeactivatedNamespaces;

    @Inject
    KubernetesClient k8sClient;

    private static final Set<String> namespacesToIgnore = Set.of("default", "kube-system", "kube-public", "kube-node-lease");
    private static final Predicate<String> ignoreDefaultNamespaces = namespaceName -> !namespacesToIgnore.contains(namespaceName);


    @BeforeEach
    public void beforeEach() {
        Namespace.deleteAll();
        k8sClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(ns -> ns.getMetadata().getName())
                .filter(ignoreDefaultNamespaces)
                .forEach(namespaceName -> {
                    k8sClient.namespaces().withName(namespaceName).delete();
                });
    }

    @Test
    void cleanUpInactiveNamespaces() {
        Instant instantToBeDeleted = Instant.now().minus(300, DAYS);
        Instant justNow = Instant.now();

        Namespace protectedNamespace = persistNamespaceWithActivatedUntil("main", instantToBeDeleted);
        Namespace namespaceThatDoesNotExistInK8s = persistNamespaceWithActivatedUntil("namespace-that-does-not-exist-in-k8s", instantToBeDeleted);
        Namespace namespaceThatExistInK8s = persistNamespaceWithActivatedUntil("namespace-that-exist-in-k8s", instantToBeDeleted);
        Namespace freshNamespace = persistNamespaceWithActivatedUntil("fresh-namespace", justNow);

        createK8sNamespace(protectedNamespace);
        createK8sNamespace(namespaceThatExistInK8s);
        createK8sNamespace(freshNamespace);

        deleteDeactivatedNamespaces.syncAndCleanup(scheduledExecution());
        System.out.println(k8sClient.namespaces().list().getItems().stream().map(io.fabric8.kubernetes.api.model.Namespace::getMetadata).map(ObjectMeta::getName).collect(Collectors.toSet()));
        assertThat(Namespace.findByIdOptional(namespaceThatDoesNotExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(namespaceThatExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(freshNamespace.id)).isNotEmpty();
        assertThat(Namespace.findByIdOptional(protectedNamespace.id)).isNotEmpty();

        assertThat(isNamespaceDeletedOrTerminating(namespaceThatExistInK8s.name)).isTrue();
        assertThat(isNamespaceExistingAndActive(freshNamespace.name)).isTrue();
        assertThat(isNamespaceExistingAndActive(protectedNamespace.name)).isTrue();
    }

    @Test
    void syncDatabaseWithKubernetesNamespaces() throws InterruptedException {
        Namespace namespaceThatExistsInBothPlaces = persistNamespaceWithCustomOCreationDate("in-both-places-and-old-enough", Instant.now().minus(15, MINUTES));
        createK8sNamespace(namespaceThatExistsInBothPlaces);

        Namespace inBothPlacesButNotOldEnough = persistNamespaceWithCustomOCreationDate("in-both-places-but-not-old-enough", Instant.now());
        createK8sNamespace(inBothPlacesButNotOldEnough);

        Namespace namespaceThatExistOnlyInK8s = new Namespace();
        namespaceThatExistOnlyInK8s.name = "namespace-that-exist-only-in-k8s";
        createK8sNamespace(namespaceThatExistOnlyInK8s);
        Namespace freshNamespaceOnlyInDb = persistNamespaceWithCustomOCreationDate("fresh-namespace-only-in-db", Instant.now());
        persistNamespaceWithCustomOCreationDate("old-namespace-only-in-db", Instant.now().minus(15, MINUTES));

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
                .filter(ns -> "Active".equalsIgnoreCase(ns.getStatus().getPhase()))
                .map(ns -> ns.getMetadata().getName())
                .filter(ignoreDefaultNamespaces))
                .containsExactlyInAnyOrder(inBothPlacesButNotOldEnough.name,
                        namespaceThatExistsInBothPlaces.name,
                        namespaceThatExistOnlyInK8s.name);
    }

    private boolean isNamespaceDeletedOrTerminating(String name) {
        io.fabric8.kubernetes.api.model.Namespace namespace = k8sClient.namespaces().withName(name).get();
        return namespace == null || "Terminating".equalsIgnoreCase(namespace.getStatus().getPhase());
    }

    private boolean isNamespaceExistingAndActive(String name) {
        io.fabric8.kubernetes.api.model.Namespace namespace = k8sClient.namespaces().withName(name).get();
        return namespace != null && "Active".equalsIgnoreCase(namespace.getStatus().getPhase());
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