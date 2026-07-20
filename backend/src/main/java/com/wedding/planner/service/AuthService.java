package com.wedding.planner.service;

import com.wedding.planner.domain.Role;
import com.wedding.planner.domain.RoleName;
import com.wedding.planner.domain.User;
import com.wedding.planner.dto.AuthResponse;
import com.wedding.planner.dto.LoginRequest;
import com.wedding.planner.dto.RegisterRequest;
import com.wedding.planner.dto.UserResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.EmailAlreadyExistsException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.RoleRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.security.AppUserPrincipal;
import com.wedding.planner.security.JwtService;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login and current-user lookups. Registration is limited to the PLANNER and USER
 * roles; ADMIN accounts are seeded, never self-registered.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final InvitationService invitationService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       InvitationService invitationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.invitationService = invitationService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() == RoleName.ROLE_ADMIN) {
            throw new BadRequestException("Cannot self-register with an administrative role");
        }
        if (request.inviteToken() != null && request.role() != RoleName.ROLE_USER) {
            throw new BadRequestException("Invitations are for couple accounts only");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new BadRequestException("Unknown role: " + request.role()));

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName());
        user.addRole(role);
        userRepository.save(user);

        // Consuming the invitation attaches this account as the project's owning couple.
        // Same transaction: a failed/duplicate invitation rolls back the registration too.
        if (request.inviteToken() != null) {
            invitationService.accept(request.inviteToken(), user);
        }

        AppUserPrincipal principal = AppUserPrincipal.from(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.bearer(token, user.getId(), user.getEmail(), authorityNames(principal));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        return AuthResponse.bearer(
                token, principal.getId(), principal.getUsername(), authorityNames(principal));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(AppUserPrincipal principal) {
        User user = userRepository.findByEmailWithRoles(principal.getUsername())
                .orElseThrow(() -> ResourceNotFoundException.of("User", principal.getUsername()));
        return UserResponse.from(user);
    }

    private List<String> authorityNames(AppUserPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
