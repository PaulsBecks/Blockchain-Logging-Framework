package blf.core.writers;

import blf.core.exceptions.ExceptionHandler;
import io.reactivex.annotations.NonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LogWriter
 */
public class HttpWriter extends DataWriter {
    private static final Logger LOGGER = Logger.getLogger(LogWriter.class.getName());

    private final List<HttpRequest> httpRequests;
    private HttpClient httpClient;

    public HttpWriter() {
        this.httpRequests = new LinkedList<>();
        this.httpClient = HttpClient.newBuilder().build();
    }

    public void addHttpRequest(String uri, String json) {
        LOGGER.info(String.format("Add new HTTP request with URI: %s and JSON: %s", uri, json));
        httpRequests.add(
            HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
        );
    }

    @Override
    protected void writeState(String currentBlock) {
        for (HttpRequest request : httpRequests) {
            LOGGER.info(String.format("Send request: %s", request.toString()));
            try {
                this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                LOGGER.info("Error when sending http request: " + e.toString());
            }
        }
    }

    @Override
    protected void deleteState() {
        this.httpRequests.clear();
    }
}
