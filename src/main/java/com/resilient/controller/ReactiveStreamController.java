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
import org.springframework.core.io.buffer.DataBufferFactory;
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
 *
 * <h2>Backpressure Strategy</h2>
 * <ul>
 *   <li>SSE: {@code onBackpressureDrop} applied <em>before</em> map to avoid allocating objects
 *       that will be discarded. Drop events are logged as warnings.</li>
 *   <li>NDJSON: {@code Flux.range} is a cold, cooperative source that already honours
 *       downstream demand natively — no explicit backpressure operator needed.</li>
 *   <li>File: {@code DataBufferUtils.read} streams in configurable chunks; the WebFlux
 *       response sink requests chunks on demand (Reactive Streams pull model).</li>
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

    // ---------------------------------------------------------------------------
    // BACKPRESSURE CONFIG — two separate properties, formerly conflated as one.
    // ---------------------------------------------------------------------------

    /**
     * Maximum number of items to buffer in reactive backpressure buffers (e.g. NDJSON overflow).
     * Kept separate from the file chunk size to avoid semantic confusion.
     * Default: 50 items.
     */
    @Value("${streaming.backpressure.buffer.size:50}")
    private int backpressureBufferSize;

    /**
     * Chunk size (bytes) used when reading files reactively with {@code DataBufferUtils.read}.
     * Using a larger chunk (64 KB default) gives much better I/O throughput than the old
     * 50-byte value that was previously shared with {@code backpressureBufferSize}.
     * Default: 65536 bytes (64 KB).
     */
    @Value("${streaming.file.chunk.bytes:65536}")
    private int fileChunkBytes;

    // Path to a sample file used for the file streaming endpoint.
    private final Path sampleFilePath = Path.of("src/main/resources/sample.json");

    /**
     * Shared, singleton {@link DataBufferFactory} used for file streaming.
     *
     * <p><strong>Backpressure note</strong>: using a shared factory (instead of constructing
     * one per-request) avoids unnecessary object allocation on the hot path. The factory itself
     * is stateless and thread-safe.
     */
    private final DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

    /**
     * Streams user data to the client as Server-Sent Events (SSE).
     * SSE is ideal for pushing real-time updates from the server to the client over a single connection.
     *
     * <p><strong>Backpressure</strong>: {@code Flux.interval} is a hot, timer-driven source that
     * produces items regardless of downstream demand. {@code onBackpressureDrop} is placed
     * <em>before</em> {@code .map()} so that ticks are discarded <em>before</em> the more
     * expensive {@link UserResponse} object is allocated — avoiding wasted CPU when the
     * SSE client (e.g. a slow network connection) cannot keep up.
     *
     * @return A {@link Flux} of {@link UserResponse} objects, which Spring WebFlux will format as an SSE stream.
     */
    @Operation(
            summary = "Stream users via Server-Sent Events (SSE)",
            description =
                    "Pushes 10 user items spaced 1 second apart over a reactive text/event-stream connection."
                            + " Uses onBackpressureDrop (applied before map) so slow clients shed load without blocking the timer thread.")
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
        // Create a stream that emits a new number every second (hot source).
        return Flux.interval(Duration.ofSeconds(1))
                // Limit the stream to 10 emissions, after which it will complete.
                .take(10)
                // [BACKPRESSURE - FIX] onBackpressureDrop is placed HERE, before .map(), so that
                // raw Long ticks are discarded before we allocate a UserResponse object.
                // Previously this was after .map(), wasting allocation on items that get dropped.
                // Strategy: DROP — suitable for SSE where the newest events are most relevant.
                .onBackpressureDrop(tick ->
                        log.warn("[BACKPRESSURE] SSE tick {} dropped — client is slower than 1 event/sec", tick))
                // Transform each surviving tick into a UserResponse.
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
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
     * <p><strong>Backpressure</strong>: {@code Flux.range} is a <em>cold, synchronous, bounded</em>
     * source. It already implements the Reactive Streams pull model natively — it pauses emission
     * until the downstream subscriber requests more items. Therefore <em>no explicit backpressure
     * operator is needed here</em>. Adding {@code onBackpressureBuffer} to a cooperative cold source
     * would be redundant overhead and was removed.
     *
     * @return A {@link Flux} of {@link UserResponse} objects, which Spring WebFlux will format as an NDJSON stream.
     */
    @Operation(
            summary = "Stream users as Newline-Delimited JSON (NDJSON)",
            description = "Streams user JSON objects separated by newlines (application/x-ndjson)."
                    + " Flux.range is a cooperative cold source — backpressure is handled natively by the"
                    + " Reactive Streams pull protocol; no extra buffer operator required.")
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
        // [BACKPRESSURE] Flux.range is a cold, cooperative source: it respects downstream demand
        // by only producing items when the subscriber calls request(n). No onBackpressureBuffer
        // operator is required — the Reactive Streams protocol itself provides the back-pressure.
        return Flux.range(1, 10)
                // Transform each number into a new UserResponse object.
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
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
     * <p><strong>Backpressure</strong>: {@code DataBufferUtils.read} implements the Reactive Streams
     * publisher contract. The WebFlux HTTP response sink requests the next chunk only after the
     * previous one has been written to the network. This provides implicit chunk-level backpressure
     * without any explicit operator. The chunk size is controlled by {@code fileChunkBytes}
     * (default 64 KB), now separate from the reactive buffer size config.
     *
     * @param request The incoming server request, used to check for caching headers.
     * @return A {@link Mono} containing a {@link ResponseEntity} with the file stream or a 304 Not Modified status.
     */
    @Operation(
            summary = "Stream file with conditional HTTP caching",
            description = "Streams file content in non-blocking DataBuffer chunks (default 64 KB) with ETag and"
                    + " Last-Modified caching validation headers. Backpressure is implicit: the response"
                    + " sink pulls one chunk at a time via the Reactive Streams request(n) protocol.")
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
        // [BACKPRESSURE] DataBufferUtils.read publishes file chunks as a Reactive Streams Flux.
        // The WebFlux HTTP layer calls request(1) for each chunk after writing the previous one
        // to the network — providing implicit pull-based backpressure without any extra operator.
        // fileChunkBytes (default 64 KB) controls memory per chunk; larger = fewer round trips.
        // [FIX] Use the shared dataBufferFactory (singleton) instead of allocating one per-request.
        Flux<DataBuffer> data = DataBufferUtils.read(sampleFilePath, dataBufferFactory, fileChunkBytes)
                // Set a timeout for the streaming operation itself.
                .timeout(Duration.ofMinutes(2))
                // [FIX] doOnError is a SIDE-EFFECT operator — throwing inside it causes
                // undefined behaviour and can swallow the original exception.
                // We only log here; the actual exception transformation is done by onErrorMap below.
                .doOnError(ex -> log.error("File streaming error for path {}", sampleFilePath, ex))
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
