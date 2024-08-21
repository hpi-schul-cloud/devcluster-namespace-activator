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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

@WithKubernetesTestServer
@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class DeleteDeactivatedNamespacesTest {

    @Inject
    DeleteDeactivatedNamespaces deleteDeactivatedNamespaces;

    @Inject
    KubernetesClient k8sClient;

    @Test
    void deleteDeactivatedNamespaces() {
        Instant instantToBeDeleted = Instant.now().minus(300, DAYS);
        Instant justNow = Instant.now();

        Namespace namespaceThatDoesNotExistInK8s = persistNamespace("namespaceThatDoesNotExistInK8s", instantToBeDeleted);
        Namespace namespaceThatExistInK8s = persistNamespace("namespaceThatExistInK8s", instantToBeDeleted);
        Namespace freshNamespace = persistNamespace("freshNamespace", justNow);

        createK8sNamespace(namespaceThatExistInK8s);
        createK8sNamespace(freshNamespace);

        deleteDeactivatedNamespaces.deleteDeactivatedNamespaces(scheduledExecution());

        assertThat(Namespace.findByIdOptional(namespaceThatDoesNotExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(namespaceThatExistInK8s.id)).isEmpty();
        assertThat(Namespace.findByIdOptional(freshNamespace.id)).isNotEmpty();

        assertThat(k8sClient.namespaces().withName(namespaceThatExistInK8s.name).get()).isNull();
        assertThat(k8sClient.namespaces().withName(freshNamespace.name).get()).isNotNull();

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

    private static Namespace persistNamespace(String name, Instant activatedUntil) {
        Namespace namespace = Namespace.create(name, activatedUntil);

        namespace.persist();
        return namespace;
    }
}