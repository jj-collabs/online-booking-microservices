package com.booking.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Logs every request received and completed at the gateway, including
 * status code and duration. Forms the basis of centralised logging and
 * feeds future SOC/knowledge-graph ingestion.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        log.info("event=request_received method={} path={} time={}", method, path, Instant.now());

        return chain.filter(exchange).doFinally(signal -> {
            long duration = System.currentTimeMillis() - start;
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            log.info("event=request_completed method={} path={} status={} durationMs={}",
                    method, path, status, duration);
        });
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
