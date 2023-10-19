package de.svs;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import io.quarkus.logging.Log;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

@Path("/namespace")
public class Namespace {

    @ConfigProperty(name = "namespace.mongodb.name", defaultValue = "keda")
    String mongoDbName;

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "namespace.activationHours", defaultValue = "48")
    int activationHours;

    @ConfigProperty(name = "externalHostName", defaultValue = "localhost")
    String externalHostName;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance namespace(String host, String defaultNamespace, String message);
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@QueryParam("namespace") Optional<String> namespace, @QueryParam("redirected-from-503") Optional<Boolean> gotRedirectedFrom503) {
        return Templates.namespace(this.externalHostName,
                namespace.orElse(""),
                gotRedirectedFrom503.filter(Boolean::booleanValue)
                        .map(b -> "You got here because your namespace appears to be deactivated")
                        .orElse(null));
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance post(@FormParam("namespace") String namespace, @FormParam("submitType") String submitType) {
        Log.info("post called " + namespace + " " + submitType);
        final Instant activatedUntil = switch (submitType.toLowerCase(Locale.ENGLISH)) {
            case "activate" -> Instant.now().plus(activationHours, ChronoUnit.HOURS);
            case "deactivate" -> Instant.EPOCH;
            default -> throw new IllegalStateException("Unexpected value: " + submitType);
        };

        UpdateResult updateResult = getCollection().updateOne(
                and(eq("name", namespace), exists("activatedUntil", true)),
                set("activatedUntil", activatedUntil));

        final String message;
        if (updateResult.getModifiedCount() > 0) {
            message = "namespace " + namespace + " is now activated until " + activatedUntil;
        } else {
            message = namespace + " not found";
        }
        Log.info(message);
        return Templates.namespace(this.externalHostName, namespace, message);
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
        Log.info("namespace " + dto.getName() + " is now activated until " + activatedUntil);
        return dto;
    }

    private MongoCollection<Document> getCollection() {
        MongoCollection<Document> namespaces = mongoClient.getDatabase(mongoDbName).getCollection("namespaces");
        namespaces.createIndex(eq("name", 1), new IndexOptions().unique(true));
        return namespaces;
    }

}
