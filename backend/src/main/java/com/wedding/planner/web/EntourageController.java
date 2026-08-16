package com.wedding.planner.web;

import com.wedding.planner.dto.EntourageDtos.EntourageMemberRequest;
import com.wedding.planner.dto.EntourageDtos.EntourageMemberResponse;
import com.wedding.planner.dto.EntourageDtos.ImportFromGuestsRequest;
import com.wedding.planner.dto.EntourageDtos.ImportFromGuestsResult;
import com.wedding.planner.service.EntourageService;
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
 * A project's entourage (wedding party) list. Access is gated on the owning {@code projectId}, so
 * isolation is inherited from the project's access rules — same tier as guests, not manage-only.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/entourage")
public class EntourageController {

    private final EntourageService entourageService;

    public EntourageController(EntourageService entourageService) {
        this.entourageService = entourageService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<EntourageMemberResponse> list(@PathVariable UUID projectId) {
        return entourageService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<EntourageMemberResponse> add(@PathVariable UUID projectId,
                                                       @Valid @RequestBody EntourageMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entourageService.add(projectId, request));
    }

    @PutMapping("/{memberId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public EntourageMemberResponse update(@PathVariable UUID projectId,
                                          @PathVariable UUID memberId,
                                          @Valid @RequestBody EntourageMemberRequest request) {
        return entourageService.update(projectId, memberId, request);
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> remove(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        entourageService.remove(projectId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{memberId}/move-up")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public EntourageMemberResponse moveUp(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        return entourageService.moveUp(projectId, memberId);
    }

    @PutMapping("/{memberId}/move-down")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public EntourageMemberResponse moveDown(@PathVariable UUID projectId, @PathVariable UUID memberId) {
        return entourageService.moveDown(projectId, memberId);
    }

    @PostMapping("/import-from-guests")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ImportFromGuestsResult importFromGuests(@PathVariable UUID projectId,
                                                   @Valid @RequestBody ImportFromGuestsRequest request) {
        return entourageService.importFromGuests(projectId, request.entries());
    }
}
