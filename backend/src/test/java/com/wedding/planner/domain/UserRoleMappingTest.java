package com.wedding.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wedding.planner.AbstractPostgresContainerTest;
import com.wedding.planner.repository.RoleRepository;
import com.wedding.planner.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verifies User <-> Role mappings: uniqueness constraints and the many-to-many join table.
 */
class UserRoleMappingTest extends AbstractPostgresContainerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void persistsAndReloadsUserWithMultipleRoles() {
        Role admin = em.persistAndFlush(new Role(RoleName.ROLE_ADMIN));
        Role planner = em.persistAndFlush(new Role(RoleName.ROLE_PLANNER));

        User user = new User("planner@wedding.test", "hash", "Ada", "Lovelace");
        user.addRole(admin);
        user.addRole(planner);
        User saved = em.persistFlushFind(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.ROLE_ADMIN, RoleName.ROLE_PLANNER);
    }

    @Test
    void roleNameIsStoredAsStringLiteral() {
        em.persistAndFlush(new Role(RoleName.ROLE_USER));

        Object storedName = em.getEntityManager()
                .createNativeQuery("SELECT name FROM roles WHERE name = 'ROLE_USER'")
                .getSingleResult();

        assertThat(storedName).isEqualTo("ROLE_USER");
    }

    @Test
    void enforcesUniqueRoleName() {
        // saveAndFlush goes through the @Repository proxy, so the raw persistence exception is
        // translated into Spring's DataIntegrityViolationException.
        roleRepository.saveAndFlush(new Role(RoleName.ROLE_ADMIN));

        assertThatThrownBy(() -> roleRepository.saveAndFlush(new Role(RoleName.ROLE_ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesUniqueUserEmail() {
        userRepository.saveAndFlush(new User("dup@wedding.test", "hash", "First", "One"));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("dup@wedding.test", "hash", "Second", "Two")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByEmailReturnsMatchingUser() {
        em.persistAndFlush(new User("lookup@wedding.test", "hash", "Look", "Up"));
        em.clear();

        assertThat(userRepository.findByEmail("lookup@wedding.test")).isPresent();
        assertThat(userRepository.findByEmail("missing@wedding.test")).isEmpty();
    }

    @Test
    void findByNameReturnsMatchingRole() {
        em.persistAndFlush(new Role(RoleName.ROLE_PLANNER));
        em.clear();

        assertThat(roleRepository.findByName(RoleName.ROLE_PLANNER)).isPresent();
        assertThat(roleRepository.findByName(RoleName.ROLE_ADMIN)).isEmpty();
    }
}
