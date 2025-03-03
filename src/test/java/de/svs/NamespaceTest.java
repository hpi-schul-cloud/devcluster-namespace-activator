package de.svs;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@WithTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
class NamespaceTest {

    @BeforeEach
    public void beforeEach() {
        Namespace.deleteAll();
    }

    @Test
    public void shouldNotBeFoundByNameTest() {
        persistNamespace("buh");

        assertThat(Namespace.findByName("hello2")).isEmpty();
    }

    @Test
    public void findByNameTest() {
        String name = "hello";
        Namespace namespace = persistNamespace(name);

        assertThat(Namespace.findByName(name)).contains(namespace);
    }

    @Test
    public void findByActivatedUntilOlderThanTest() {
        Instant threeMinutesAgo = Instant.now().minus(3, MINUTES);
        Instant fourMinutesAgo = Instant.now().minus(4, MINUTES);
        Instant fiveMinutesAgo = Instant.now().minus(5, MINUTES);

        String fiveMinutesAgoNamespace = "fiveMinutesAgoNamespace";
        persistNamespace("threeMinutesAgoNamespace", threeMinutesAgo);
        persistNamespace(fiveMinutesAgoNamespace, fiveMinutesAgo);

        List<Namespace> byActivatedUntilOlderThan = Namespace.findByActivatedUntilOlderThan(fourMinutesAgo);

        assertThat(byActivatedUntilOlderThan).extracting(ns -> ns.name).containsOnly(fiveMinutesAgoNamespace);
    }

    @Test
    public void testUpdateActivatedUntilIfLater() {
        Instant now = Instant.now();
        Instant laterInstant = now.plus(1, DAYS);
        Namespace ns = persistNamespace("gurke", now);
        ns.updateActivatedUntilIfLater(laterInstant);
        assertThat(ns.activatedUntil).isEqualTo(laterInstant);
    }

    @Test
    public void testUpdateActivatedUntilWhenCurrentIsLater() {
        Instant now = Instant.now();
        Instant earlierInstant = now.minus(1, DAYS);
        Namespace ns = persistNamespace("gummi", now);
        ns.updateActivatedUntilIfLater(earlierInstant);
        assertThat(ns.activatedUntil).isEqualTo(now);
    }

    private static Namespace persistNamespace(String name, Instant activatedUntil) {
        Namespace namespace = Namespace.create(name, activatedUntil);

        namespace.persist();
        return namespace;
    }

    private static Namespace persistNamespace(String name) {
        return persistNamespace(name, Instant.now());
    }

}