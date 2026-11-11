package io.github.vipersq.takrequest.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small shared helpers: date-time group and elapsed/relative formatting. */
public final class Util {
    private Util() {}

    public static String dtg(long t) {
        if (t <= 0) return "UNK";
        SimpleDateFormat f = new SimpleDateFormat("ddHHmm'Z'MMMyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t)).toUpperCase(Locale.US);
    }

    /** "45 min" or "2 h 05 min"; "0 min" for anything under a minute or in the past. */
    public static String until(long now, long target) {
        long min = Math.max(0, (target - now) / 60000L);
        return min < 60 ? min + " min" : (min / 60) + " h " + String.format(Locale.US, "%02d", min % 60) + " min";
    }

    public static String elapsed(long from, long now) { return until(from, now * 2 - from); }
}
