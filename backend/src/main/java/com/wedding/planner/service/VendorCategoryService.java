package com.wedding.planner.service;

import com.wedding.planner.domain.VendorCategory;
import com.wedding.planner.dto.VendorCategoryDtos.CreateVendorCategoryRequest;
import com.wedding.planner.dto.VendorCategoryDtos.UpdateVendorCategoryRequest;
import com.wedding.planner.dto.VendorCategoryDtos.VendorCategoryResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ConflictException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.VendorCategoryRepository;
import com.wedding.planner.repository.VendorDirectoryRepository;
import com.wedding.planner.repository.VendorRepository;
import com.wedding.planner.repository.VendorTemplateItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-managed vendor categories. Deleting a category that is still referenced by any vendor,
 * template item, directory entry, or expense deactivates it instead of removing it (kept for
 * existing data, hidden from new pickers); unreferenced categories are hard-deleted.
 */
@Service
public class VendorCategoryService {

    private final VendorCategoryRepository categoryRepository;
    private final VendorRepository vendorRepository;
    private final VendorTemplateItemRepository templateItemRepository;
    private final VendorDirectoryRepository directoryRepository;
    private final ExpenseRepository expenseRepository;

    public VendorCategoryService(VendorCategoryRepository categoryRepository,
                                 VendorRepository vendorRepository,
                                 VendorTemplateItemRepository templateItemRepository,
                                 VendorDirectoryRepository directoryRepository,
                                 ExpenseRepository expenseRepository) {
        this.categoryRepository = categoryRepository;
        this.vendorRepository = vendorRepository;
        this.templateItemRepository = templateItemRepository;
        this.directoryRepository = directoryRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorCategoryResponse> listActive() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(VendorCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorCategoryResponse> listAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(VendorCategoryResponse::from)
                .toList();
    }

    @Transactional
    public VendorCategoryResponse create(CreateVendorCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists");
        }
        int nextOrder = categoryRepository.findAll().stream()
                .mapToInt(VendorCategory::getSortOrder)
                .max()
                .orElse(-1) + 1;
        VendorCategory category = new VendorCategory(name, uniqueSlug(name), nextOrder);
        return VendorCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public VendorCategoryResponse update(UUID id, UpdateVendorCategoryRequest request) {
        VendorCategory category = requireCategory(id);
        String name = request.name().trim();
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists");
        }
        category.setName(name);
        category.setActive(request.active());
        return VendorCategoryResponse.from(category);
    }

    /** Hard-delete if unreferenced anywhere; otherwise deactivate. */
    @Transactional
    public void delete(UUID id) {
        VendorCategory category = requireCategory(id);
        boolean inUse = vendorRepository.countByCategoryId(id) > 0
                || templateItemRepository.countByCategoryId(id) > 0
                || directoryRepository.countByCategoryId(id) > 0
                || expenseRepository.countByCategoryId(id) > 0;
        if (inUse) {
            category.setActive(false);
        } else {
            categoryRepository.delete(category);
        }
    }

    /**
     * Resolves a category id to the entity for vendor/directory/template writes. Existence is
     * required (400 if unknown); the {@code active} flag governs only what pickers show, so an
     * existing vendor whose category was deactivated can still be edited.
     */
    @Transactional(readOnly = true)
    public VendorCategory requireForAssignment(UUID id) {
        if (id == null) {
            throw new BadRequestException("A category is required");
        }
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Unknown category: " + id));
    }

    private VendorCategory requireCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor category", id));
    }

    private String uniqueSlug(String name) {
        String base = name.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "CATEGORY";
        }
        String slug = base;
        int suffix = 2;
        while (categoryRepository.findBySlug(slug).isPresent()) {
            slug = base + "_" + suffix++;
        }
        return slug;
    }
}
