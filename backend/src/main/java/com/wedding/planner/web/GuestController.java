package com.wedding.planner.web;

import com.wedding.planner.dto.GuestRequest;
import com.wedding.planner.dto.GuestResponse;
import com.wedding.planner.service.GuestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guests nested under a project. Access is gated on the owning {@code projectId}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/guests")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<GuestResponse> list(@PathVariable UUID projectId) {
        return guestService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<GuestResponse> create(@PathVariable UUID projectId,
                                                @Valid @RequestBody GuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.create(projectId, request));
    }

    /** Bulk create for CSV import. Each row is validated; the whole batch is all-or-nothing. */
    @PostMapping("/import")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<List<GuestResponse>> importGuests(
            @PathVariable UUID projectId,
            @RequestBody List<@Valid GuestRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestService.importGuests(projectId, requests));
    }

    @PutMapping("/{guestId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public GuestResponse update(@PathVariable UUID projectId,
                                @PathVariable UUID guestId,
                                @Valid @RequestBody GuestRequest request) {
        return guestService.update(projectId, guestId, request);
    }

    @DeleteMapping("/{guestId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID guestId) {
        guestService.delete(projectId, guestId);
        return ResponseEntity.noContent().build();
    }
}
