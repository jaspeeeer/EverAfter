package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.TimelineEvent;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.dto.TimelineDtos.TimelineEventRequest;
import com.wedding.planner.dto.TimelineDtos.TimelineEventResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.TimelineEventRepository;
import com.wedding.planner.repository.VendorRepository;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The wedding-day run sheet: time-slotted events with their involved suppliers. Editing is
 * gated to planners/admins at the controller; couples read the same timeline.
 */
@Service
public class TimelineService {

    /**
     * Times before this hour are treated as "after midnight" and sort to the end of the day, so
     * a 01:00 after-party follows the 23:00 party instead of preceding the 06:00 makeup call.
     */
    static final LocalTime EARLY_MORNING_CUTOFF = LocalTime.of(4, 0);

    private final TimelineEventRepository timelineEventRepository;
    private final ProjectRepository projectRepository;
    private final VendorRepository vendorRepository;
    private final ActivityLogService activityLog;

    public TimelineService(TimelineEventRepository timelineEventRepository,
                           ProjectRepository projectRepository,
                           VendorRepository vendorRepository,
                           ActivityLogService activityLog) {
        this.timelineEventRepository = timelineEventRepository;
        this.projectRepository = projectRepository;
        this.vendorRepository = vendorRepository;
        this.activityLog = activityLog;
    }

    /** Minutes-from-day-start used for ordering, where the day wraps at the early-morning cutoff. */
    static int wrappedMinutes(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        return time.isBefore(EARLY_MORNING_CUTOFF) ? minutes + 24 * 60 : minutes;
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> list(UUID projectId) {
        requireProject(projectId);
        return timelineEventRepository.findByProjectIdWithVendors(projectId).stream()
                .sorted(Comparator
                        .comparingInt((TimelineEvent e) -> wrappedMinutes(e.getStartTime()))
                        .thenComparing(TimelineEvent::getTitle))
                .map(TimelineEventResponse::from)
                .toList();
    }

    @Transactional
    public TimelineEventResponse create(UUID projectId, TimelineEventRequest request) {
        Project project = requireProject(projectId);
        TimelineEvent event = new TimelineEvent(request.title(), request.startTime());
        applyRequest(event, request, projectId);
        event.setProject(project);
        TimelineEvent saved = timelineEventRepository.save(event);
        activityLog.record(projectId, ActivityEntityType.TIMELINE_EVENT, saved.getId(),
                ActivityAction.CREATE,
                "Added timeline event \"" + saved.getTitle() + "\" at " + saved.getStartTime());
        return TimelineEventResponse.from(saved);
    }

    @Transactional
    public TimelineEventResponse update(UUID projectId, UUID eventId,
                                        TimelineEventRequest request) {
        TimelineEvent event = requireEventInProject(projectId, eventId);
        event.setTitle(request.title());
        event.setStartTime(request.startTime());
        applyRequest(event, request, projectId);
        activityLog.record(projectId, ActivityEntityType.TIMELINE_EVENT, eventId,
                ActivityAction.UPDATE, "Updated timeline event \"" + event.getTitle() + "\"");
        return TimelineEventResponse.from(event);
    }

    @Transactional
    public void delete(UUID projectId, UUID eventId) {
        TimelineEvent event = requireEventInProject(projectId, eventId);
        String title = event.getTitle();
        timelineEventRepository.delete(event);
        activityLog.record(projectId, ActivityEntityType.TIMELINE_EVENT, eventId,
                ActivityAction.DELETE, "Deleted timeline event \"" + title + "\"");
    }

    /**
     * Quick-start: seeds a typical wedding-day run (makeup call through after-party) so planners
     * adjust times instead of typing everything. Only allowed while the timeline is empty.
     */
    @Transactional
    public List<TimelineEventResponse> applyTypicalDay(UUID projectId) {
        Project project = requireProject(projectId);
        if (timelineEventRepository.countByProjectId(projectId) > 0) {
            throw new BadRequestException("This project already has timeline events");
        }

        record Preset(String title, String location, int startHour, int startMin, Integer endHour) {
        }
        List<Preset> presets = List.of(
                new Preset("Hair & makeup call", "Bridal suite", 6, 0, 9),
                new Preset("Photographer arrives — prep coverage", "Bridal suite", 8, 0, 10),
                new Preset("First look & couple photos", null, 9, 30, 10),
                new Preset("Travel to ceremony", null, 10, 30, 11),
                new Preset("Ceremony", null, 11, 0, 12),
                new Preset("Cocktails & group photos", null, 12, 0, 13),
                new Preset("Reception & dinner", null, 13, 0, 16),
                new Preset("Party & dancing", null, 16, 0, 22),
                new Preset("After-party", null, 22, 0, null));

        List<TimelineEvent> events = presets.stream()
                .map(p -> {
                    TimelineEvent event = new TimelineEvent(
                            p.title(), LocalTime.of(p.startHour(), p.startMin()));
                    event.setLocation(p.location());
                    if (p.endHour() != null) {
                        event.setEndTime(LocalTime.of(p.endHour(), 0));
                    }
                    event.setProject(project);
                    return event;
                })
                .toList();

        List<TimelineEvent> saved = timelineEventRepository.saveAll(events);
        activityLog.record(projectId, ActivityEntityType.TIMELINE_EVENT, null,
                ActivityAction.CREATE, "Applied typical wedding-day timeline (" + saved.size() + " events)");
        return saved.stream().map(TimelineEventResponse::from).toList();
    }

    /** Shared field mapping + the cross-tenant supplier guard. */
    private void applyRequest(TimelineEvent event, TimelineEventRequest request, UUID projectId) {
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setEndTime(request.endTime());
        event.replaceVendors(resolveVendors(request.vendorIds(), projectId));
    }

    /** Every linked supplier must belong to the same project — ids can't cross tenants. */
    private Set<Vendor> resolveVendors(List<UUID> vendorIds, UUID projectId) {
        Set<Vendor> vendors = new HashSet<>();
        if (vendorIds == null) {
            return vendors;
        }
        for (UUID vendorId : new HashSet<>(vendorIds)) {
            Vendor vendor = vendorRepository.findById(vendorId)
                    .orElseThrow(() -> new BadRequestException(
                            "Unknown vendor: " + vendorId));
            if (!vendor.getProject().getId().equals(projectId)) {
                throw new BadRequestException(
                        "Vendor " + vendorId + " does not belong to this project");
            }
            vendors.add(vendor);
        }
        return vendors;
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private TimelineEvent requireEventInProject(UUID projectId, UUID eventId) {
        TimelineEvent event = timelineEventRepository.findByIdWithVendors(eventId)
                .orElseThrow(() -> ResourceNotFoundException.of("Timeline event", eventId));
        if (!event.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Timeline event", eventId);
        }
        return event;
    }
}
