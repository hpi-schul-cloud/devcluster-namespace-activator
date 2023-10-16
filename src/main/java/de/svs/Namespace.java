package de.svs;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
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

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance namespace(String defaultNamespace);
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@QueryParam("namespace") Optional<String> namespace) {
        return Templates.namespace(namespace.orElse(""));
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

        getCollection().updateOne(
                and(eq("name", namespace), exists("activatedUntil", true)),
                set("activatedUntil", activatedUntil));

        return Templates.namespace(namespace);
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
        return dto;
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(mongoDbName).getCollection("namespaces");
    }

}
