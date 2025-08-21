package com.resilient.controller;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.resilient.dto.UserResponse;
import com.resilient.model.User;
import io.micrometer.observation.annotation.Observed;
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
 * ReactiveStreamController demonstrates production-grade reactive streaming: - SSE for real-time
 * updates - NDJSON for batch streaming - File streaming with HTTP caching (ETag + Last-Modified)
 */
@RestController
@RequestMapping("/stream")
@Observed
public class ReactiveStreamController {

    private static final Logger log = LoggerFactory.getLogger(ReactiveStreamController.class);

    @Value("${streaming.buffer.size:50}")
    private int bufferSize;

    private final Path sampleFilePath = Path.of("src/main/resources/sample.json");

    /** Streams user data to the client as Server-Sent Events (SSE). */
    @GetMapping(value = "/sse/users", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserResponse> streamUsersSse() {
        return Flux.interval(Duration.ofSeconds(1))
                .take(10)
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
                .onBackpressureBuffer(bufferSize, v -> {}, reactor.core.publisher.BufferOverflowStrategy.DROP_LATEST)
                .timeout(Duration.ofMinutes(2))
                .doOnError(ex -> log.error("SSE streaming error", ex))
                .doFinally(sig -> log.info("SSE stream finished with signal: {}", sig));
    }

    /** Streams user data as newline-delimited JSON (NDJSON). */
    @GetMapping(value = "/ndjson/users", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<UserResponse> streamUsersNdjson() {
        return Flux.range(1, 10)
                .map(i -> UserResponse.from(User.create("User" + i, "user" + i + "@example.com", "User " + i)))
                .onBackpressureBuffer(bufferSize, v -> {}, reactor.core.publisher.BufferOverflowStrategy.DROP_LATEST)
                .timeout(Duration.ofMinutes(2))
                .doOnError(ex -> log.error("NDJSON streaming error", ex))
                .doFinally(sig -> log.info("NDJSON stream finished with signal: {}", sig));
    }

    /** Streams a file to the client reactively, with ETag and Last-Modified caching. */
    @GetMapping(value = "/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Observed(name = "file.stream", contextualName = "stream-file")
    public Mono<ResponseEntity<Flux<DataBuffer>>> streamFile(ServerHttpRequest request) {
        if (!sampleFilePath.toFile().exists()) {
            log.error("Sample file not found: {}", sampleFilePath);
            return Mono.error(new ResponseStatusException(NOT_FOUND, "File not found"));
        }

        long lastModifiedMillis = sampleFilePath.toFile().lastModified();
        long fileSize = sampleFilePath.toFile().length();
        String etag = "\"" + fileSize + "-" + lastModifiedMillis + "\"";

        // Conditional GET handling (ETag)
        List<String> ifNoneMatch = request.getHeaders().getIfNoneMatch();
        if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
            log.info("ETag matched, returning 304 Not Modified");
            return Mono.just(ResponseEntity.status(304)
                    .eTag(etag)
                    .lastModified(lastModifiedMillis)
                    .build());
        }

        // Conditional GET handling (Last-Modified)
        if (request.getHeaders().getIfModifiedSince() >= 0
                && request.getHeaders().getIfModifiedSince() >= lastModifiedMillis) {
            log.info("Last-Modified matched, returning 304 Not Modified");
            return Mono.just(ResponseEntity.status(304)
                    .eTag(etag)
                    .lastModified(lastModifiedMillis)
                    .build());
        }

        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        Flux<DataBuffer> data = DataBufferUtils.read(sampleFilePath, factory, bufferSize)
                .timeout(Duration.ofMinutes(2))
                .doOnError(ex -> {
                    log.error("File streaming error for path {}", sampleFilePath, ex);
                    throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Error streaming file", ex);
                })
                .doFinally(sig -> log.info("File stream completed with signal: {}", sig))
                .onErrorMap(
                        IOException.class,
                        ex -> new ResponseStatusException(INTERNAL_SERVER_ERROR, "File read error", ex));

        return Mono.just(ResponseEntity.ok()
                .eTag(etag)
                .lastModified(lastModifiedMillis)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data));
    }
}
