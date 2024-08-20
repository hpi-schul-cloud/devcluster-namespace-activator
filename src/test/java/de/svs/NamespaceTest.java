package de.svs;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class NamespaceTest {

    @Test
    public void shouldNotBeFoundByNameTest() {
        Namespace namespace = new Namespace();

        namespace.name = "hello";
        namespace.persist();

        assertThat(Namespace.findByName("hello2")).isEmpty();
    }

    @Test
    public void findByNameTest() {
        Namespace namespace = new Namespace();

        String name = "hello";
        namespace.name = name;
        namespace.persist();

        assertThat(Namespace.findByName(name)).contains(namespace);
    }

}