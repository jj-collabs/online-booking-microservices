package com.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Talks to resource-service over HTTP (resolved via Eureka through the
 * load-balanced RestTemplate). Wrapped with Resilience4j retry + circuit
 * breaker: a few transient failures are retried, but if resource-service
 * is persistently down the breaker opens and calls fail fast via the
 * fallback instead of piling up and cascading the failure into
 * booking-service. Every open/failure event is logged for future SOC
 * ingestion (e.g. "Repeated service failures", "Dependency failure
 * between services").
 */
@Component
public class ResourceClient {

    private static final Logger log = LoggerFactory.getLogger(ResourceClient.class);

    private final RestTemplate restTemplate;

    public ResourceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "resourceService", fallbackMethod = "availabilityFallback")
    @Retry(name = "resourceService")
    public boolean isResourceAvailable(Long resourceId) {
        String url = "http://resource-service/api/resources/" + resourceId + "/availability";
        Boolean available = restTemplate.getForObject(url, Boolean.class);
        return Boolean.TRUE.equals(available);
    }

    // Fallback invoked when retries are exhausted or the circuit is open.
    // Fails safe: treats the resource as unavailable rather than allowing
    // an unverified booking to proceed.
    private boolean availabilityFallback(Long resourceId, Throwable t) {
        log.error("event=dependency_failure dependency=resource-service resourceId={} error={}",
                resourceId, t.toString());
        return false;
    }
}
