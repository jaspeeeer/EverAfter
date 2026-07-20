package com.wedding.planner.service;

import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.dto.VendorRequest;
import com.wedding.planner.dto.VendorResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for vendors nested under a project. See {@link TaskService} for the project-scoping
 * pattern that keeps authorization sound.
 */
@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ProjectRepository projectRepository;

    public VendorService(VendorRepository vendorRepository, ProjectRepository projectRepository) {
        this.vendorRepository = vendorRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> list(UUID projectId) {
        requireProject(projectId);
        return vendorRepository.findByProjectId(projectId).stream()
                .map(VendorResponse::from)
                .toList();
    }

    @Transactional
    public VendorResponse create(UUID projectId, VendorRequest request) {
        Project project = requireProject(projectId);
        Vendor vendor = new Vendor(request.name(), request.category());
        vendor.setContactEmail(request.contactEmail());
        vendor.setPhone(request.phone());
        vendor.setBooked(request.booked());
        vendor.setProject(project);
        return VendorResponse.from(vendorRepository.save(vendor));
    }

    @Transactional
    public VendorResponse update(UUID projectId, UUID vendorId, VendorRequest request) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        vendor.setName(request.name());
        vendor.setCategory(request.category());
        vendor.setContactEmail(request.contactEmail());
        vendor.setPhone(request.phone());
        vendor.setBooked(request.booked());
        return VendorResponse.from(vendor);
    }

    @Transactional
    public void delete(UUID projectId, UUID vendorId) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        vendorRepository.delete(vendor);
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private Vendor requireVendorInProject(UUID projectId, UUID vendorId) {
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor", vendorId));
        if (!vendor.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Vendor", vendorId);
        }
        return vendor;
    }
}
