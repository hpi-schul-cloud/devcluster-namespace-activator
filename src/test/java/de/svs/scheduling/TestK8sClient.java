package de.svs.scheduling;

import de.svs.QuarkusMongoDbTestResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@WithKubernetesTestServer
@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
public class TestK8sClient {

    @Inject
    KubernetesClient k8sClient;

    @Test
    public void testK8sClient() throws InterruptedException {
        System.out.println(k8sClient.namespaces().list().getItems().stream().map(Namespace::getMetadata).map(ObjectMeta::getName).collect(Collectors.toSet()));
        k8sClient.namespaces().delete();
        System.out.println(k8sClient.namespaces().list().getItems().stream().map(Namespace::getMetadata).map(ObjectMeta::getName).collect(Collectors.toSet()));
        k8sClient.namespaces().withName("hugo").delete();
        System.out.println(k8sClient.namespaces().list().getItems().stream().map(Namespace::getMetadata).map(ObjectMeta::getName).collect(Collectors.toSet()));
        k8sClient.namespaces().delete();
        System.out.println(k8sClient.namespaces().list().getItems().stream().map(Namespace::getMetadata).map(ObjectMeta::getName).collect(Collectors.toSet()));
        k8sClient.namespaces().withName("default").delete();

    }

    private void createK8sNamespace(String name) {
        io.fabric8.kubernetes.api.model.Namespace k8sNamespace = new NamespaceBuilder()
                .withNewMetadata()
                .withName(name)
                .and()
                .build();

        k8sClient.resource(k8sNamespace).create();
    }
}
