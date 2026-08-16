package com.wedding.planner.repository;

import com.wedding.planner.domain.EntourageMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntourageMemberRepository extends JpaRepository<EntourageMember, UUID> {

    List<EntourageMember> findByProjectIdOrderBySortOrderAsc(UUID projectId);
}
