package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.ChecklistTemplate;
import com.wedding.planner.domain.ChecklistTemplateItem;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Task;
import com.wedding.planner.domain.TaskStatus;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorTemplate;
import com.wedding.planner.domain.VendorTemplateItem;
import com.wedding.planner.dto.TaskResponse;
import com.wedding.planner.dto.TemplateDtos.ChecklistTemplateRequest;
import com.wedding.planner.dto.TemplateDtos.ChecklistTemplateResponse;
import com.wedding.planner.dto.TemplateDtos.VendorTemplateRequest;
import com.wedding.planner.dto.TemplateDtos.VendorTemplateResponse;
import com.wedding.planner.dto.VendorResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ChecklistTemplateRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.VendorRepository;
import com.wedding.planner.repository.VendorTemplateRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-managed checklist/vendor templates, and the "apply" operations that bulk-create tasks or
 * vendor slots on a project. Authorization lives at the controllers: template writes are
 * admin-only, applying requires planner/admin plus project access.
 */
@Service
public class TemplateService {

    private final ChecklistTemplateRepository checklistTemplateRepository;
    private final VendorTemplateRepository vendorTemplateRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final VendorRepository vendorRepository;
    private final VendorCategoryService vendorCategoryService;
    private final ActivityLogService activityLog;

    public TemplateService(ChecklistTemplateRepository checklistTemplateRepository,
                           VendorTemplateRepository vendorTemplateRepository,
                           ProjectRepository projectRepository,
                           TaskRepository taskRepository,
                           VendorRepository vendorRepository,
                           VendorCategoryService vendorCategoryService,
                           ActivityLogService activityLog) {
        this.checklistTemplateRepository = checklistTemplateRepository;
        this.vendorTemplateRepository = vendorTemplateRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.vendorRepository = vendorRepository;
        this.vendorCategoryService = vendorCategoryService;
        this.activityLog = activityLog;
    }

    // --- Checklist template CRUD ---

    @Transactional(readOnly = true)
    public List<ChecklistTemplateResponse> listChecklistTemplates() {
        return checklistTemplateRepository.findAllWithItems().stream()
                .map(ChecklistTemplateResponse::from)
                .toList();
    }

    @Transactional
    public ChecklistTemplateResponse createChecklistTemplate(ChecklistTemplateRequest request) {
        ChecklistTemplate template = new ChecklistTemplate(request.name(), request.description());
        request.items().forEach(item -> template.addItem(
                new ChecklistTemplateItem(item.title(), item.description(), item.daysBeforeWedding())));
        return ChecklistTemplateResponse.from(checklistTemplateRepository.save(template));
    }

    @Transactional
    public ChecklistTemplateResponse updateChecklistTemplate(UUID templateId,
                                                             ChecklistTemplateRequest request) {
        ChecklistTemplate template = requireChecklistTemplate(templateId);
        template.setName(request.name());
        template.setDescription(request.description());
        template.replaceItems(request.items().stream()
                .map(item -> new ChecklistTemplateItem(
                        item.title(), item.description(), item.daysBeforeWedding()))
                .toList());
        return ChecklistTemplateResponse.from(template);
    }

    @Transactional
    public void deleteChecklistTemplate(UUID templateId) {
        checklistTemplateRepository.delete(requireChecklistTemplate(templateId));
    }

    // --- Vendor template CRUD ---

    @Transactional(readOnly = true)
    public List<VendorTemplateResponse> listVendorTemplates() {
        return vendorTemplateRepository.findAllWithItems().stream()
                .map(VendorTemplateResponse::from)
                .toList();
    }

    @Transactional
    public VendorTemplateResponse createVendorTemplate(VendorTemplateRequest request) {
        VendorTemplate template = new VendorTemplate(request.name(), request.description());
        request.items().forEach(item -> template.addItem(new VendorTemplateItem(
                item.name(), vendorCategoryService.requireForAssignment(item.categoryId()))));
        return VendorTemplateResponse.from(vendorTemplateRepository.save(template));
    }

    @Transactional
    public VendorTemplateResponse updateVendorTemplate(UUID templateId,
                                                       VendorTemplateRequest request) {
        VendorTemplate template = requireVendorTemplate(templateId);
        template.setName(request.name());
        template.setDescription(request.description());
        template.replaceItems(request.items().stream()
                .map(item -> new VendorTemplateItem(
                        item.name(), vendorCategoryService.requireForAssignment(item.categoryId())))
                .toList());
        return VendorTemplateResponse.from(template);
    }

    @Transactional
    public void deleteVendorTemplate(UUID templateId) {
        vendorTemplateRepository.delete(requireVendorTemplate(templateId));
    }

    // --- Applying templates to a project ---

    /**
     * Creates one TODO task per template item. When both the project's wedding date and the
     * item's daysBeforeWedding are present, the due date is counted back from the wedding.
     */
    @Transactional
    public List<TaskResponse> applyChecklistTemplate(UUID projectId, UUID templateId) {
        Project project = requireProject(projectId);
        ChecklistTemplate template = requireChecklistTemplate(templateId);
        LocalDate weddingDate = project.getWeddingDate();

        List<Task> tasks = template.getItems().stream()
                .map(item -> {
                    Task task = new Task(item.getTitle(), TaskStatus.TODO);
                    task.setDescription(item.getDescription());
                    if (weddingDate != null && item.getDaysBeforeWedding() != null) {
                        task.setDueDate(weddingDate.minusDays(item.getDaysBeforeWedding()));
                    }
                    task.setProject(project);
                    return task;
                })
                .toList();

        List<Task> saved = taskRepository.saveAll(tasks);
        activityLog.record(projectId, ActivityEntityType.TASK, null, ActivityAction.CREATE,
                "Applied checklist template \"" + template.getName() + "\" (" + saved.size() + " tasks)");
        return saved.stream().map(TaskResponse::from).toList();
    }

    /** Creates one unbooked vendor slot per template item. */
    @Transactional
    public List<VendorResponse> applyVendorTemplate(UUID projectId, UUID templateId) {
        Project project = requireProject(projectId);
        VendorTemplate template = requireVendorTemplate(templateId);

        List<Vendor> vendors = template.getItems().stream()
                .map(item -> {
                    Vendor vendor = new Vendor(item.getName(), item.getCategory());
                    vendor.setProject(project);
                    return vendor;
                })
                .toList();

        List<Vendor> saved = vendorRepository.saveAll(vendors);
        activityLog.record(projectId, ActivityEntityType.VENDOR, null, ActivityAction.CREATE,
                "Applied vendor template \"" + template.getName() + "\" (" + saved.size() + " vendors)");
        return saved.stream().map(VendorResponse::from).toList();
    }

    // --- helpers ---

    private ChecklistTemplate requireChecklistTemplate(UUID id) {
        return checklistTemplateRepository.findByIdWithItems(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Checklist template", id));
    }

    private VendorTemplate requireVendorTemplate(UUID id) {
        return vendorTemplateRepository.findByIdWithItems(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Vendor template", id));
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }
}
