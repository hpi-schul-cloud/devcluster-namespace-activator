package de.svs.og;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@Path("/og")
public class RandomGiphyGif {

    @ConfigProperty(name = "giphyapikey")
    Optional<String> giphyApiKey;


    @Path("/image.gif")
    @GET
    @Produces("image/gif")
    public RestResponse<StreamingOutput> getRandomGif() throws InterruptedException, IOException, URISyntaxException {
        if (giphyApiKey.isPresent()) {
            String apiKey = giphyApiKey.get();

            URI uri = URI.create("https://api.giphy.com/v1/gifs/random?api_key=" + apiKey + "&tag=cat");
            String gihpyUrl = new ObjectMapper().readTree(uri.toURL()).get("data").get("images").get("original").get("url").textValue();

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(new URI(gihpyUrl)).GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // stream gets closed after it has been fully copied
            InputStream inputStream = response.body();
            return RestResponse.ok(inputStream::transferTo, "image/gif");
        } else {
            return RestResponse.notFound();
        }
    }

}
