package com.dfpp.gateway.filter;

import com.dfpp.common.security.AuthenticatedUser;
import com.dfpp.common.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header for protected routes.
 * On success it strips the raw token and forwards trustworthy identity headers
 * ({@code X-User-Id}, {@code X-User-Name}, {@code X-User-Roles}) downstream so
 * internal services never have to re-parse the JWT.
 */
@Component
public class JwtAuthGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGatewayFilterFactory.class);
    private final JwtTokenProvider tokenProvider;

    public JwtAuthGatewayFilterFactory(JwtTokenProvider tokenProvider) {
        super(Config.class);
        this.tokenProvider = tokenProvider;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (header == null || !header.startsWith("Bearer ")) {
                return unauthorized(exchange, "Missing or malformed Authorization header");
            }
            String token = header.substring(7);
            if (!tokenProvider.isValid(token)) {
                return unauthorized(exchange, "Invalid or expired token");
            }

            AuthenticatedUser user = tokenProvider.toUser(token);
            if (config.requiredRole != null && !user.hasRole(config.requiredRole)) {
                return forbidden(exchange, "Requires role " + config.requiredRole);
            }

            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id", String.valueOf(user.userId()))
                    .header("X-User-Name", user.username())
                    .header("X-User-Roles", String.join(",", user.roles()))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        return write(exchange, HttpStatus.UNAUTHORIZED, reason);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        return write(exchange, HttpStatus.FORBIDDEN, reason);
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String reason) {
        log.debug("Gateway auth rejection: {} - {}", status, reason);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + reason + "\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("requiredRole");
    }

    public static class Config {
        private String requiredRole;

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }
    }
}
