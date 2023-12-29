package de.svs.status;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NamespaceStatus {

    private static final Logger logger = Logger.getLogger(NamespaceStatus.class);
    private final ObjectMapper objectMapper;
    private final String baseDomain;

    public NamespaceStatus(ObjectMapper objectMapper, String baseDomain) {
        this.objectMapper = objectMapper;
        this.baseDomain = baseDomain;
    }

    public StatusDto get(String namespace) {
        String versionAggregatorJson = "";
        String baseUri = "https://" + namespace + this.baseDomain;
        String uri = baseUri + "/version";
        logger.debug("doing something " + namespace);
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).GET().build();
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(5, TimeUnit.SECONDS);
            logger.debug("got response for " + namespace);

            if (response.statusCode() >= 200 && 300 >= response.statusCode()) {
                versionAggregatorJson = response.body();
                boolean available = !objectMapper.readTree(versionAggregatorJson).get("services-unavailable").asBoolean();
                if (available) {
                    return new StatusDto("available!", baseUri, true, true);
                } else {
                    return new StatusDto("...", baseUri, true, false);
                }
            } else {
                return new StatusDto("/version returned 404 ...", baseUri, true, false);
            }

        } catch (JsonProcessingException e) {
            logger.error(e);
            return new StatusDto("invalid json? " + versionAggregatorJson, baseUri, false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
