package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wedding.planner.domain.RsvpStatus;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IcsServiceTest {

    private final IcsService service = new IcsService("https://example.test");

    private RsvpViewResponse rsvp(LocalDate weddingDate, String ceremonyVenueName,
                                  String ceremonyVenueAddress, String receptionVenueName,
                                  String receptionVenueAddress, LocalTime ceremonyTime,
                                  LocalTime receptionTime) {
        return new RsvpViewResponse("Guest", "Test Wedding", weddingDate, RsvpStatus.PENDING, 1,
                null, ceremonyVenueName, ceremonyVenueAddress, receptionVenueName,
                receptionVenueAddress, ceremonyTime, receptionTime, false, null, false, false,
                false, null, null, null, null, null, null, null, List.of());
    }

    private RsvpViewResponse rsvp(LocalDate weddingDate, LocalTime ceremonyTime, LocalTime receptionTime) {
        return rsvp(weddingDate, null, null, null, null, ceremonyTime, receptionTime);
    }

    @Test
    void missingWeddingDateIsRejected() {
        assertThatThrownBy(() ->
                service.buildInvitationIcs(rsvp(null, null, null), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void noTimesAtAllDefaultsToAnAllDayishWindow() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null), UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T000000");
        assertThat(ics).contains("DTEND:20270101T235900");
    }

    @Test
    void onlyCeremonyTimeDefaultsToAThreeHourWindow() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), LocalTime.of(15, 0), null), UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T150000");
        assertThat(ics).contains("DTEND:20270101T180000");
    }

    @Test
    void receptionTimeBeforeCeremonyRollsOverToTheNextDay() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), LocalTime.of(20, 0), LocalTime.of(1, 0)),
                UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T200000");
        assertThat(ics).contains("DTEND:20270102T010000");
    }

    @Test
    void ceremonyVenueDrivesLocationAndSpecialCharactersAreEscaped() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), "Rick, Morty; & Co\\", null, null, null, null, null),
                UUID.randomUUID());

        assertThat(ics).contains("LOCATION:Rick\\, Morty\\; & Co\\\\");
    }

    @Test
    void receptionVenueIsMentionedInDescriptionNotLocation() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), "St. Mary's Church", "Church St", "Grand Hall",
                        "Hall Ave", LocalTime.of(15, 0), LocalTime.of(18, 0)),
                UUID.randomUUID());

        String locationLine = ics.lines().filter(line -> line.startsWith("LOCATION:")).findFirst().orElseThrow();
        assertThat(locationLine).isEqualTo("LOCATION:St. Mary's Church\\, Church St");
        assertThat(locationLine).doesNotContain("Grand Hall");
        assertThat(ics).contains("Reception to follow at Grand Hall\\, Hall Ave");
    }

    @Test
    void noReceptionVenueOmitsTheReceptionMention() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), "St. Mary's Church", "Church St", null, null,
                        LocalTime.of(15, 0), null),
                UUID.randomUUID());

        assertThat(ics).doesNotContain("Reception to follow at");
    }

    @Test
    void hasNoTrailingBlankLinesAndUsesCrlf() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null), UUID.randomUUID());

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }
}
