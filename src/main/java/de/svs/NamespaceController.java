package de.svs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.svs.status.NamespaceStatus;
import de.svs.status.StatusDto;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Path("/namespace")
public class NamespaceController {

    private static final Logger logger = Logger.getLogger(NamespaceController.class);

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

    private Instant getActivatedUntil() {
        return Instant.now().plus(activationHours, ChronoUnit.HOURS);
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get(@QueryParam("namespace") Optional<String> namespace, @QueryParam("redirected-from-503") Optional<Boolean> gotRedirectedFrom503) {
        return Templates.namespace(this.externalHostName,
                namespace.orElse(""),
                gotRedirectedFrom503.filter(Boolean::booleanValue)
                        .map(_ -> "You got here because your namespace appears to be deactivated")
                        .orElse(null),
                false);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance post(@FormParam("namespace") String namespace, @FormParam("action") Action action) {
        logger.info("post called " + namespace + " " + action);

        final Instant activatedUntil = switch (action) {
            case ACTIVATE -> getActivatedUntil();
        };

        Optional<Namespace> nsOp = Namespace.findByName(namespace);
        final boolean pollNamespace;
        final String message;
        if (nsOp.isPresent()) {
            Namespace namespaceEntity = nsOp.get();
            namespaceEntity.updateActivatedUntilIfLater(activatedUntil);
            namespaceEntity.update();
            message = "namespace " + namespace + " is now activated until " + activatedUntil;
            pollNamespace = true;
        } else {
            message = namespace + " not found";
            pollNamespace = false;
        }
        logger.info(message);
        return Templates.namespace(this.externalHostName, namespace, message, pollNamespace);
    }


    @POST
    @Path("/createIfNotExistsAndWait")
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public void testBlocking(NamespaceDto dto, @Context RoutingContext ctx) {
        String namespace = dto.getName();
        var response = ctx.response();

        if (Namespace.findByName(namespace).isPresent()) {
            logger.info("attempted to create namespace " + namespace + " but already present, won't wait");
            response.setStatusCode(304).end("already exists\n");
        } else {
            logger.info("creating namespace " + namespace + ", will wait");

            Namespace ne = new Namespace();
            ne.name = namespace;
            ne.activatedUntil = getActivatedUntil();
            ne.persist();

            pollAndStreamStatus(dto, response);
        }
    }

    @POST
    @Path("/extendAndWait")
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public void extendAndWait(NamespaceDto dto, @Context RoutingContext ctx) {
        String namespace = dto.getName();
        Optional<Namespace> namespaceEntity = Namespace.findByName(namespace);
        HttpServerResponse response = ctx.response();
        if (namespaceEntity.isPresent()) {
            logger.info("extending activation time of " + namespace);
            Namespace ns = namespaceEntity.get();
            ns.updateActivatedUntilIfLater(getActivatedUntil());
            ns.update();
            pollAndStreamStatus(dto, response);
        } else {
            logger.info("namespace (" + namespace + ") to extend activation time has not been not found");
            response.setStatusCode(404);
            response.end();
        }
    }

    @Path("/status")
    @GET()
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public void status(@QueryParam("namespace") String namespace, @Context RoutingContext ctx) {
        NamespaceDto dto = new NamespaceDto();
        dto.setName(namespace);
        dto.setMaxWaitTimeInSeconds(120);
        pollAndStreamStatus(dto, ctx.response());
    }

    private void pollAndStreamStatus(NamespaceDto dto, HttpServerResponse response) {
        response.setChunked(true);
        response.putHeader("Content-Type", "text/plain");

        int delayInSeconds = 2;
        int maxWaitTimeInSeconds = dto.getMaxWaitTimeInSeconds();
        int maxTries = maxWaitTimeInSeconds / delayInSeconds;

        for (int i = 0; i < maxTries; i++) {
            try {
                StatusDto status = new NamespaceStatus(objectMapper, baseDomain).get(dto.getName());
                String json = objectMapper.writeValueAsString(status);
                response.write(json + "\n");

                if (status.finalMessage()) {
                    response.write("done\n");
                    response.end();
                    return;
                } else {
                    TimeUnit.SECONDS.sleep(delayInSeconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.write("interrupted\n");
                response.end();
                return;
            } catch (JsonProcessingException e) {
                logger.error("Error during polling", e);
                response.write("error: " + e.getMessage() + "\n");
                response.end();
                return;
            }
        }

        response.write("timeout\n");
        response.end();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public NamespaceDto createNamespaceEntry(NamespaceDto dto) {
        Instant activatedUntil = getActivatedUntil();

        Namespace namespace = new Namespace();
        namespace.name = dto.getName();
        namespace.activatedUntil = activatedUntil;
        namespace.persist();

        dto.setActivatedUntil(activatedUntil);
        logger.info("namespace " + dto.getName() + " is now activated until " + activatedUntil);
        return dto;
    }

}
