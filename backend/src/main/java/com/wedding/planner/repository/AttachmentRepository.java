package com.wedding.planner.repository;

import com.wedding.planner.domain.Attachment;
import com.wedding.planner.domain.AttachmentOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByOwnerTypeAndOwnerIdOrderByUploadedAtDesc(
            AttachmentOwnerType ownerType, UUID ownerId);

    List<Attachment> findByProjectIdOrderByUploadedAtDesc(UUID projectId);
}
