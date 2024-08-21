package de.svs.scheduling;

import de.svs.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class DeleteDeactivatedNamespaces {

    private static final Logger logger = Logger.getLogger(DeleteDeactivatedNamespaces.class);
    private final KubernetesClient kubernetesClient;

    @ConfigProperty(name = "namespace.deletion.afterDaysOfInactivity", defaultValue = "30")
    int afterDaysOfInactivity;

    public DeleteDeactivatedNamespaces(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Scheduled(cron = "{namespace.deletion.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deleteDeactivatedNamespaces(ScheduledExecution execution) {
        Instant thirtyDaysAgo = Instant.now().minus(afterDaysOfInactivity, ChronoUnit.DAYS);
        List<Namespace> namespacesToDelete = Namespace.findByActivatedUntilOlderThan(thirtyDaysAgo);
        logger.info("found namespaces to delete: " + namespacesToDelete.stream().map(ns -> ns.name).toList());
        for (Namespace namespace : namespacesToDelete) {
            logger.info("deleting namespace: " + namespace.name);
            io.fabric8.kubernetes.api.model.Namespace k8sNamespace = kubernetesClient.namespaces().withName(namespace.name).get();
            if (k8sNamespace != null) {
                kubernetesClient.namespaces().withName(namespace.name).delete();
                namespace.delete();
                logger.info("deleted namespace in k8s and db: " + namespace.name);
            } else {
                namespace.delete();
                logger.info("namespace " + namespace.name + " not found, deleted it only from db");
            }
        }
        logger.info("finished deleting namespaces");
    }
}
