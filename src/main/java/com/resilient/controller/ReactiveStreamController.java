package com.resilient.controller;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.resilient.dto.UserResponse;
import com.resilient.model.User;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Demonstrates production-grade reactive streaming techniques in a WebFlux controller.
 *
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>Server-Sent Events (SSE) for real-time, push-based updates.</li>
 *   <li>Newline-Delimited JSON (NDJSON) for efficient streaming of multiple JSON objects.</li>
 *   <li>Large file streaming with support for HTTP caching headers (ETag and Last-Modified).</li>
 * </ul>
 */
@RestController
@RequestMapping("/stream")
@Observed
@Tag(
        name = "Reactive Streaming",
        description =
                "Demonstrates non-blocking streaming patterns (Server-Sent Events, NDJSON, and chunked binary streaming with ETag caching)")
@SecurityRequirement(name = "bearerAuth")
public class ReactiveStreamController {

    // Logger for this controller.
    private static final Logger log = LoggerFactory.getLogger(ReactiveStreamController.class);

    // Configurable buffer size for backpressure handling, defaults to 50.
    @Value("${streaming.buffer.size:50}")
    private int bufferSize;

    // Path to a sample file used for the file streaming endpoint.
    private final Path sampleFilePath = Path.of("src/main/resources/sample.json");

