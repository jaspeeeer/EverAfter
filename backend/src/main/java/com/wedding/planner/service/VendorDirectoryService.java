package com.wedding.planner.service;

import com.wedding.planner.domain.VendorDirectoryEntry;
import com.wedding.planner.dto.VendorDirectoryDtos.VendorDirectoryRequest;
import com.wedding.planner.dto.VendorDirectoryDtos.VendorDirectoryResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.VendorDirectoryRepository;
import com.wedding.planner.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The global, admin-curated vendor directory. Delete deactivates entries that projects have
 * already used (so the report link survives), and hard-deletes unused ones.
 */
@Service
public class VendorDirectoryService {

    private final VendorDirectoryRepository directoryRepository;
    private final VendorRepository vendorRepository;
    private final VendorCategoryService vendorCategoryService;

    public VendorDirectoryService(VendorDirectoryRepository directoryRepository,
                                  VendorRepository vendorRepository,
                                  VendorCategoryService vendorCategoryService) {
        this.directoryRepository = directoryRepository;
        this.vendorRepository = vendorRepository;
        this.vendorCategoryService = vendorCategoryService;
    }

    @Transactional(readOnly = true)
    public List<VendorDirectoryResponse> listAll() {
        return directoryRepository.findAllWithCategory().stream()
                .map(VendorDirectoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorDirectoryResponse> listActive() {
        return directoryRepository.findActiveWithCategory().stream()
                .map(VendorDirectoryResponse::from)
                .toList();
    }

    @Transactional
    public VendorDirectoryResponse create(VendorDirectoryRequest request) {
        VendorDirectoryEntry entry = new VendorDirectoryEntry(
                request.name().trim(),
                vendorCategoryService.requireForAssignment(request.categoryId()));
        apply(entry, request);
        entry.setActive(request.active() == null || request.active());
        return VendorDirectoryResponse.from(directoryRepository.save(entry));
    }

    @Transactional
    public VendorDirectoryResponse update(UUID id, VendorDirectoryRequest request) {
        VendorDirectoryEntry entry = requireEntry(id);
        entry.setName(request.name().trim());
        entry.setCategory(vendorCategoryService.requireForAssignment(request.categoryId()));
        apply(entry, request);
        if (request.active() != null) {
            entry.setActive(request.active());
        }
        return VendorDirectoryResponse.from(entry);
    }

    @Transactional
    public void delete(UUID id) {
        VendorDirectoryEntry entry = requireEntry(id);
        if (vendorRepository.countByDirectoryEntryId(id) > 0) {
            entry.setActive(false);
        } else {
            directoryRepository.delete(entry);
        }
    }

    private void apply(VendorDirectoryEntry entry, VendorDirectoryRequest request) {
        entry.setContactEmail(request.contactEmail());
        entry.setPhone(request.phone());
        entry.setTypicalPrice(request.typicalPrice());
        entry.setNotes(request.notes());
    }

    private VendorDirectoryEntry requireEntry(UUID id) {
        return directoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor directory entry", id));
    }
}
