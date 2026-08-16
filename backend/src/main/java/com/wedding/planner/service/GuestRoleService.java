package com.wedding.planner.service;

import com.wedding.planner.domain.GuestRole;
import com.wedding.planner.dto.GuestRoleDtos.CreateGuestRoleRequest;
import com.wedding.planner.dto.GuestRoleDtos.GuestRoleResponse;
import com.wedding.planner.dto.GuestRoleDtos.UpdateGuestRoleRequest;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ConflictException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.GuestRoleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-managed guest roles. Deleting a role that is still referenced by any guest deactivates it
 * instead of removing it (kept for existing guests, hidden from new pickers); unreferenced roles
 * are hard-deleted.
 */
@Service
public class GuestRoleService {

    private final GuestRoleRepository roleRepository;
    private final GuestRepository guestRepository;

    public GuestRoleService(GuestRoleRepository roleRepository, GuestRepository guestRepository) {
        this.roleRepository = roleRepository;
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public List<GuestRoleResponse> listActive() {
        return roleRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(GuestRoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuestRoleResponse> listAll() {
        return roleRepository.findAllByOrderBySortOrderAsc().stream()
                .map(GuestRoleResponse::from)
                .toList();
    }

    @Transactional
    public GuestRoleResponse create(CreateGuestRoleRequest request) {
        String name = request.name().trim();
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A role named \"" + name + "\" already exists");
        }
        GuestRole parent = resolveParent(request.parentId(), null);
        int nextOrder = roleRepository.findAll().stream()
                .mapToInt(GuestRole::getSortOrder)
                .max()
                .orElse(-1) + 1;
        GuestRole role = new GuestRole(name, uniqueSlug(name), nextOrder);
        role.setEntourageEligible(request.entourageEligible());
        role.setParent(parent);
        return GuestRoleResponse.from(roleRepository.save(role));
    }

    @Transactional
    public GuestRoleResponse update(UUID id, UpdateGuestRoleRequest request) {
        GuestRole role = requireRole(id);
        String name = request.name().trim();
        if (!role.getName().equalsIgnoreCase(name)
                && roleRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A role named \"" + name + "\" already exists");
        }
        GuestRole parent = resolveParent(request.parentId(), id);
        if (parent != null && roleRepository.countByParentId(id) > 0) {
            throw new BadRequestException(
                    "A parent role cannot itself become a sub-role — remove its sub-roles first");
        }
        role.setName(name);
        role.setActive(request.active());
        role.setEntourageEligible(request.entourageEligible());
        role.setParent(parent);
        return GuestRoleResponse.from(role);
    }

    /** Hard-delete if unreferenced by any guest; otherwise deactivate. */
    @Transactional
    public void delete(UUID id) {
        GuestRole role = requireRole(id);
        if (roleRepository.countByParentId(id) > 0) {
            throw new BadRequestException("This role has sub-roles; remove them first");
        }
        if (guestRepository.countByRolesId(id) > 0) {
            role.setActive(false);
        } else {
            roleRepository.delete(role);
        }
    }

    /**
     * Resolves an optional parent role id, enforcing: it exists, it isn't the role itself, and
     * it is itself top-level (one level of nesting only — a sub-role can't contain sub-roles).
     */
    private GuestRole resolveParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new BadRequestException("A role cannot be its own parent");
        }
        GuestRole parent = roleRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("Unknown parent role: " + parentId));
        if (parent.getParent() != null) {
            throw new BadRequestException("A sub-role cannot itself contain sub-roles");
        }
        return parent;
    }

    /**
     * Resolves a list of role ids to their entities for a guest write. Null/empty → no roles.
     * Dedupes; existence is required for every id (400 if unknown). Roles are global (not
     * project-scoped), so — unlike a project-scoped resource — no tenant check applies here.
     * The {@code active} flag governs only what pickers show, so an existing guest whose role
     * was deactivated can still carry it (and still be edited to keep or drop it).
     */
    @Transactional(readOnly = true)
    public Set<GuestRole> resolveRoles(List<UUID> roleIds) {
        Set<GuestRole> roles = new HashSet<>();
        if (roleIds == null) {
            return roles;
        }
        for (UUID roleId : new HashSet<>(roleIds)) {
            GuestRole role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + roleId));
            roles.add(role);
        }
        return roles;
    }

    private GuestRole requireRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Guest role", id));
    }

    private String uniqueSlug(String name) {
        String base = name.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "ROLE";
        }
        String slug = base;
        int suffix = 2;
        while (roleRepository.findBySlug(slug).isPresent()) {
            slug = base + "_" + suffix++;
        }
        return slug;
    }
}
