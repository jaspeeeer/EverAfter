package com.wedding.planner.web;

import com.wedding.planner.dto.GuestRoleDtos.CreateGuestRoleRequest;
import com.wedding.planner.dto.GuestRoleDtos.GuestRoleResponse;
import com.wedding.planner.dto.GuestRoleDtos.UpdateGuestRoleRequest;
import com.wedding.planner.service.GuestRoleService;
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

/** Admin management of the guest role lookup (full list incl. inactive). */
@RestController
@RequestMapping("/api/admin/guest-roles")
@PreAuthorize("hasRole('ADMIN')")
public class GuestRoleAdminController {

    private final GuestRoleService guestRoleService;

    public GuestRoleAdminController(GuestRoleService guestRoleService) {
        this.guestRoleService = guestRoleService;
    }

    @GetMapping
    public List<GuestRoleResponse> list() {
        return guestRoleService.listAll();
    }

    @PostMapping
    public ResponseEntity<GuestRoleResponse> create(
            @Valid @RequestBody CreateGuestRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(guestRoleService.create(request));
    }

    @PutMapping("/{id}")
    public GuestRoleResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateGuestRoleRequest request) {
        return guestRoleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        guestRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
