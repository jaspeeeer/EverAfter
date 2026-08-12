package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wedding.planner.domain.RsvpStatus;
import com.wedding.planner.dto.RsvpDtos.RsvpViewResponse;
import com.wedding.planner.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IcsServiceTest {

    private final IcsService service = new IcsService("https://example.test");

    private RsvpViewResponse rsvp(LocalDate weddingDate, String venueName, String venueAddress,
                                  LocalTime ceremonyTime, LocalTime receptionTime) {
        return new RsvpViewResponse("Guest", "Test Wedding", weddingDate, RsvpStatus.PENDING, 1,
                null, venueName, venueAddress, ceremonyTime, receptionTime, false, null, false);
    }

    @Test
    void missingWeddingDateIsRejected() {
        assertThatThrownBy(() ->
                service.buildInvitationIcs(rsvp(null, null, null, null, null), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void noTimesAtAllDefaultsToAnAllDayishWindow() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null, null, null), UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T000000");
        assertThat(ics).contains("DTEND:20270101T235900");
    }

    @Test
    void onlyCeremonyTimeDefaultsToAThreeHourWindow() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null, LocalTime.of(15, 0), null),
                UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T150000");
        assertThat(ics).contains("DTEND:20270101T180000");
    }

    @Test
    void receptionTimeBeforeCeremonyRollsOverToTheNextDay() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null, LocalTime.of(20, 0), LocalTime.of(1, 0)),
                UUID.randomUUID());

        assertThat(ics).contains("DTSTART:20270101T200000");
        assertThat(ics).contains("DTEND:20270102T010000");
    }

    @Test
    void specialCharactersInTheVenueAreEscaped() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), "Rick, Morty; & Co\\", null, null, null),
                UUID.randomUUID());

        assertThat(ics).contains("LOCATION:Rick\\, Morty\\; & Co\\\\");
    }

    @Test
    void hasNoTrailingBlankLinesAndUsesCrlf() {
        String ics = service.buildInvitationIcs(
                rsvp(LocalDate.of(2027, 1, 1), null, null, null, null), UUID.randomUUID());

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }
}
