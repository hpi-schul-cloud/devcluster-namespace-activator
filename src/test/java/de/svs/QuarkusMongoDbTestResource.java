package de.svs;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MongoDBContainer;

import java.util.Collections;
import java.util.Map;

@QuarkusTestResource(QuarkusMongoDbTestResource.ContainerResource.class)
public class QuarkusMongoDbTestResource {
    public static class ContainerResource implements QuarkusTestResourceLifecycleManager {
        private MongoDBContainer mongoContainer;

        @Override
        public Map<String, String> start() {
            mongoContainer = new MongoDBContainer("mongo:latest")
                    .withExposedPorts(27017);
            mongoContainer.start();

            String connectionString = "mongodb://" + mongoContainer.getHost() + ":" + mongoContainer.getMappedPort(27017);
            return Collections.singletonMap("quarkus.mongodb.connection-string", connectionString);
        }

        @Override
        public void stop() {
            if (mongoContainer != null) {
                mongoContainer.stop();
            }
        }
    }
}
