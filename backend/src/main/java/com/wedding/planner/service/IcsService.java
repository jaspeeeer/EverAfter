package com.wedding.planner.service;

import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the "Add to calendar" (.ics) file offered on the public invitation page. Hand-rolled per
 * RFC 5545 — no library, ~40 lines of actual generation logic.
 *
 * <p>Times are emitted as <b>floating</b> (no {@code Z}, no {@code TZID}) — a wedding's ceremony
 * time is a wall-clock instant at the venue, not a point on the UTC timeline, so a guest viewing
 * the invite from a different time zone should see the same 3:00 PM the invitation printed, not
 * a shifted one.
 */
@Service
public class IcsService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String frontendBaseUrl;

    public IcsService(@Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * @param rsvpToken the guest's own RSVP token — doubles as the event {@code UID} basis, so a
     *                  guest re-downloading the file updates their existing calendar entry rather
     *                  than creating a duplicate. Never exposed anywhere the token itself isn't
     *                  already the credential.
     */
    public String buildInvitationIcs(RsvpViewResponse rsvp, UUID rsvpToken) {
        if (rsvp.weddingDate() == null) {
            throw new BadRequestException("No wedding date has been set for this project yet.");
        }

        LocalTime ceremonyTime = rsvp.ceremonyTime();
        LocalTime receptionTime = rsvp.receptionTime();
        LocalDate weddingDate = rsvp.weddingDate();
        LocalDateTime start =
                LocalDateTime.of(weddingDate, ceremonyTime != null ? ceremonyTime : LocalTime.MIDNIGHT);
        LocalDateTime end = endOf(weddingDate, start, ceremonyTime, receptionTime);

        // The ceremony is the calendar event's anchor (DTSTART/DTEND are ceremony-derived), so
        // it drives LOCATION; the reception — a separate place and time — gets a mention in
        // DESCRIPTION instead of fighting the ceremony for the one LOCATION field.
        String location = joinNonBlank(rsvp.ceremonyVenueName(), rsvp.ceremonyVenueAddress());
        StringBuilder description = new StringBuilder("RSVP: " + frontendBaseUrl + "/rsvp/" + rsvpToken);
        String receptionMention = joinNonBlank(rsvp.receptionVenueName(), rsvp.receptionVenueAddress());
        if (!receptionMention.isBlank()) {
            // A real newline here — escape() below turns it into the RFC 5545 literal "\n"
            // sequence; writing "\\n" directly would double-escape into "\\\\n".
            description.append('\n').append("Reception to follow at ").append(receptionMention);
        }

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Ever After//Wedding Planner//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + rsvpToken + "@wedding-planner");
        lines.add("DTSTAMP:" + UTC_STAMP.format(Instant.now().atZone(ZoneOffset.UTC)));
        lines.add("DTSTART:" + STAMP.format(start));
        lines.add("DTEND:" + STAMP.format(end));
        lines.add("SUMMARY:" + escape(rsvp.projectName()));
        if (!location.isBlank()) {
            lines.add("LOCATION:" + escape(location));
        }
        lines.add("DESCRIPTION:" + escape(description.toString()));
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        return String.join("\r\n", lines) + "\r\n";
    }

    /** Reception time (rolled to the next day if it's numerically before the start), else a
     * 3-hour default after the ceremony, else end-of-day. */
    private LocalDateTime endOf(LocalDate weddingDate, LocalDateTime start,
                                LocalTime ceremonyTime, LocalTime receptionTime) {
        if (receptionTime != null) {
            LocalDateTime end = LocalDateTime.of(weddingDate, receptionTime);
            return end.isAfter(start) ? end : end.plusDays(1);
        }
        if (ceremonyTime != null) {
            return start.plus(Duration.ofHours(3));
        }
        return LocalDateTime.of(weddingDate, LocalTime.of(23, 59));
    }

    private String joinNonBlank(String a, String b) {
        boolean hasA = a != null && !a.isBlank();
        boolean hasB = b != null && !b.isBlank();
        if (hasA && hasB) {
            return a + ", " + b;
        }
        return hasA ? a : hasB ? b : "";
    }

    /** RFC 5545 §3.3.11 text escaping: backslash first, then comma/semicolon, then newline. */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}