    /**
     * Streams user data to the client as Server-Sent Events (SSE).
     * SSE is ideal for pushing real-time updates from the server to the client over a single connection.
     *
     * @return A {@link Flux} of {@link UserResponse} objects, which Spring WebFlux will format as an SSE stream.
     */
    @Operation(
            summary = "Stream users via Server-Sent Events (SSE)",
            description =
                    "Pushes 10 user items spaced 1 second apart over a reactive text/event-stream connection with backpressure drop handling.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "SSE connection established and active",
                        content =
                                @Content(
                                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                        schema = @Schema(implementation = UserResponse.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT")
            })
    @GetMapping(value = "/sse/users", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserResponse> streamUsersSse() {
        // Create a stream that emits a new number every second.
        return Flux.interval(Duration.ofSeconds(1))
                // Limit the stream to 10 emissions, after which it will complete.
                .take(10)
                // Transform each emitted number into a new UserResponse object.
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
                // If the client (subscriber) cannot process items as fast as they are produced, drop the item and log a
                // warning.
                .onBackpressureDrop(user -> log.warn("Dropping user due to backpressure: {}", user.username()))
                // Set a 2-minute timeout for the entire stream to prevent it from running indefinitely.
                .timeout(Duration.ofMinutes(2))
                // Log any error that terminates the stream.
                .doOnError(ex -> log.error("SSE streaming error", ex))
                // Log a message when the stream finishes for any reason (complete, error, or cancel).
                .doFinally(sig -> log.info("SSE stream finished with signal: {}", sig));
    }

    /**
     * Streams user data as newline-delimited JSON (NDJSON).
     * NDJSON is a convenient format for streaming sequences of JSON objects without a top-level array.
     *
     * @return A {@link Flux} of {@link UserResponse} objects, which Spring WebFlux will format as an NDJSON stream.
     */
    @Operation(
            summary = "Stream users as Newline-Delimited JSON (NDJSON)",
            description =
                    "Streams user JSON objects separated by newlines (application/x-ndjson) with configurable backpressure buffering.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "NDJSON streaming response",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                                        schema = @Schema(implementation = UserResponse.class))),
                @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT")
            })
    @GetMapping(value = "/ndjson/users", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<UserResponse> streamUsersNdjson() {
        // Create a stream of 10 numbers, emitted as quickly as possible.
        return Flux.range(1, 10)
                // Transform each number into a new UserResponse object.
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
                // If the client can't keep up, buffer up to `bufferSize` items. If the buffer fills, drop the latest
                // item.
                .onBackpressureBuffer(bufferSize, v -> {}, reactor.core.publisher.BufferOverflowStrategy.DROP_LATEST)
                // Set a 2-minute timeout for the entire stream.
                .timeout(Duration.ofMinutes(2))
                // Log any error that terminates the stream.
                .doOnError(ex -> log.error("NDJSON streaming error", ex))
                // Log when the stream finishes.
                .doFinally(sig -> log.info("NDJSON stream finished with signal: {}", sig));
    }

    /**
     * Streams a file to the client reactively, with support for HTTP caching headers.
     * This approach is memory-efficient for large files as it streams them in chunks.
     *
     * @param request The incoming server request, used to check for caching headers.
     * @return A {@link Mono} containing a {@link ResponseEntity} with the file stream or a 304 Not Modified status.
     */
    @Operation(
            summary = "Stream file with conditional HTTP caching",
            description =
                    "Streams file content in non-blocking DataBuffer chunks with ETag and Last-Modified caching validation headers.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "File chunk stream successfully initiated",
                        headers = {
                            @Header(
                                    name = "ETag",
                                    description = "Entity tag for cache validation",
                                    schema = @Schema(type = "string")),
                            @Header(
                                    name = "Last-Modified",
                                    description = "Last modification epoch timestamp",
                                    schema = @Schema(type = "integer"))
                        },
                        content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
                @ApiResponse(
                        responseCode = "304",
                        description =
                                "Not Modified (cached content is fresh based on If-None-Match or If-Modified-Since)",
                        content = @Content),
                @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT"),
                @ApiResponse(responseCode = "404", description = "Target sample file not found"),
                @ApiResponse(responseCode = "500", description = "I/O streaming error")
            })
    @GetMapping(value = "/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Observed(name = "file.stream", contextualName = "stream-file")
    public Mono<ResponseEntity<Flux<DataBuffer>>> streamFile(
            @Parameter(hidden = true) ServerHttpRequest request,
            @Parameter(
                            name = "If-None-Match",
                            in = ParameterIn.HEADER,
                            description = "ETag from previous response for conditional GET",
                            required = false,
                            example = "\"184-1724745600000\"")
                    @RequestHeader(name = "If-None-Match", required = false)
                    String ifNoneMatchHeader,
            @Parameter(
                            name = "If-Modified-Since",
                            in = ParameterIn.HEADER,
                            description = "Timestamp for conditional GET",
                            required = false)
                    @RequestHeader(name = "If-Modified-Since", required = false)
                    String ifModifiedSinceHeader) {
        // First, check if the requested file actually exists on the server.
        if (!sampleFilePath.toFile().exists()) {
            log.error("Sample file not found: {}", sampleFilePath);
            return Mono.error(new ResponseStatusException(NOT_FOUND, "File not found"));
        }

        // Get file metadata to generate caching headers.
        long lastModifiedMillis = sampleFilePath.toFile().lastModified();
        long fileSize = sampleFilePath.toFile().length();
        // Create a strong ETag based on file size and last modification time.
        String etag = "\"" + fileSize + "-" + lastModifiedMillis + "\"";

        // --- Conditional GET Handling: ETag ---
        // Check if the client sent an 'If-None-Match' header.
        List<String> ifNoneMatch = request.getHeaders().getIfNoneMatch();
        if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
            // If the client's ETag matches ours, the file is unchanged.
            log.info("ETag matched, returning 304 Not Modified");
            // Return an HTTP 304 response to tell the client to use its cached version.
            return Mono.just(ResponseEntity.status(304)
                    .eTag(etag)
                    .lastModified(lastModifiedMillis)
                    .build());
        }

        // --- Conditional GET Handling: Last-Modified ---
        // Check if the client sent an 'If-Modified-Since' header.
        if (request.getHeaders().getIfModifiedSince() >= 0
                && request.getHeaders().getIfModifiedSince() >= lastModifiedMillis) {
            // If the file has not been modified since the client's cached date, return 304.
            log.info("Last-Modified matched, returning 304 Not Modified");
            return Mono.just(ResponseEntity.status(304)
                    .eTag(etag)
                    .lastModified(lastModifiedMillis)
                    .build());
        }

        // --- File Streaming ---
        // If no cache match, stream the file content.
        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        // Read the file reactively in chunks of `bufferSize` bytes. This is non-blocking and memory-efficient.
        Flux<DataBuffer> data = DataBufferUtils.read(sampleFilePath, factory, bufferSize)
                // Set a timeout for the streaming operation itself.
                .timeout(Duration.ofMinutes(2))
                // Define what to do if an error occurs during the stream.
                .doOnError(ex -> {
                    log.error("File streaming error for path {}", sampleFilePath, ex);
                    // Throw a web-friendly exception if something goes wrong.
                    throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Error streaming file", ex);
                })
                // Log when the file stream completes, errors, or is cancelled.
                .doFinally(sig -> log.info("File stream completed with signal: {}", sig))
                // Specifically map IOExceptions to a 500 Internal Server Error.
                .onErrorMap(
                        IOException.class,
                        ex -> new ResponseStatusException(INTERNAL_SERVER_ERROR, "File read error", ex));

        // Build the HTTP 200 OK response with caching headers and the file stream as the body.
        return Mono.just(ResponseEntity.ok()
                .eTag(etag)
                .lastModified(lastModifiedMillis)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data));
    }
}
