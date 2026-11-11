package io.github.vipersq.takrequest.atak;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import io.github.vipersq.takrequest.core.Enums;
import io.github.vipersq.takrequest.core.Request;

/**
 * Map marker icons drawn in code. Colour follows priority (grey once the request is done); the glyph
 * follows the kind (aircraft, ship, truck, box, SITREP bars, ACE shield). A small corner dot shows
 * the status, and an exercise/milsim request gets a dashed ring so a drill is never mistaken as real.
 */
final class MarkerIcons {
    private MarkerIcons() {}

    private static final int PX = 96;
    static final int DISPLAY = 48;
    private static final Map<String, String> CACHE = new HashMap<>();

    static String key(Request r) {
        return r.kind.name() + "/" + (r.status.isDone() ? "DONE" : r.priority.name()) + "/" + r.status.name() + "/" + (r.mode == Enums.Mode.REAL ? "R" : "X");
    }

    static synchronized String uri(Request r) {
        String key = key(r);
        String uri = CACHE.get(key);
        if (uri == null) {
            Bitmap b = draw(r);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
            b.recycle();
            uri = "base64://" + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP | Base64.URL_SAFE);
            CACHE.put(key, uri);
        }
        return uri;
    }

    private static int statusColour(Enums.Status s) {
        switch (s) {
            case APPROVED: case IN_PROGRESS: return Style.GREEN;
            case DENIED: case CANCELLED: return Style.RED;
            case COMPLETE: return Style.BLUE;
            case ACKNOWLEDGED: case SENT: return Style.AMBER;
            default: return Style.GREY;
        }
    }

    private static Bitmap draw(Request r) {
        Bitmap bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float m = PX / 2f;
        int fill = r.status.isDone() ? Style.GREY : r.priority.argb;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x66000000);
        c.drawCircle(m, m, 47, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(m, m, 44, p);
        p.setColor(fill);
        c.drawCircle(m, m, 37, p);

        p.setColor(Style.on(fill));
        switch (r.kind) {
            case TRANSPORT: glyphTransport(c, m, p, r); break;
            case RESUPPLY: glyphBox(c, m, p); break;
            case SITREP: glyphBars(c, m, p); break;
            case ACE: glyphShield(c, m, p); break;
        }

        // status dot, bottom-right
        p.setColor(0xFFFFFFFF);
        c.drawCircle(m + 24, m + 24, 13, p);
        p.setColor(statusColour(r.status));
        c.drawCircle(m + 24, m + 24, 10, p);

        // exercise / non-real: dashed ring
        if (r.mode != Enums.Mode.REAL) {
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[] { 10, 9 }, 0));
            c.drawCircle(m, m, 40, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
        }
        return bmp;
    }

    private static void glyphTransport(Canvas c, float m, Paint p, Request r) {
        switch (r.transport == null ? Enums.Carrier.LAND : r.transport.carrier) {
            case AIR: glyphAir(c, m, p); break;
            case SEA: glyphSea(c, m, p); break;
            default: glyphTruck(c, m, p); break;
        }
    }

    private static void glyphAir(Canvas c, float m, Paint p) {
        // simple top-down aircraft
        Path a = new Path();
        a.moveTo(m, m - 26); a.lineTo(m + 5, m - 6); a.lineTo(m + 26, m + 4); a.lineTo(m + 26, m + 11);
        a.lineTo(m + 5, m + 7); a.lineTo(m + 4, m + 20); a.lineTo(m + 12, m + 26); a.lineTo(m + 12, m + 30);
        a.lineTo(m, m + 26); a.lineTo(m - 12, m + 30); a.lineTo(m - 12, m + 26); a.lineTo(m - 4, m + 20);
        a.lineTo(m - 5, m + 7); a.lineTo(m - 26, m + 11); a.lineTo(m - 26, m + 4); a.lineTo(m - 5, m - 6);
        a.close();
        c.drawPath(a, p);
    }

    private static void glyphSea(Canvas c, float m, Paint p) {
        Path h = new Path();
        h.moveTo(m - 26, m + 2); h.lineTo(m + 26, m + 2); h.lineTo(m + 16, m + 20); h.lineTo(m - 16, m + 20);
        h.close();
        c.drawPath(h, p);
        c.drawRect(new RectF(m - 12, m - 16, m + 12, m), p);
        p.setStrokeWidth(5); p.setStyle(Paint.Style.STROKE);
        c.drawLine(m - 24, m + 26, m + 24, m + 26, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void glyphTruck(Canvas c, float m, Paint p) {
        c.drawRect(new RectF(m - 26, m - 12, m + 6, m + 10), p);          // box body
        Path cab = new Path();
        cab.moveTo(m + 6, m - 4); cab.lineTo(m + 18, m - 4); cab.lineTo(m + 24, m + 4); cab.lineTo(m + 24, m + 10);
        cab.lineTo(m + 6, m + 10); cab.close();
        c.drawPath(cab, p);
        p.setColor(0xFF000000);
        c.drawCircle(m - 16, m + 14, 6, p); c.drawCircle(m + 14, m + 14, 6, p);
    }

    private static void glyphBox(Canvas c, float m, Paint p) {
        c.drawRect(new RectF(m - 22, m - 14, m + 22, m + 22), p);
        p.setColor(0xFF000000);
        p.setStrokeWidth(5); p.setStyle(Paint.Style.STROKE);
        c.drawLine(m, m - 14, m, m + 22, p);
        c.drawLine(m - 22, m + 4, m + 22, m + 4, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void glyphBars(Canvas c, float m, Paint p) {
        c.drawRect(new RectF(m - 22, m + 6, m - 8, m + 22), p);
        c.drawRect(new RectF(m - 7, m - 6, m + 7, m + 22), p);
        c.drawRect(new RectF(m + 8, m - 20, m + 22, m + 22), p);
    }

    private static void glyphShield(Canvas c, float m, Paint p) {
        Path s = new Path();
        s.moveTo(m, m - 24); s.lineTo(m + 20, m - 15); s.lineTo(m + 20, m + 4);
        s.quadTo(m + 20, m + 18, m, m + 26); s.quadTo(m - 20, m + 18, m - 20, m + 4);
        s.lineTo(m - 20, m - 15); s.close();
        c.drawPath(s, p);
    }
}
