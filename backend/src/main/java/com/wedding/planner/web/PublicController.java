package com.wedding.planner.web;

import com.wedding.planner.dto.InvitationDtos.InvitationPublicResponse;
import com.wedding.planner.dto.RsvpDtos.RsvpUpdateRequest;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.service.AttachmentService;
import com.wedding.planner.service.GuestService;
import com.wedding.planner.service.IcsService;
import com.wedding.planner.service.InvitationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated endpoints ({@code /api/public/**} is permitted in the security config).
 * Everything here is keyed by an unguessable UUID token, exposes no internal ids, and allows
 * only the narrow updates an invitee should make about themselves.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final GuestService guestService;
    private final InvitationService invitationService;
    private final AttachmentService attachmentService;
    private final IcsService icsService;

    public PublicController(GuestService guestService, InvitationService invitationService,
                            AttachmentService attachmentService, IcsService icsService) {
        this.guestService = guestService;
        this.invitationService = invitationService;
        this.attachmentService = attachmentService;
        this.icsService = icsService;
    }

    /** What a guest sees when opening their RSVP link. */
    @GetMapping("/rsvp/{token}")
    public RsvpViewResponse viewRsvp(@PathVariable UUID token) {
        return guestService.viewByRsvpToken(token);
    }

    /** A guest submitting / changing their RSVP. */
    @PutMapping("/rsvp/{token}")
    public RsvpViewResponse respond(@PathVariable UUID token,
                                    @Valid @RequestBody RsvpUpdateRequest request) {
        return guestService.respondByRsvpToken(token, request);
    }

    /** Invitation preview for the register page (prefills the invitee's email). */
    @GetMapping("/invitations/{token}")
    public InvitationPublicResponse invitation(@PathVariable UUID token) {
        return invitationService.findPublic(token);
    }

    /**
     * A project's named photo slot (cover, ceremony, reception, or attire men/women), for the
     * invitation page. Keyed by the guest's own RSVP token rather than an attachment id — the
     * public DTO never learns the attachment's id (see {@code RsvpViewResponse#hasCover}/
     * {@code hasCeremonyPhoto}/{@code hasReceptionPhoto}/{@code hasAttireMenPhoto}/
     * {@code hasAttireWomenPhoto}), so this is the only way in.
     */
    @GetMapping("/rsvp/{token}/cover")
    public ResponseEntity<InputStreamResource> cover(@PathVariable UUID token) throws IOException {
        return streamPhoto(attachmentService.downloadProjectCover(guestService.projectIdByRsvpToken(token)));
    }

    @GetMapping("/rsvp/{token}/ceremony-photo")
    public ResponseEntity<InputStreamResource> ceremonyPhoto(@PathVariable UUID token) throws IOException {
        return streamPhoto(attachmentService.downloadCeremonyPhoto(guestService.projectIdByRsvpToken(token)));
    }

    @GetMapping("/rsvp/{token}/reception-photo")
    public ResponseEntity<InputStreamResource> receptionPhoto(@PathVariable UUID token) throws IOException {
        return streamPhoto(attachmentService.downloadReceptionPhoto(guestService.projectIdByRsvpToken(token)));
    }

    @GetMapping("/rsvp/{token}/attire-men-photo")
    public ResponseEntity<InputStreamResource> attireMenPhoto(@PathVariable UUID token) throws IOException {
        return streamPhoto(attachmentService.downloadAttireMenPhoto(guestService.projectIdByRsvpToken(token)));
    }

    @GetMapping("/rsvp/{token}/attire-women-photo")
    public ResponseEntity<InputStreamResource> attireWomenPhoto(@PathVariable UUID token) throws IOException {
        return streamPhoto(attachmentService.downloadAttireWomenPhoto(guestService.projectIdByRsvpToken(token)));
    }

    private ResponseEntity<InputStreamResource> streamPhoto(AttachmentService.Download download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().getContentType()))
                .contentLength(download.attachment().getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(new InputStreamResource(download.data()));
    }

    /** An "Add to calendar" file for the wedding, built from the same data the invitation shows. */
    @GetMapping("/rsvp/{token}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@PathVariable UUID token) {
        RsvpViewResponse rsvp = guestService.viewByRsvpToken(token);
        String ics = icsService.buildInvitationIcs(rsvp, token);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("wedding.ics", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }
}
