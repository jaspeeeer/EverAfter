package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wedding.planner.domain.GuestRole;
import com.wedding.planner.dto.GuestRoleDtos.CreateGuestRoleRequest;
import com.wedding.planner.dto.GuestRoleDtos.GuestRoleResponse;
import com.wedding.planner.dto.GuestRoleDtos.UpdateGuestRoleRequest;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.GuestRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for guest-role hierarchy (V24): one level of nesting only, mirroring
 * {@code VendorService.resolveParent}'s package-item pattern. Non-hierarchy behavior (name
 * uniqueness, deactivate-if-referenced) is already covered at the HTTP level in
 * {@code GuestRoleIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class GuestRoleServiceTest {

    @Mock
    private GuestRoleRepository roleRepository;

    @Mock
    private GuestRepository guestRepository;

    @InjectMocks
    private GuestRoleService guestRoleService;

    private GuestRole roleWithId(String name, String slug, int sortOrder) {
        GuestRole role = new GuestRole(name, slug, sortOrder);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        return role;
    }

    @Test
    void createWithAParentSetsIt() {
        GuestRole parent = roleWithId("Secondary Sponsor", "SECONDARY_SPONSOR", 0);
        when(roleRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(roleRepository.findAll()).thenReturn(List.of(parent));
        when(roleRepository.save(org.mockito.ArgumentMatchers.any(GuestRole.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GuestRoleResponse response = guestRoleService.create(
                new CreateGuestRoleRequest("Candle", true, parent.getId()));

        assertThat(response.parentId()).isEqualTo(parent.getId());
        assertThat(response.parentName()).isEqualTo("Secondary Sponsor");
    }

    @Test
    void updateCanReparentAPreviouslyTopLevelRole() {
        GuestRole parent = roleWithId("Secondary Sponsor", "SECONDARY_SPONSOR", 0);
        GuestRole role = roleWithId("Candle", "CANDLE", 1);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(roleRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        GuestRoleResponse response = guestRoleService.update(role.getId(),
                new UpdateGuestRoleRequest("Candle", true, true, parent.getId()));

        assertThat(response.parentId()).isEqualTo(parent.getId());
    }

    @Test
    void creatingWithANonExistentParentIs400() {
        UUID unknownParentId = UUID.randomUUID();
        when(roleRepository.findById(unknownParentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestRoleService.create(
                new CreateGuestRoleRequest("Candle", true, unknownParentId)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aRoleCannotBeItsOwnParent() {
        GuestRole role = roleWithId("Candle", "CANDLE", 1);
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> guestRoleService.update(role.getId(),
                new UpdateGuestRoleRequest("Candle", true, true, role.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aSubRoleCannotItselfContainSubRoles() {
        GuestRole topLevel = roleWithId("Secondary Sponsor", "SECONDARY_SPONSOR", 0);
        GuestRole subRole = roleWithId("Candle", "CANDLE", 1);
        subRole.setParent(topLevel);
        when(roleRepository.findById(subRole.getId())).thenReturn(Optional.of(subRole));

        assertThatThrownBy(() -> guestRoleService.create(
                new CreateGuestRoleRequest("Veil", true, subRole.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reparentingARoleThatAlreadyHasSubRolesIsRejected() {
        GuestRole parent = roleWithId("Secondary Sponsor", "SECONDARY_SPONSOR", 0);
        GuestRole otherTopLevel = roleWithId("Best Man", "BEST_MAN", 1);
        when(roleRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(roleRepository.findById(otherTopLevel.getId())).thenReturn(Optional.of(otherTopLevel));
        when(roleRepository.countByParentId(parent.getId())).thenReturn(3L);

        assertThatThrownBy(() -> guestRoleService.update(parent.getId(),
                new UpdateGuestRoleRequest("Secondary Sponsor", true, true, otherTopLevel.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deletingARoleWithSubRolesIsRejected() {
        GuestRole parent = roleWithId("Secondary Sponsor", "SECONDARY_SPONSOR", 0);
        when(roleRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(roleRepository.countByParentId(parent.getId())).thenReturn(2L);

        assertThatThrownBy(() -> guestRoleService.delete(parent.getId()))
                .isInstanceOf(BadRequestException.class);
    }
}
