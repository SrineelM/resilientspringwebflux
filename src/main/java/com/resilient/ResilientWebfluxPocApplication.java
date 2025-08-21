package com.resilient;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

/** Main entry point for the Resilient Spring WebFlux Proof of Concept application. */
@SpringBootApplication
public class ResilientWebfluxPocApplication {

    private static final Logger logger = LoggerFactory.getLogger(ResilientWebfluxPocApplication.class);

    public static void main(String[] args) {
        long start = System.nanoTime();

        if (isDebugEnabled()) {
            logger.info("Reactor operator debugging enabled.");
            Hooks.onOperatorDebug();
        }

        printCustomBanner();
        logJvmAndGcInfo();

        SpringApplication.run(ResilientWebfluxPocApplication.class, args);

        logger.info("Application started in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    /** Prints a custom startup banner. */
    private static void printCustomBanner() {
        logger.info("\n============================================");
        logger.info("   Resilient Spring WebFlux POC (Java 17)");
        logger.info("   Cloud Native | Secure | Observable");
        logger.info("============================================\n");
    }

    /** Logs JVM, GC, and memory info at startup. */
    private static void logJvmAndGcInfo() {
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024L * 1024L;
        logger.info("JVM Info:");
        logger.info("  Java Version: {}", System.getProperty("java.version"));
        logger.info("  JVM: {} ({})", System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
        logger.info("  Available processors: {}", runtime.availableProcessors());
        logger.info("  Max Memory: {} MB", runtime.maxMemory() / mb);
        logger.info("  Total Memory: {} MB", runtime.totalMemory() / mb);
        logger.info("  Free Memory: {} MB", runtime.freeMemory() / mb);

        ManagementFactory.getGarbageCollectorMXBeans()
                .forEach(gc -> logger.info(
                        "  GC: {} ({} collections, {} ms total)",
                        gc.getName(),
                        gc.getCollectionCount(),
                        gc.getCollectionTime()));
    }

    /** Checks if Reactor debugging should be enabled based on system properties. */
    private static boolean isDebugEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("reactor.debug"))
                || "debug".equalsIgnoreCase(System.getProperty("logging.level.reactor"));
    }
}
