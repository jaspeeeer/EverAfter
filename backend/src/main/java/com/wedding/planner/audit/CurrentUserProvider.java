package com.wedding.planner.audit;

import com.wedding.planner.security.AppUserPrincipal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the acting user from the ambient Spring Security context. Returns empty when the caller
 * is an unauthenticated background job (the scheduler, seeders) — audit code should record such
 * events as system-initiated (no actor).
 */
@Component
public class CurrentUserProvider {

    public Optional<AppUserPrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        if (principal instanceof AppUserPrincipal p) return Optional.of(p);
        return Optional.empty();
    }
}
