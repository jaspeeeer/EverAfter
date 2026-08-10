package com.wedding.planner.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.planner.config.RateLimitProperties;
import com.wedding.planner.config.RateLimitProperties.Bucket;
import com.wedding.planner.web.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP fixed-window limiter for the two unauthenticated surfaces that would otherwise
 * take unlimited traffic: the token-keyed public endpoints ({@code /api/public/**}, cheap
 * reconnaissance/DoS amplification even though the UUID keyspace itself is unguessable) and the
 * auth endpoints ({@code /api/auth/register}, {@code /api/auth/login} — an account-enumeration
 * oracle and a BCrypt CPU amplifier). Runs before {@link JwtAuthenticationFilter} so throttled
 * traffic never pays for token parsing.
 *
 * <p>Deliberately hand-rolled rather than a bucket4j/resilience4j dependency: this is a single
 * instance with no distributed cache to coordinate through, and the whole mechanism is ~80 lines.
 * Runs outside Spring MVC (and therefore outside {@code @RestControllerAdvice}), so a rejection
 * writes its own {@link ProblemDetails}-shaped body to stay consistent with every other error the
 * API returns.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/register", "/api/auth/login");

    /** Sweep stale windows every this-many requests, so a rotating-IP attacker can't grow the map unbounded. */
    private static final int SWEEP_INTERVAL = 1000;

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        BucketMatch match = resolveBucket(request);
        if (match == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientIp(request) + ":" + match.name();
        Decision decision = checkAndRecord(key, match.bucket());
        if (requestCounter.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweepStaleWindows();
        }

        if (!decision.allowed()) {
            writeTooManyRequests(response, decision.retryAfterMs());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record BucketMatch(String name, Bucket bucket) {
    }

    private BucketMatch resolveBucket(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (AUTH_PATHS.contains(path)) {
            return new BucketMatch("auth", properties.auth());
        }
        if (PATH_MATCHER.match("/api/public/**", path)) {
            return new BucketMatch("public", properties.publicApi());
        }
        return null;
    }

    private record Decision(boolean allowed, long retryAfterMs) {
    }

    /** Mutable per-key counter; every mutation happens inside {@link ConcurrentHashMap#compute}, which is atomic per key. */
    private static final class Window {
        private long windowStart;
        private int count;

        Window(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    private Decision checkAndRecord(String key, Bucket bucket) {
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicLong retryAfterMs = new AtomicLong();

        windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= bucket.windowMs()) {
                allowed.set(true);
                return new Window(now, 1);
            }
            if (existing.count < bucket.limit()) {
                existing.count++;
                allowed.set(true);
                return existing;
            }
            allowed.set(false);
            retryAfterMs.set(bucket.windowMs() - (now - existing.windowStart));
            return existing;
        });

        return new Decision(allowed.get(), Math.max(0, retryAfterMs.get()));
    }

    private void sweepStaleWindows() {
        long now = System.currentTimeMillis();
        long maxWindowMs = Math.max(properties.publicApi().windowMs(), properties.auth().windowMs());
        windows.entrySet().removeIf(e -> now - e.getValue().windowStart > maxWindowMs * 2);
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterMs) throws IOException {
        long retryAfterSeconds = (retryAfterMs + 999) / 1000;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        var problem = ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, "Too many requests — try again shortly");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
