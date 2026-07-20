package com.wedding.planner.repository;

import com.wedding.planner.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /** Eagerly fetches roles so authorities can be resolved outside a persistence context. */
    @Query("select u from User u left join fetch u.roles where u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    /** Rows of {@code [roleName(String via name()), count(Long)]} for the admin dashboard. */
    @Query("select r.name, count(u) from User u join u.roles r group by r.name")
    java.util.List<Object[]> countUsersByRole();

    boolean existsByEmail(String email);
}
