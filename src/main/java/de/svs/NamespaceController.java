package de.svs;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestMulti;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Path("/namespace")
public class NamespaceController {

    private static final Logger logger = Logger.getLogger(NamespaceController.class);


    @ConfigProperty(name = "namespace.activationHours", defaultValue = "48")
    int activationHours;

    @ConfigProperty(name = "externalHostName", defaultValue = "localhost")
    String externalHostName;

    @Inject
    NamespaceActivationWaiter namespaceActivationWaiter;

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
                        .map(b -> "You got here because your namespace appears to be deactivated")
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
            case DEACTIVATE -> Instant.EPOCH;
        };

        Optional<Namespace> nsOp = Namespace.findByName(namespace);
        final boolean pollNamespace;
        final String message;
        if (nsOp.isPresent()) {
            Namespace namespaceEntity = nsOp.get();
            namespaceEntity.activatedUntil = activatedUntil;
            namespaceEntity.update();
            if (action == Action.ACTIVATE) {
                message = "namespace " + namespace + " is now activated until " + activatedUntil;
                pollNamespace = true;
            } else {
                message = "namespace " + namespace + " has been deactivated";
                pollNamespace = false;
            }
        } else {
            message = namespace + " not found";
            pollNamespace = false;
        }
        logger.info(message);
        return Templates.namespace(this.externalHostName, namespace, message, pollNamespace);
    }


    @POST
    @Path("/createIfNotExistsAndWait")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public RestMulti<OutboundSseEvent> createIfNotExistsAndWait(NamespaceDto dto) {
        String namespace = dto.getName();
        if (Namespace.findByName(namespace).isPresent()) {
            logger.info("namespace " + namespace + " already present, won't wait");
            return RestMulti.<OutboundSseEvent>fromMultiData(Multi.createFrom().empty())
                    .status(304)
                    .build();
        } else {
            logger.info("creating namespace " + namespace + ", will wait");
            Namespace ne = new Namespace();
            ne.name = namespace;
            ne.activatedUntil = getActivatedUntil();
            ne.persist();
            return RestMulti.fromMultiData(namespaceActivationWaiter.waitForNamespaceToBecomeAvailable(namespace, dto.getMaxWaitTimeInSeconds()))
                    .status(201)
                    .build();
        }
    }

    @POST
    @Path("/extendAndWait")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public RestMulti<OutboundSseEvent> extendAndWait(NamespaceDto dto) {
        String namespace = dto.getName();
        Optional<Namespace> namespaceEntity = Namespace.findByName(namespace);
        if (namespaceEntity.isPresent()) {
            logger.info("extending activation time of " + namespace);
            Instant newActivatedUntil = getActivatedUntil();
            Namespace ns = namespaceEntity.get();
            ns.activatedUntil = (ns.activatedUntil.isAfter(newActivatedUntil) ? ns.activatedUntil : newActivatedUntil);
            ns.update();
            return RestMulti.fromMultiData(namespaceActivationWaiter.waitForNamespaceToBecomeAvailable(namespace, dto.getMaxWaitTimeInSeconds()))
                    .status(200)
                    .build();
        } else {
            logger.info("namespace " + namespace + " not found");
            return RestMulti.<OutboundSseEvent>fromMultiData(Multi.createFrom().empty())
                    .status(404)
                    .build();
        }
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

    @Path("/status")
    @GET()
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<OutboundSseEvent> status(@QueryParam("namespace") String namespace) {
        return namespaceActivationWaiter.waitForNamespaceToBecomeAvailable(namespace, 60);
    }


}
