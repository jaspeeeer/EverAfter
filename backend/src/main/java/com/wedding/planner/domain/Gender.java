package com.wedding.planner.domain;

/**
 * Optional gender classification for a {@link Guest}. Planner-internal metadata; not exposed on
 * the public RSVP surface.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
