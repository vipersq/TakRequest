package io.github.vipersq.takrequest.core;

/** Shared enumerations for every request type. */
public final class Enums {
    private Enums() {}

    /** Operating mode. Shown on every request and marker so a drill is never mistaken for the real thing. */
    public enum Mode {
        REAL(""), EXERCISE("EXERCISE"), MILSIM("MILSIM"), TRAINING("TRAINING");
        /** Banner text; empty for REAL. */
        public final String banner;
        Mode(String b) { banner = b; }
    }

    /** Where a request is in its lifecycle. New values go at the end: an unknown status reads as DRAFT. */
    public enum Status {
        DRAFT, SENT, ACKNOWLEDGED, APPROVED, DENIED, IN_PROGRESS, COMPLETE, CANCELLED;
        public boolean isOpen() { return this != COMPLETE && this != CANCELLED && this != DENIED; }
        public boolean isDone() { return this == COMPLETE || this == CANCELLED || this == DENIED; }
    }

    /** Request priority. argb drives the marker and chip colour. */
    public enum Priority {
        FLASH('Z', 0xFFE5484D), IMMEDIATE('O', 0xFFF5A524), PRIORITY('P', 0xFF4DA3FF), ROUTINE('R', 0xFF2ECC71);
        public final char letter;
        public final int argb;
        Priority(char l, int c) { letter = l; argb = c; }
    }

    /** Kind of request; each has its own form and marker glyph. */
    public enum Kind { TRANSPORT, RESUPPLY, SITREP, ACE }

    /** Transport mode for a movement request. */
    public enum Carrier { AIR, SEA, LAND }

    /** What is being moved. */
    public enum LoadType { PERSONNEL, CARGO, VEHICLE, MIXED, MEDICAL }

    /** ACE / LACE combat-power report line. */
    public enum AceState { GREEN, AMBER, RED, BLACK }
}
