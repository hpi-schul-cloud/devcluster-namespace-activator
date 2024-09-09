package de.svs.metrics;

import de.svs.Namespace;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Startup
@ApplicationScoped
public class CustomNamespaceActivatorMetrics {

    @Inject
    public CustomNamespaceActivatorMetrics(MeterRegistry registry) {
        Gauge.builder("active_namespaces", Namespace::countActiveNamespaces)
                .description("Number of active namespaces")
                .register(registry);

        Gauge.builder("total_namespaces", Namespace::countTotalNamespaces)
                .description("Total number of namespaces known by the activator")
                .register(registry);
    }

}
