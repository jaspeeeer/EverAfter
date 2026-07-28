package com.wedding.planner.web;

import com.wedding.planner.dto.GuestRoleDtos.GuestRoleResponse;
import com.wedding.planner.service.GuestRoleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Active guest roles for the picker. Readable by any authenticated user (planners classify
 * guests); admin management lives at {@code /api/admin/guest-roles}.
 */
@RestController
@RequestMapping("/api/guest-roles")
public class GuestRoleController {

    private final GuestRoleService guestRoleService;

    public GuestRoleController(GuestRoleService guestRoleService) {
        this.guestRoleService = guestRoleService;
    }

    @GetMapping
    public List<GuestRoleResponse> list() {
        return guestRoleService.listActive();
    }
}
