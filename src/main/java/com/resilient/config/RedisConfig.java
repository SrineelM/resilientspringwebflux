package com.resilient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Configures the connection to Redis for production environments.
 * <p>
 * This configuration is only active when the "prod" profile is enabled. It sets up
 * the necessary beans to interact with a Redis server, which is typically used for
 * features like distributed rate limiting or caching in a production setting.
 */
@Configuration
@Profile("prod")
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Creates a reactive Redis connection factory using Lettuce.
     * <p>
     * This bean establishes the low-level connection to the Redis server. The host and port
     * are configured via {@code spring.data.redis.host} and {@code spring.data.redis.port}
     * properties, with defaults to localhost:6379.
     *
     * @return A {@link ReactiveRedisConnectionFactory} instance.
     */
    @Bean
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * Creates a {@link ReactiveStringRedisTemplate} for high-level reactive Redis operations.
     * <p>
     * This template is specialized for string-based interactions and uses string serializers
     * for keys and values, which is common for many use cases like caching and rate limiting.
     *
     * @param factory The {@link ReactiveRedisConnectionFactory} to use for the template.
     * @return A configured {@link ReactiveStringRedisTemplate}.
     */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory, RedisSerializationContext.string());
    }
}
