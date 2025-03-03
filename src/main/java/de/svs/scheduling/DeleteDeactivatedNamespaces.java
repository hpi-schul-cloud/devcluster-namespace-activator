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
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeleteDeactivatedNamespaces {

    private static final Logger logger = Logger.getLogger(DeleteDeactivatedNamespaces.class);
    private final KubernetesClient kubernetesClient;

    @ConfigProperty(name = "namespace.sync-db-k8s.afterDaysOfInactivity", defaultValue = "30")
    int afterDaysOfInactivity;

    @ConfigProperty(name = "namespace.sync-db-k8s.removeDeletedNamespacesFromDatabase", defaultValue = "true")
    boolean removeDeletedNamespacesFromDatabase;

    @ConfigProperty(name = "namespace.sync-db-k8s.namespacesNotToRemove", defaultValue = "main")
    List<String> namespacesNotToDelete;

    public DeleteDeactivatedNamespaces(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Scheduled(cron = "{namespace.sync-db-k8s.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void syncAndCleanup(ScheduledExecution execution) {
        cleanUpInactiveNamespaces();
        if (removeDeletedNamespacesFromDatabase) {
            syncDatabaseWithKubernetesNamespaces();
        } else {
            logger.info("removing Namespaces from Database has been disabled");
        }
    }

    /**
     * Deletes namespaces from the database and Kubernetes cluster after a configurable period of inactivity.
     * <p>
     * Inactivity is determined based on the last activation date of the namespace. The cleanup is triggered
     * if the namespace has not been activated within the defined period.
     * <p>
     * Example:
     * - Default activation time: 2 days
     * - Deletion period after inactivity: 30 days
     * <p>
     * Scenario:
     * - Namespace created on 01.11.2024 and activated on the same day
     * - No further activations after 01.11.2024
     * - Deletion date: 01.11.2024 + 2 days (activation time) + 30 days (inactivity period) = 03.12.2024
     * <p>
     * On 03.12.2024, the namespace will be deleted.
     */
    private void cleanUpInactiveNamespaces() {
        Instant xDaysAgo = Instant.now().minus(afterDaysOfInactivity, ChronoUnit.DAYS);
        List<Namespace> namespacesToDelete = Namespace.findByActivatedUntilOlderThan(xDaysAgo);
        logger.info("found namespaces to delete: " + namespacesToDelete.stream().map(ns -> ns.name).toList());
        for (Namespace namespaceFromDb : namespacesToDelete) {
            if (namespacesNotToDelete.contains(namespaceFromDb.name)) {
                logger.warn("attempted to delete protected namespace: " + namespaceFromDb.name);
            } else{
                logger.info("deleting namespace: " + namespaceFromDb.name + " it's last activation time was: " + namespaceFromDb.activatedUntil);
                io.fabric8.kubernetes.api.model.Namespace k8sNamespace = kubernetesClient.namespaces().withName(namespaceFromDb.name).get();
                if (k8sNamespace != null) {
                    kubernetesClient.namespaces().withName(namespaceFromDb.name).delete();
                    namespaceFromDb.delete();
                    logger.info("deleted namespace in k8s and db: " + namespaceFromDb.name);
                } else {
                    namespaceFromDb.delete();
                    logger.info("namespace " + namespaceFromDb.name + " not found, deleted it only from db");
                }
            }
        }
        logger.info("finished deleting namespaces");
    }

    /**
     * Removes namespaces from the database that no longer exist in the Kubernetes cluster.
     * <p>
     * This method ensures that the database contains only active namespaces,
     * improving the accuracy of metrics and preventing stale data.
     * <p>
     * Behavior:
     * - The method checks all namespaces stored in the database.
     * - If a namespace is not found in the Kubernetes cluster's namespace list, it is deleted from the database.
     * - A grace period of 5 minutes is applied for newly created namespaces to ensure they have time to sync with Kubernetes.
     * <p>
     * Notes:
     * - The method runs periodically, as defined by the configured cron expression.
     */
    private void syncDatabaseWithKubernetesNamespaces() {
        Set<String> k8sNamespaceList = kubernetesClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(namespace -> namespace.getMetadata().getName())
                .collect(Collectors.toSet());

        for (Namespace namespaceFromDb : Namespace.getAll()) {
            logger.debug("checking namespace " + namespaceFromDb.name + " " + namespaceFromDb.id.getDate());
            // grace period, if namespace has just been created but is not known to k8s yet
            Instant instantFromDb = Instant.ofEpochSecond(namespaceFromDb.id.getTimestamp());
            Instant instantFromDbWithGracePeriod = instantFromDb.plus(5, ChronoUnit.MINUTES);
            if (Instant.now().isAfter(instantFromDbWithGracePeriod)) {
                if (!k8sNamespaceList.contains(namespaceFromDb.name)) {
                    logger.info("deleting namespace in database: " + namespaceFromDb.name);
                    namespaceFromDb.delete();
                } else {
                    logger.debug("namespace " + namespaceFromDb.name + " found in k8s");
                }
            } else {
                logger.info("namespace " + namespaceFromDb.name + " ignored since it is not old enough for automatic removal from db");
            }
        }
    }
}
