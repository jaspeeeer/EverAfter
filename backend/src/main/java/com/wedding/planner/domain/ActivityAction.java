package com.wedding.planner.domain;

/** Kind of mutation recorded in the activity log. */
public enum ActivityAction {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE
}
