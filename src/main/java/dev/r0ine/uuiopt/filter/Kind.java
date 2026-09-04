package dev.r0ine.uuiopt.filter;

public enum Kind {
    /** Whole payload replaces the previous state, so an identical repeat is redundant. */
    ABSOLUTE,
    MOVE_POS,
    MOVE_POS_ROT,
    MOVE_ROT,
    STOP_SOUND,
    /** Entity appears on the client: its cached state is no longer valid. */
    SPAWN,
    REMOVE,
    /** Dimension change or reconfiguration: every cached state is dropped. */
    RESET
}
