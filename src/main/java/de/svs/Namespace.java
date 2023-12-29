package de.svs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import de.svs.status.NamespaceStatus;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

@Path("/namespace")
public class Namespace {

    private static final Logger logger = Logger.getLogger(Namespace.class);

    @ConfigProperty(name = "namespace.mongodb.name", defaultValue = "keda")
    String mongoDbName;

    @Inject
    MongoClient mongoClient;

    @Inject
    Sse sse;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "namespace.activationHours", defaultValue = "48")
    int activationHours;

    @ConfigProperty(name = "externalHostName", defaultValue = "localhost")
    String externalHostName;

    @ConfigProperty(name = "baseDomain")
    String baseDomain;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance namespace(String host, String defaultNamespace, String message, boolean pollNamespace);
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@QueryParam("namespace") Optional<String> namespace, @QueryParam("redirected-from-503") Optional<Boolean> gotRedirectedFrom503) {
        return Templates.namespace(this.externalHostName,
                namespace.orElse(""),
                gotRedirectedFrom503.filter(Boolean::booleanValue)
                        .map(b -> "You got here because your namespace appears to be deactivated")
                        .orElse(null),
                false);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance post(@FormParam("namespace") String namespace, @FormParam("submitType") String submitType) {
        logger.info("post called " + namespace + " " + submitType);
        final Instant activatedUntil = switch (submitType.toLowerCase(Locale.ENGLISH)) {
            case "activate" -> Instant.now().plus(activationHours, ChronoUnit.HOURS);
            case "deactivate" -> Instant.EPOCH;
            default -> throw new IllegalStateException("Unexpected value: " + submitType);
        };

        UpdateResult updateResult = getCollection().updateOne(
                and(eq("name", namespace), exists("activatedUntil", true)),
                set("activatedUntil", activatedUntil));

        final boolean pollNamespace;
        final String message;
        if (updateResult.getModifiedCount() > 0) {
            message = "namespace " + namespace + " is now activated until " + activatedUntil;
            pollNamespace = true;
        } else if (activatedUntil.equals(Instant.EPOCH)) {
            message = "namespace " + namespace + " has been deactivated";
            pollNamespace = false;
        } else {
            message = namespace + " not found";
            pollNamespace = false;
        }
        logger.info(message);
        return Templates.namespace(this.externalHostName, namespace, message, pollNamespace);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public NamespaceDto createNamespaceEntry(NamespaceDto dto) {
        Instant activatedUntil = Instant.now().plus(activationHours, ChronoUnit.HOURS);
        getCollection().updateOne(
                and(eq("name", dto.getName())),
                set("activatedUntil", activatedUntil),
                new UpdateOptions().upsert(true));
        dto.setActivatedUntil(activatedUntil);
        logger.info("namespace " + dto.getName() + " is now activated until " + activatedUntil);
        return dto;
    }

    @Path("/status")
    @GET()
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<OutboundSseEvent> status(@QueryParam("namespace") String namespace) {
        AtomicBoolean finalMessageReceived = new AtomicBoolean();

        return Multi.createBy()
                .repeating()
                .supplier(Unchecked.supplier(() -> new NamespaceStatus(objectMapper, baseDomain).get(namespace)))
                .withDelay(Duration.ofSeconds(2))
                .until(outboundSseEvent -> finalMessageReceived.getAndSet(outboundSseEvent.finalMessage()))
                .map(Unchecked.function(statusDto -> sse.newEventBuilder()
                        .name("namespace-status")
                        .data(String.class, objectMapper.writeValueAsString(statusDto))
                        .build()))
                .select()
                .first(10);
    }

    private MongoCollection<Document> getCollection() {
        MongoCollection<Document> namespaces = mongoClient.getDatabase(mongoDbName).getCollection("namespaces");
        namespaces.createIndex(eq("name", 1), new IndexOptions().unique(true));
        namespaces.createIndex(eq("activatedUntil", 1), new IndexOptions().expireAfter(30L, TimeUnit.DAYS));
        return namespaces;
    }

}
