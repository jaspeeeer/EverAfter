package com.wedding.planner.service;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorCategory;
import com.wedding.planner.domain.VendorDirectoryEntry;
import com.wedding.planner.domain.VendorPayment;
import com.wedding.planner.dto.VendorPaymentDtos.VendorPaymentRequest;
import com.wedding.planner.dto.VendorPaymentDtos.VendorPaymentResponse;
import com.wedding.planner.dto.VendorRequest;
import com.wedding.planner.dto.VendorResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.VendorDirectoryRepository;
import com.wedding.planner.repository.VendorPaymentRepository;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for vendors nested under a project. See {@link TaskService} for the project-scoping
 * pattern that keeps authorization sound. A vendor's agreed price is mirrored into a single
 * linked budget {@link Expense} (see {@link #syncVendorExpense}).
 */
@Service
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ProjectRepository projectRepository;
    private final VendorCategoryService vendorCategoryService;
    private final VendorDirectoryRepository directoryRepository;
    private final ExpenseRepository expenseRepository;
    private final VendorPaymentRepository paymentRepository;

    public VendorService(VendorRepository vendorRepository,
                         ProjectRepository projectRepository,
                         VendorCategoryService vendorCategoryService,
                         VendorDirectoryRepository directoryRepository,
                         ExpenseRepository expenseRepository,
                         VendorPaymentRepository paymentRepository) {
        this.vendorRepository = vendorRepository;
        this.projectRepository = projectRepository;
        this.vendorCategoryService = vendorCategoryService;
        this.directoryRepository = directoryRepository;
        this.expenseRepository = expenseRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> list(UUID projectId) {
        requireProject(projectId);
        Map<UUID, BigDecimal> paidByVendor = new HashMap<>();
        for (Object[] row : paymentRepository.sumByProjectGroupedByVendor(projectId)) {
            paidByVendor.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return vendorRepository.findByProjectIdWithCategory(projectId).stream()
                .map(v -> VendorResponse.from(v, paidByVendor.getOrDefault(v.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public VendorResponse create(UUID projectId, VendorRequest request) {
        Project project = requireProject(projectId);
        VendorCategory category = vendorCategoryService.requireForAssignment(request.categoryId());
        Vendor parent = resolveParent(projectId, request.parentId(), null);
        requireNoPriceForPackageItem(parent, request.agreedPrice());
        Vendor vendor = new Vendor(request.name(), category);
        vendor.setContactEmail(request.contactEmail());
        vendor.setPhone(request.phone());
        vendor.setBooked(request.booked());
        vendor.setAgreedPrice(parent == null ? request.agreedPrice() : null);
        vendor.setParent(parent);
        vendor.setProject(project);
        Vendor saved = vendorRepository.save(vendor);
        syncVendorExpense(saved);
        return VendorResponse.from(saved);
    }

    @Transactional
    public VendorResponse update(UUID projectId, UUID vendorId, VendorRequest request) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        Vendor parent = resolveParent(projectId, request.parentId(), vendorId);
        if (parent != null && vendorRepository.countByParentId(vendor.getId()) > 0) {
            throw new BadRequestException(
                    "A package cannot itself become an item — remove its items first");
        }
        requireNoPriceForPackageItem(parent, request.agreedPrice());
        vendor.setName(request.name());
        vendor.setCategory(vendorCategoryService.requireForAssignment(request.categoryId()));
        vendor.setContactEmail(request.contactEmail());
        vendor.setPhone(request.phone());
        vendor.setBooked(request.booked());
        vendor.setParent(parent);
        vendor.setAgreedPrice(parent == null ? request.agreedPrice() : null);
        syncVendorExpense(vendor);
        return VendorResponse.from(vendor, paymentRepository.sumByVendorId(vendor.getId()));
    }

    @Transactional
    public void delete(UUID projectId, UUID vendorId) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        // Remove the system-owned agreed-price line explicitly; any manual vendor mappings are
        // left in place (the FK is ON DELETE SET NULL, so they simply unmap).
        expenseRepository.findByVendorIdAndManagedTrue(vendorId)
                .ifPresent(expenseRepository::delete);
        vendorRepository.delete(vendor);
    }

    /** Copies a directory entry into this project as a new vendor and keeps the link. */
    @Transactional
    public VendorResponse addFromDirectory(UUID projectId, UUID directoryId) {
        Project project = requireProject(projectId);
        VendorDirectoryEntry entry = directoryRepository.findById(directoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor directory entry", directoryId));
        Vendor vendor = new Vendor(entry.getName(), entry.getCategory());
        vendor.setContactEmail(entry.getContactEmail());
        vendor.setPhone(entry.getPhone());
        vendor.setDirectoryEntry(entry);
        vendor.setProject(project);
        // No agreed price yet — the planner sets that when the deal is done.
        return VendorResponse.from(vendorRepository.save(vendor));
    }

    /**
     * Keeps the vendor's budget line in step with its agreed price and recorded payments: upsert one
     * linked (managed) expense when a price is set — its amount is the full price and its paid
     * amount is the sum of the vendor's payments — and delete it when the price is cleared. A
     * package item (parent set) never gets a line of its own — its package carries the one bundled
     * price, so this is the single guard that keeps the budget from double-counting.
     */
    private void syncVendorExpense(Vendor vendor) {
        Optional<Expense> existing = expenseRepository.findByVendorIdAndManagedTrue(vendor.getId());
        if (vendor.getParent() != null || vendor.getAgreedPrice() == null) {
            existing.ifPresent(expenseRepository::delete);
            return;
        }
        Expense expense = existing.orElseGet(() -> {
            Expense created = new Expense(
                    vendor.getName(), vendor.getAgreedPrice(), vendor.getCategory());
            created.setProject(vendor.getProject());
            created.setVendor(vendor);
            created.setManaged(true);
            return created;
        });
        expense.setDescription(vendor.getName());
        expense.setAmount(vendor.getAgreedPrice());
        // Expenses and vendors share one category lookup, so reuse the vendor's category directly.
        expense.setCategory(vendor.getCategory());
        // Payments drive how much of this line is paid (capped at the full amount).
        BigDecimal paid = paymentRepository.sumByVendorId(vendor.getId()).min(vendor.getAgreedPrice());
        expense.setPaidAmount(paid);
        expense.setPaid(paid.compareTo(vendor.getAgreedPrice()) >= 0);
        expenseRepository.save(expense);
    }

    // --- Vendor payments (installments) ---

    @Transactional(readOnly = true)
    public List<VendorPaymentResponse> listPayments(UUID projectId, UUID vendorId) {
        requireVendorInProject(projectId, vendorId);
        return paymentRepository.findByVendorIdOrderByPaidOnAscIdAsc(vendorId).stream()
                .map(VendorPaymentResponse::from)
                .toList();
    }

    @Transactional
    public VendorPaymentResponse addPayment(UUID projectId, UUID vendorId, VendorPaymentRequest request) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        if (vendor.getParent() != null) {
            throw new BadRequestException(
                    "Payments are recorded on the package, not on its individual items");
        }
        if (vendor.getAgreedPrice() == null) {
            throw new BadRequestException("Set the vendor's full amount before recording a payment");
        }
        BigDecimal balance = vendor.getAgreedPrice().subtract(paymentRepository.sumByVendorId(vendorId));
        if (request.amount().compareTo(balance) > 0) {
            throw new BadRequestException("Payment exceeds the remaining balance");
        }
        VendorPayment payment = paymentRepository.save(
                new VendorPayment(vendor, request.amount(), request.paidOn(), request.note()));
        syncVendorExpense(vendor);
        return VendorPaymentResponse.from(payment);
    }

    @Transactional
    public void deletePayment(UUID projectId, UUID vendorId, UUID paymentId) {
        Vendor vendor = requireVendorInProject(projectId, vendorId);
        VendorPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor payment", paymentId));
        if (!payment.getVendor().getId().equals(vendorId)) {
            throw ResourceNotFoundException.of("Vendor payment", paymentId);
        }
        paymentRepository.delete(payment);
        syncVendorExpense(vendor);
    }

    /**
     * Resolves an optional package (parent) vendor id, enforcing: the package belongs to this
     * project, it is itself top-level (one level of nesting only — a package item can't contain
     * items), and a vendor can't be parented to itself.
     */
    private Vendor resolveParent(UUID projectId, UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new BadRequestException("A vendor cannot be its own package");
        }
        Vendor parent = vendorRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("Unknown package: " + parentId));
        if (!parent.getProject().getId().equals(projectId)) {
            throw new BadRequestException("The package must belong to the same project");
        }
        if (parent.getParent() != null) {
            throw new BadRequestException("A package item cannot itself contain items");
        }
        return parent;
    }

    private void requireNoPriceForPackageItem(Vendor parent, BigDecimal agreedPrice) {
        if (parent != null && agreedPrice != null) {
            throw new BadRequestException(
                    "A package item cannot have its own price — set it on the package instead");
        }
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
