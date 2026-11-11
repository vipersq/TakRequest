package io.github.vipersq.takrequest.core;

import java.util.UUID;

/**
 * One request of any kind. Holds the common envelope (who, when, status, mode, position) plus
 * exactly one detail block matching {@link #kind}. Revisioned for last-writer-wins sync.
 */
public class Request {
    public String uid = "TAKREQ-" + UUID.randomUUID();
    public int rev = 1;
    public long created, updated;
    public String editor = "";          // callsign/UID of the last editor
    public String origin = "";          // callsign that raised it

    public Enums.Kind kind = Enums.Kind.TRANSPORT;
    public Enums.Mode mode = Enums.Mode.REAL;
    public Enums.Status status = Enums.Status.DRAFT;
    public Enums.Priority priority = Enums.Priority.ROUTINE;

    /** Short human title shown as the marker label, e.g. "LIFT.1" or "RESUP.3". */
    public String title = "";
    /** Primary map position (pickup for transport, delivery for resupply, reporting point otherwise). */
    public double lat, lon;

    /** Exactly one of these is populated per kind. */
    public Transport transport;
    public Resupply resupply;
    public Sitrep sitrep;

    public Request() {}

    public static Request of(Enums.Kind kind) {
        Request r = new Request();
        r.kind = kind;
        r.created = System.currentTimeMillis();
        switch (kind) {
            case TRANSPORT: r.transport = new Transport(); break;
            case RESUPPLY: r.resupply = new Resupply(); break;
            case SITREP: case ACE: r.sitrep = new Sitrep(); break;
        }
        return r;
    }

    public String label() { return title.isEmpty() ? kind.name() + "-" + uid.substring(uid.length() - 4).toUpperCase() : title; }

    /** Call after any local edit so other devices accept the change. */
    public void touch(long now, String editorCallsign) { rev++; updated = now; editor = editorCallsign == null ? "" : editorCallsign; }

    /** True when {@code other} should replace this record: higher revision wins, then later edit, then editor name. */
    public boolean isSupersededBy(Request other) {
        if (other.rev != rev) return other.rev > rev;
        if (other.updated != updated) return other.updated > updated;
        return other.editor.compareTo(editor) > 0;
    }
}
