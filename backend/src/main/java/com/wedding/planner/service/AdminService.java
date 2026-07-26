package com.wedding.planner.service;

import com.wedding.planner.domain.User;
import com.wedding.planner.dto.AdminDtos.AdminUserResponse;
import com.wedding.planner.dto.AdminDtos.PlatformStatsResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.GuestRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.repository.VendorRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform administration: user management and platform-wide stats. All entry points are gated
 * to {@code ROLE_ADMIN} at the controller.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final VendorRepository vendorRepository;
    private final ExpenseRepository expenseRepository;
    private final GuestRepository guestRepository;

    public AdminService(UserRepository userRepository,
                        ProjectRepository projectRepository,
                        TaskRepository taskRepository,
                        VendorRepository vendorRepository,
                        ExpenseRepository expenseRepository,
                        GuestRepository guestRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.vendorRepository = vendorRepository;
        this.expenseRepository = expenseRepository;
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getEmail))
                .map(AdminUserResponse::from)
                .toList();
    }

    /**
     * Enables/disables an account. Disabled users fail login and token authentication. Admins
     * cannot disable themselves — that's how you lock yourself out.
     */
    @Transactional
    public AdminUserResponse setEnabled(UUID userId, boolean enabled, UUID callerId) {
        if (userId.equals(callerId)) {
            throw new BadRequestException("You cannot disable your own account");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        user.setEnabled(enabled);
        return AdminUserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PlatformStatsResponse stats() {
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        for (Object[] row : userRepository.countUsersByRole()) {
            usersByRole.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return new PlatformStatsResponse(
                userRepository.count(),
                usersByRole,
                projectRepository.count(),
                taskRepository.count(),
                // Top-level only — a package's items aren't separate vendors for this count.
                vendorRepository.countByParentIsNull(),
                expenseRepository.count(),
                guestRepository.count());
    }
}
