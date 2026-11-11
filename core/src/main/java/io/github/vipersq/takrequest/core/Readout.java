package io.github.vipersq.takrequest.core;

import java.util.Locale;

/** Plain-text reports for chat, voice read-back and the remarks of the map marker. */
public final class Readout {
    private Readout() {}

    private static String grid(Request r, String text, double lat, double lon) {
        return text != null && !text.isEmpty() ? text : String.format(Locale.US, "%.5f %.5f", lat, lon);
    }

    private static String header(Request r) {
        String banner = r.mode.banner.isEmpty() ? "" : "*** " + r.mode.banner + " *** ";
        return banner + r.label() + "  [" + r.priority.letter + " " + r.priority + " / " + r.status + "]";
    }

    private static String window(Transport t) {
        if (t.windowStart <= 0 && t.windowEnd <= 0) return "ASAP";
        return Util.dtg(t.windowStart) + (t.windowEnd > 0 ? " - " + Util.dtg(t.windowEnd) : "");
    }

    public static String transport(Request r) {
        Transport t = r.transport;
        StringBuilder sb = new StringBuilder();
        sb.append(header(r)).append('\n');
        sb.append("MOVEMENT REQUEST (").append(t.carrier).append(")\n");
        sb.append("Pickup: ").append(grid(r, t.pickupText, t.pickupLat, t.pickupLon)).append('\n');
        if (t.hasDest) sb.append("Dest: ").append(grid(r, t.destText, t.destLat, t.destLon)).append('\n');
        sb.append("Load: ").append(t.loadType);
        if (t.personnel > 0) sb.append("  PAX ").append(t.personnel);
        if (t.litterPatients > 0) sb.append("  litter ").append(t.litterPatients);
        if (t.vehicles > 0) sb.append("  veh ").append(t.vehicles);
        if (t.cargoWeightKg > 0) sb.append("  ").append((long) t.cargoWeightKg).append(" kg");
        sb.append('\n');
        if (!t.cargoDescription.isEmpty()) sb.append("Cargo: ").append(t.cargoDescription).append('\n');
        sb.append("Window: ").append(window(t)).append('\n');
        if (!t.freq.isEmpty() || !t.callsign.isEmpty()) sb.append("Comms: ").append(t.freq).append(' ').append(t.callsign).append('\n');
        if (!t.marking.isEmpty()) sb.append("Marking: ").append(t.marking).append('\n');
        if (t.isAir()) {
            StringBuilder lz = new StringBuilder();
            if (!t.lzSize.isEmpty()) lz.append("size ").append(t.lzSize).append("; ");
            if (!t.lzSurface.isEmpty()) lz.append("surface ").append(t.lzSurface).append("; ");
            if (!t.lzObstacles.isEmpty()) lz.append("obstacles ").append(t.lzObstacles).append("; ");
            if (!t.lzApproach.isEmpty()) lz.append("approach ").append(t.lzApproach);
            if (lz.length() > 0) sb.append("LZ: ").append(lz.toString().trim()).append('\n');
        }
        if (!t.remarks.isEmpty()) sb.append("Remarks: ").append(t.remarks);
        return sb.toString().trim();
    }

    public static String resupply(Request r) {
        Resupply s = r.resupply;
        StringBuilder sb = new StringBuilder();
        sb.append(header(r)).append('\n').append("RESUPPLY REQUEST\n");
        sb.append("Deliver to: ").append(grid(r, s.locationText.isEmpty() ? s.deliverTo : s.locationText, s.lat, s.lon)).append('\n');
        if (s.lines.isEmpty()) sb.append("(no items listed)");
        for (Resupply.Line l : s.lines) sb.append("- ").append(l.summary()).append('\n');
        if (!s.remarks.isEmpty()) sb.append("Remarks: ").append(s.remarks);
        return sb.toString().trim();
    }

    public static String sitrep(Request r) {
        Sitrep s = r.sitrep;
        StringBuilder sb = new StringBuilder();
        sb.append(header(r)).append('\n');
        if (r.kind == Enums.Kind.ACE) {
            sb.append("ACE REPORT\n");
            sb.append("A (ammo): ").append(s.ammo).append('\n');
            sb.append("C (casualties): ").append(s.casualties).append('\n');
            sb.append("E (equipment): ").append(s.equipment);
            if (!s.location.isEmpty()) sb.append("\nL (location): ").append(s.location);
            if (!s.aceRemarks.isEmpty()) sb.append("\nRemarks: ").append(s.aceRemarks);
        } else {
            sb.append("SITREP\n");
            if (!s.situation.isEmpty()) sb.append("Situation: ").append(s.situation).append('\n');
            if (!s.activity.isEmpty()) sb.append("Activity: ").append(s.activity).append('\n');
            if (!s.location.isEmpty()) sb.append("Location: ").append(s.location).append('\n');
            if (!s.nextAction.isEmpty()) sb.append("Next: ").append(s.nextAction);
        }
        return sb.toString().trim();
    }

    public static String text(Request r) {
        switch (r.kind) {
            case TRANSPORT: return transport(r);
            case RESUPPLY: return resupply(r);
            default: return sitrep(r);
        }
    }
}
