package io.github.vipersq.takrequest.atak;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.github.vipersq.takrequest.core.CotCodec;
import io.github.vipersq.takrequest.core.Enums;
import io.github.vipersq.takrequest.core.Readout;
import io.github.vipersq.takrequest.core.Request;
import io.github.vipersq.takrequest.core.Resupply;
import io.github.vipersq.takrequest.core.Transport;
import io.github.vipersq.takrequest.core.Util;

/** All screens of the pane: request board, per-kind editors and the status workflow. */
class Ui {
    interface Pick { void on(int index); }
    interface Form { void on(String[] values); }

    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int CAPS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    private static final int NUM = InputType.TYPE_CLASS_NUMBER;
    private static final int DEC = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    private static final long TICK_MS = 30_000L;

    private final Context ctx;
    private final FrameLayout root;
    private final Repo repo;
    private final float dp;
    private boolean listVisible;
    private final List<Runnable> clocks = new ArrayList<>();
    private boolean ticking;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clocks.isEmpty() || !root.isAttachedToWindow()) { ticking = false; return; }
            for (Runnable r : clocks) r.run();
            root.postDelayed(this, TICK_MS);
        }
    };

    Ui(Context pluginContext, FrameLayout root, Repo repo) {
        this.ctx = pluginContext;
        this.root = root;
        this.repo = repo;
        this.dp = pluginContext.getResources().getDisplayMetrics().density;
        repo.onRemoteChange = () -> { if (listVisible) showList(); };
    }

    // ------------------------------------------------------------------ board

    void showList() {
        LinearLayout col = screen();
        listVisible = true;
        List<Request> all = repo.store.sorted();
        col.addView(header("REQUEST BOARD", all.size() + " total"));

        col.addView(row(
                primary("+ NEW REQUEST", this::newRequest),
                secondary("REFRESH", this::showList)), margins(0, 0, 0, 8));

        if (all.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No requests yet.", 15, true, Style.TEXT));
            empty.addView(text("Create a transport, resupply, SITREP or ACE report. It appears on the map and is shared when you send it.", 13, false, Style.MUTED));
            col.addView(empty);
        }
        for (final Request r : all) col.addView(listRow(r));
        col.addView(text("Coordination and reporting tool. Not a targeting or fire-support system.", 11, false, Style.MUTED));
        startClocks();
    }

    private View listRow(final Request r) {
        final int colour = r.status.isDone() ? Style.GREY : r.priority.argb;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setBackground(Style.pressable(Style.CARD, Style.STROKE, px(10), px(1)));
        row.setOnClickListener(v -> showEditor(copy(r), false));

        TextView badge = text(kindShort(r.kind), 12, true, Style.on(colour));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Style.round(colour, 0, px(8), 0));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(52), ViewGroup.LayoutParams.MATCH_PARENT);
        blp.setMargins(px(8), px(8), px(4), px(8));
        row.addView(badge, blp);

        LinearLayout body = column();
        body.setPadding(px(6), px(8), px(8), px(8));
        String ttl = r.label() + (r.mode.banner.isEmpty() ? "" : "   [" + r.mode.banner + "]");
        body.addView(text(ttl, 16, true, r.mode.banner.isEmpty() ? Style.TEXT : Style.AMBER));
        body.addView(text(kindLine(r), 13, false, Style.TEXT));
        body.addView(text(r.priority.name() + "  ·  " + r.status.name().replace('_', ' ')
                + (r.origin.isEmpty() ? "" : "  ·  from " + r.origin), 12, false, Style.MUTED));
        // clock line
        final TextView age = text("", 12, false, Style.MUTED);
        body.addView(age);
        Runnable clock = () -> age.setText(clockLine(r));
        clock.run();
        clocks.add(clock);
        row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout wrap = new FrameLayout(ctx);
        wrap.addView(row);
        wrap.setPadding(0, 0, 0, px(8));
        return wrap;
    }

    private static String kindShort(Enums.Kind k) {
        switch (k) { case TRANSPORT: return "MOVE"; case RESUPPLY: return "SUP"; case ACE: return "ACE"; default: return "SIT"; }
    }
    private static String kindLine(Request r) {
        switch (r.kind) {
            case TRANSPORT:
                Transport t = r.transport;
                StringBuilder sb = new StringBuilder(t.carrier + "  " + t.loadType);
                if (t.personnel > 0) sb.append("  PAX ").append(t.personnel);
                if (t.vehicles > 0) sb.append("  veh ").append(t.vehicles);
                return sb.toString();
            case RESUPPLY: return r.resupply.lines.size() + " item(s)  →  " + (r.resupply.deliverTo.isEmpty() ? "see card" : r.resupply.deliverTo);
            case ACE: return "A " + r.sitrep.ammo + " · C " + r.sitrep.casualties + " · E " + r.sitrep.equipment;
            default: return r.sitrep.activity.isEmpty() ? "SITREP" : r.sitrep.activity;
        }
    }
    private String clockLine(Request r) {
        long now = System.currentTimeMillis();
        if (r.kind == Enums.Kind.TRANSPORT && r.transport.windowStart > now)
            return "Pickup window in " + Util.until(now, r.transport.windowStart);
        return "Raised " + Util.elapsed(r.created, now) + " ago";
    }

    private void newRequest() {
        String[] kinds = { "Transport (air/sea/land)", "Resupply", "SITREP", "ACE report" };
        final Enums.Kind[] map = { Enums.Kind.TRANSPORT, Enums.Kind.RESUPPLY, Enums.Kind.SITREP, Enums.Kind.ACE };
        choose("New request", kinds, i -> {
            Request r = Request.of(map[i]);
            double[] p = repo.here(true);
            r.lat = p[0]; r.lon = p[1];
            r.origin = repo.callsign();
            r.rev = 0;
            SimpleDateFormat f = new SimpleDateFormat("HHmm", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            r.title = kindShort(map[i]) + "." + f.format(new Date());
            showEditor(r, true);
        });
    }

    private static Request copy(Request r) {
        return CotCodec.fromCot(CotCodec.toCot(r, System.currentTimeMillis(), Repo.STALE_MS));
    }

    // ------------------------------------------------------------------ editor

    void showEditor(final Request r, final boolean isNew) {
        final LinearLayout col = screen();
        col.addView(secondary("‹  BACK  (discard changes)", this::showList), margins(0, 0, 0, 8));
        col.addView(header((isNew ? "NEW " : "") + r.kind.name() + "  " + r.label(), repo.mgrs(r.lat, r.lon)));

        // --- envelope
        LinearLayout env = card("REQUEST");
        final EditText title = edit("Title (e.g. LIFT.1)", r.title, CAPS);
        env.addView(title);
        env.addView(row(
                picker("Priority", names(Enums.Priority.values()), r.priority.ordinal(), i -> r.priority = Enums.Priority.values()[i]),
                picker("Mode", names(Enums.Mode.values()), r.mode.ordinal(), i -> r.mode = Enums.Mode.values()[i])));
        if (r.mode != Enums.Mode.REAL || true) {
            TextView note = text("Mode marks the request and its marker. Use REAL only for real tasking.", 11, false, Style.MUTED);
            env.addView(note);
        }
        col.addView(env);

        final Runnable[] apply = new Runnable[1];
        switch (r.kind) {
            case TRANSPORT: apply[0] = transportCard(col, r); break;
            case RESUPPLY: apply[0] = resupplyCard(col, r); break;
            case SITREP: apply[0] = sitrepCard(col, r); break;
            case ACE: apply[0] = aceCard(col, r); break;
        }

        // --- status workflow
        LinearLayout st = card("STATUS: " + r.status.name().replace('_', ' '));
        st.addView(text("Set the stage and send so the team sees it.", 11, false, Style.MUTED));
        st.addView(statusButtons(r, st));
        col.addView(st);

        final Runnable commit = () -> { title.clearFocus(); r.title = str(title); apply[0].run(); };
        col.addView(row(
                primary("SAVE + SEND", () -> { commit.run(); toast(repo.save(r, true) ? "Saved and sent: " + r.label() : "Saved, but ATAK rejected the message"); showList(); }),
                secondary("SAVE ONLY", () -> { commit.run(); repo.save(r, false); toast("Saved on this device: " + r.label()); showList(); })));
        col.addView(row(
                secondary("TEXT REPORT", () -> { commit.run(); report(r.kind.name(), Readout.text(r)); }),
                secondary("SHOW ON MAP", () -> {
                    MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(r.uid);
                    if (mi != null) ATAKUtilities.scaleToFit(mi); else toast("Save the request first");
                })), margins(0, 6, 0, 0));
        if (!isNew)
            col.addView(danger("DELETE FROM THIS DEVICE", () -> new AlertDialog.Builder(dialogContext())
                    .setTitle("Delete " + r.label() + "?")
                    .setMessage("Removes the request and its marker from this device only. Other devices keep their copy.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> { repo.delete(r.uid); toast("Deleted: " + r.label()); showList(); })
                    .show()), margins(0, 18, 0, 0));
        startClocks();
    }

    private View statusButtons(final Request r, final LinearLayout card) {
        LinearLayout box = column();
        Enums.Status[] flow = { Enums.Status.SENT, Enums.Status.ACKNOWLEDGED, Enums.Status.APPROVED,
                Enums.Status.DENIED, Enums.Status.IN_PROGRESS, Enums.Status.COMPLETE, Enums.Status.CANCELLED };
        LinearLayout r1 = null;
        for (int i = 0; i < flow.length; i++) {
            final Enums.Status s = flow[i];
            if (r1 == null || r1.getChildCount() == 3) { r1 = row(); box.addView(r1); }
            Button b = s == r.status
                    ? button(s.name().replace('_', ' '), Style.GREEN, 0, Style.on(Style.GREEN), () -> {})
                    : secondary(s.name().replace('_', ' '), null);
            b.setTextSize(12);
            b.setOnClickListener(v -> { r.status = s; toast("Status: " + s + " (save to share)"); showEditorKeepScroll(r); });
            r1.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return box;
    }

    private void showEditorKeepScroll(Request r) { showEditor(r, r.rev == 0 && r.updated == 0 && repo.store.get(r.uid) == null); }

    // ---- transport editor
    private Runnable transportCard(LinearLayout col, final Request r) {
        final Transport t = r.transport;
        LinearLayout c = card("MOVEMENT");
        c.addView(row(
                picker("Carrier", names(Enums.Carrier.values()), t.carrier.ordinal(), i -> t.carrier = Enums.Carrier.values()[i]),
                picker("Load", names(Enums.LoadType.values()), t.loadType.ordinal(), i -> t.loadType = Enums.LoadType.values()[i])));

        final TextView pick = text("Pickup: " + grid(t.pickupText, t.pickupLat, t.pickupLon), 13, false, Style.TEXT);
        c.addView(pick);
        c.addView(row(
                secondary("PICKUP = MY POS", () -> { double[] p = repo.here(true); t.pickupLat = p[0]; t.pickupLon = p[1]; t.pickupText = repo.mgrs(p[0], p[1]); pick.setText("Pickup: " + grid(t.pickupText, t.pickupLat, t.pickupLon)); }),
                secondary("PICKUP = MAP CTR", () -> { double[] p = repo.here(false); t.pickupLat = p[0]; t.pickupLon = p[1]; t.pickupText = repo.mgrs(p[0], p[1]); pick.setText("Pickup: " + grid(t.pickupText, t.pickupLat, t.pickupLon)); })));
        final EditText dest = edit("Destination (grid or name, optional)", t.destText, TEXT);
        c.addView(dest);

        final EditText pax = edit("Personnel (PAX)", t.personnel > 0 ? String.valueOf(t.personnel) : "", NUM);
        final EditText veh = edit("Vehicles", t.vehicles > 0 ? String.valueOf(t.vehicles) : "", NUM);
        c.addView(row(pax, veh));
        final EditText litter = edit("Litter patients", t.litterPatients > 0 ? String.valueOf(t.litterPatients) : "", NUM);
        final EditText weight = edit("Cargo weight (kg)", t.cargoWeightKg > 0 ? String.valueOf((long) t.cargoWeightKg) : "", DEC);
        c.addView(row(litter, weight));
        final EditText cargo = edit("Cargo description", t.cargoDescription, TEXT);
        c.addView(cargo);

        final long[] win = { t.windowStart, t.windowEnd };
        final TextView winTv = text("", 13, false, Style.TEXT);
        Runnable showWin = () -> winTv.setText("Window: " + (win[0] <= 0 ? "ASAP" : Util.dtg(win[0]) + (win[1] > 0 ? " - " + Util.dtg(win[1]) : "")));
        showWin.run();
        c.addView(winTv);
        c.addView(row(
                secondary("ASAP", () -> { win[0] = 0; win[1] = 0; showWin.run(); }),
                secondary("START = NOW", () -> { win[0] = System.currentTimeMillis(); showWin.run(); }),
                secondary("+30 MIN END", () -> { if (win[0] <= 0) win[0] = System.currentTimeMillis(); win[1] = (win[1] > 0 ? win[1] : win[0]) + 1800_000L; showWin.run(); })));

        final EditText freq = edit("Freq", t.freq, TEXT);
        final EditText cs = edit("Callsign", t.callsign, CAPS);
        c.addView(row(freq, cs));
        final EditText marking = edit("Marking (e.g. VS-17, smoke, IR strobe)", t.marking, TEXT);
        c.addView(marking);
        col.addView(c);

        // LZ card for air
        LinearLayout lz = card("LANDING ZONE (air)");
        final EditText lzSize = edit("Size (e.g. 2-ship)", t.lzSize, TEXT);
        final EditText lzSurface = edit("Surface", t.lzSurface, TEXT);
        final EditText lzObs = edit("Obstacles", t.lzObstacles, TEXT);
        final EditText lzApp = edit("Approach / egress", t.lzApproach, TEXT);
        lz.addView(row(lzSize, lzSurface)); lz.addView(lzObs); lz.addView(lzApp);
        col.addView(lz);

        final EditText rem = edit("Remarks", t.remarks, TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout rc = card("REMARKS"); rc.addView(rem); col.addView(rc);

        return () -> {
            t.destText = str(dest); t.hasDest = !t.destText.isEmpty();
            t.personnel = intval(pax); t.vehicles = intval(veh); t.litterPatients = intval(litter);
            t.cargoWeightKg = decval(weight); t.cargoDescription = str(cargo);
            t.windowStart = win[0]; t.windowEnd = win[1];
            t.freq = str(freq); t.callsign = str(cs); t.marking = str(marking);
            t.lzSize = str(lzSize); t.lzSurface = str(lzSurface); t.lzObstacles = str(lzObs); t.lzApproach = str(lzApp);
            t.remarks = str(rem);
        };
    }

    // ---- resupply editor
    private Runnable resupplyCard(LinearLayout col, final Request r) {
        final Resupply s = r.resupply;
        LinearLayout c = card("RESUPPLY");
        final TextView loc = text("Deliver to: " + grid(s.locationText.isEmpty() ? s.deliverTo : s.locationText, s.lat, s.lon), 13, false, Style.TEXT);
        c.addView(loc);
        c.addView(row(
                secondary("HERE", () -> { double[] p = repo.here(true); s.lat = p[0]; s.lon = p[1]; s.locationText = repo.mgrs(p[0], p[1]); loc.setText("Deliver to: " + s.locationText); }),
                secondary("MAP CENTRE", () -> { double[] p = repo.here(false); s.lat = p[0]; s.lon = p[1]; s.locationText = repo.mgrs(p[0], p[1]); loc.setText("Deliver to: " + s.locationText); })));
        final EditText deliver = edit("Delivery point (name/notes)", s.deliverTo, TEXT);
        c.addView(deliver);

        final LinearLayout lines = column();
        c.addView(lines);
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            lines.removeAllViews();
            if (s.lines.isEmpty()) lines.addView(text("No items yet.", 13, false, Style.MUTED));
            for (final Resupply.Line l : s.lines)
                lines.addView(removable(l.summary(), () -> { s.lines.remove(l); draw[0].run(); }));
        };
        draw[0].run();
        c.addView(secondary("+ ITEM", () -> {
            final Resupply.SupplyClass[] cls = Resupply.SupplyClass.values();
            String[] labels = new String[cls.length];
            for (int i = 0; i < cls.length; i++) labels[i] = cls[i].label;
            choose("Class of supply", labels, ci -> form("Add item", new String[] { "Item", "Quantity" }, new int[] { TEXT, TEXT }, v -> {
                if (v[0].isEmpty()) return;
                s.lines.add(new Resupply.Line(cls[ci], v[0], v[1])); draw[0].run();
            }));
        }));
        final EditText rem = edit("Remarks", s.remarks, TEXT);
        c.addView(rem);
        col.addView(c);
        return () -> { s.deliverTo = str(deliver); s.remarks = str(rem); };
    }

    // ---- sitrep editor
    private Runnable sitrepCard(LinearLayout col, final Request r) {
        LinearLayout c = card("SITREP");
        final EditText sit = edit("Situation (enemy/friendly)", r.sitrep.situation, TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        final EditText act = edit("Activity (what we are doing)", r.sitrep.activity, TEXT);
        final EditText loc = edit("Location", r.sitrep.location, TEXT);
        final EditText nxt = edit("Next action", r.sitrep.nextAction, TEXT);
        c.addView(sit); c.addView(act); c.addView(loc); c.addView(nxt);
        col.addView(c);
        return () -> { r.sitrep.situation = str(sit); r.sitrep.activity = str(act); r.sitrep.location = str(loc); r.sitrep.nextAction = str(nxt); };
    }

    // ---- ace editor
    private Runnable aceCard(LinearLayout col, final Request r) {
        LinearLayout c = card("ACE REPORT");
        c.addView(row(
                picker("Ammo", names(Enums.AceState.values()), r.sitrep.ammo.ordinal(), i -> r.sitrep.ammo = Enums.AceState.values()[i]),
                picker("Equip", names(Enums.AceState.values()), r.sitrep.equipment.ordinal(), i -> r.sitrep.equipment = Enums.AceState.values()[i])));
        final EditText cas = edit("Casualties (this period)", r.sitrep.casualties > 0 ? String.valueOf(r.sitrep.casualties) : "", NUM);
        final EditText loc = edit("Location (optional, for LACE)", r.sitrep.location, TEXT);
        final EditText rem = edit("Remarks", r.sitrep.aceRemarks, TEXT);
        c.addView(cas); c.addView(loc); c.addView(rem);
        col.addView(c);
        return () -> { r.sitrep.casualties = intval(cas); r.sitrep.location = str(loc); r.sitrep.aceRemarks = str(rem); };
    }

    // ------------------------------------------------------------------ widgets

    private Context dialogContext() { return MapView.getMapView().getContext(); }
    private String grid(String text, double lat, double lon) { return text != null && !text.isEmpty() ? text : String.format(Locale.US, "%.5f %.5f", lat, lon); }

    private LinearLayout screen() {
        listVisible = false;
        clocks.clear();
        root.removeAllViews();
        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(Style.BG);
        sv.setFillViewport(true);
        LinearLayout col = column();
        col.setPadding(px(10), px(10), px(10), px(28));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return col;
    }
    private void startClocks() { if (ticking || clocks.isEmpty()) return; ticking = true; root.postDelayed(tick, TICK_MS); }

    private LinearLayout column() { LinearLayout l = new LinearLayout(ctx); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row(View... views) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBaselineAligned(false);
        for (int i = 0; i < views.length; i++) {
            int h = views[i] instanceof Button && !(views[i] instanceof CheckBox) ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1);
            lp.setMargins(i == 0 ? 0 : px(3), px(3), i == views.length - 1 ? 0 : px(3), px(3));
            l.addView(views[i], lp);
        }
        return l;
    }
    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(l), px(t), px(r), px(b));
        return lp;
    }
    private TextView text(String s, int sp, boolean bold, int colour) {
        TextView t = new TextView(ctx);
        t.setText(s); t.setTextSize(sp); t.setTextColor(colour);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(px(2), px(2), px(2), px(2));
        return t;
    }
    private View header(String title, String sub) {
        LinearLayout l = column();
        l.setPadding(px(2), 0, 0, px(8));
        l.addView(text(title, 20, true, Style.TEXT));
        if (sub != null && !sub.isEmpty()) l.addView(text(sub, 12, false, Style.MUTED));
        return l;
    }
    private LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(Style.round(Style.CARD, Style.STROKE, px(10), px(1)));
        l.setPadding(px(10), px(10), px(10), px(10));
        l.setLayoutParams(margins(0, 0, 0, 10));
        return l;
    }
    private LinearLayout card(String title) {
        LinearLayout l = card();
        TextView t = text(title, 12, true, Style.GREEN);
        t.setLetterSpacing(0.08f);
        t.setPadding(px(2), 0, 0, px(6));
        l.addView(t);
        return l;
    }
    private EditText edit(String hint, String value, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hint); e.setText(value == null ? "" : value); e.setInputType(inputType);
        e.setTextColor(Style.TEXT); e.setHintTextColor(Style.MUTED); e.setTextSize(15);
        e.setBackground(Style.round(Style.FIELD, Style.STROKE, px(8), px(1)));
        e.setPadding(px(10), px(9), px(10), px(9));
        e.setLayoutParams(margins(0, 3, 0, 3));
        return e;
    }
    private Button button(String label, int fill, int stroke, int textColour, final Runnable r) {
        Button b = new Button(ctx);
        b.setText(label); b.setAllCaps(false); b.setTextColor(textColour); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(Style.pressable(fill, stroke, px(8), stroke == 0 ? 0 : px(1)));
        b.setMinHeight(px(46)); b.setMinimumHeight(px(46));
        b.setPadding(px(8), px(6), px(8), px(6));
        if (r != null) b.setOnClickListener(v -> r.run());
        return b;
    }
    private Button primary(String label, Runnable r) { return button(label, Style.GREEN, 0, Style.on(Style.GREEN), r); }
    private Button secondary(String label, Runnable r) { return button(label, Style.CARD, Style.GREY, Style.TEXT, r); }
    private Button danger(String label, Runnable r) { return button(label, Style.BG, Style.RED, Style.RED, r); }

    private View removable(String s, Runnable onRemove) {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(text(s, 14, false, Style.TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button x = button("X", Style.CARD, Style.GREY, Style.MUTED, onRemove);
        x.setMinWidth(0); x.setMinimumWidth(0);
        l.addView(x, new LinearLayout.LayoutParams(px(44), px(40)));
        return l;
    }
    private Button picker(final String label, final String[] items, int current, final Pick cb) {
        final Button b = secondary(label + ": " + items[current], null);
        b.setOnClickListener(v -> choose(label, items, i -> { b.setText(label + ": " + items[i]); cb.on(i); }));
        return b;
    }
    private void choose(String title, String[] items, final Pick cb) {
        new AlertDialog.Builder(dialogContext()).setTitle(title)
                .setItems(items, (d, which) -> cb.on(which))
                .setNegativeButton("Cancel", null).show();
    }
    private void form(String title, String[] hints, int[] types, final Form cb) {
        Context dc = dialogContext();
        LinearLayout l = new LinearLayout(dc);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(8), px(16), px(8));
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) { fields[i] = new EditText(dc); fields[i].setHint(hints[i]); fields[i].setInputType(types[i]); l.addView(fields[i]); }
        ScrollView sv = new ScrollView(dc); sv.addView(l);
        new AlertDialog.Builder(dc).setTitle(title).setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String[] out = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) out[i] = str(fields[i]);
                    cb.on(out);
                }).show();
    }
    private void report(String title, final String body) {
        new AlertDialog.Builder(dialogContext()).setTitle(title).setMessage(body)
                .setNegativeButton("Close", null)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) dialogContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("report", body));
                    toast("Copied");
                }).show();
    }
    private void toast(String s) { Toast.makeText(dialogContext(), s, Toast.LENGTH_LONG).show(); }

    private int px(int dps) { return Math.round(dps * dp); }
    private static String str(EditText e) { return e.getText().toString().trim(); }
    private static int intval(EditText e) { try { return Integer.parseInt(str(e)); } catch (Exception x) { return 0; } }
    private static double decval(EditText e) { try { return Double.parseDouble(str(e)); } catch (Exception x) { return 0; } }
    private static String[] names(Enum<?>[] vals) {
        String[] out = new String[vals.length];
        for (int i = 0; i < vals.length; i++) out[i] = vals[i].name().replace('_', ' ');
        return out;
    }
}
