package com.wedding.planner.domain;

/**
 * The three RBAC roles supported by the platform.
 *
 * <ul>
 *   <li>{@link #ROLE_ADMIN} — system administrator; oversees ALL projects and users.</li>
 *   <li>{@link #ROLE_PLANNER} — professional planner; manages MANY projects they own.</li>
 *   <li>{@link #ROLE_USER} — couple; owns exactly ONE wedding project.</li>
 * </ul>
 *
 * <p>Names deliberately carry the {@code ROLE_} prefix so they map directly onto Spring
 * Security authorities in Phase 2.
 */
public enum RoleName {
    ROLE_ADMIN,
    ROLE_PLANNER,
    ROLE_USER
}
