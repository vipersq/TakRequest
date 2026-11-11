package io.github.vipersq.takrequest.core;

import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Cursor-on-Target encoding. See docs/COT-SPEC.md.
 *
 * Each request is a spot marker (b-m-p-s-m) so every TAK client shows it with the plain-text report
 * in its remarks, plus a {@code __takrequest} detail only this plugin reads.
 */
public final class CotCodec {
    private CotCodec() {}

    public static final String TYPE = "b-m-p-s-m";
    public static final String DETAIL = "__takrequest";
    public static final int SCHEMA = 1;

    public static String toCot(Request r, long now, long staleMs) {
        try {
            Document d = newDoc();
            int argb = r.status.isDone() ? 0xFF5B6573 : r.priority.argb;
            Element detail = event(d, r.uid, now, staleMs, r.lat, r.lon);
            child(d, detail, "contact").setAttribute("callsign",
                    (r.mode.banner.isEmpty() ? "" : r.mode.banner + " ") + r.label());
            child(d, detail, "color").setAttribute("argb", String.valueOf(argb));
            child(d, detail, "remarks").setTextContent(Readout.text(r));

            Element k = child(d, detail, DETAIL);
            k.setAttribute("v", String.valueOf(SCHEMA));
            k.setAttribute("rev", String.valueOf(r.rev));
            k.setAttribute("updated", String.valueOf(r.updated));
            k.setAttribute("created", String.valueOf(r.created));
            k.setAttribute("editor", r.editor);
            k.setAttribute("origin", r.origin);
            k.setAttribute("kind", r.kind.name());
            k.setAttribute("mode", r.mode.name());
            k.setAttribute("status", r.status.name());
            k.setAttribute("priority", r.priority.name());
            k.setAttribute("title", r.title);

            switch (r.kind) {
                case TRANSPORT: encodeTransport(d, k, r.transport); break;
                case RESUPPLY: encodeResupply(d, k, r.resupply); break;
                case SITREP: case ACE: encodeSitrep(d, k, r.sitrep); break;
            }
            return write(d);
        } catch (Exception e) {
            throw new IllegalStateException("CoT encode failed", e);
        }
    }

    private static void encodeTransport(Document d, Element k, Transport t) {
        Element e = child(d, k, "transport");
        e.setAttribute("carrier", t.carrier.name());
        e.setAttribute("load", t.loadType.name());
        e.setAttribute("pickupText", t.pickupText);
        pos(e, "pickup", t.pickupLat, t.pickupLon);
        if (t.hasDest) { e.setAttribute("hasDest", "1"); e.setAttribute("destText", t.destText); pos(e, "dest", t.destLat, t.destLon); }
        num(e, "personnel", t.personnel); num(e, "litter", t.litterPatients); num(e, "vehicles", t.vehicles);
        if (t.cargoWeightKg > 0) e.setAttribute("weight", String.valueOf(t.cargoWeightKg));
        e.setAttribute("cargo", t.cargoDescription);
        if (t.windowStart > 0) e.setAttribute("winStart", String.valueOf(t.windowStart));
        if (t.windowEnd > 0) e.setAttribute("winEnd", String.valueOf(t.windowEnd));
        e.setAttribute("freq", t.freq); e.setAttribute("callsign", t.callsign); e.setAttribute("marking", t.marking);
        e.setAttribute("lzSize", t.lzSize); e.setAttribute("lzSurface", t.lzSurface);
        e.setAttribute("lzObstacles", t.lzObstacles); e.setAttribute("lzApproach", t.lzApproach);
        e.setAttribute("remarks", t.remarks);
    }

    private static void encodeResupply(Document d, Element k, Resupply s) {
        Element e = child(d, k, "resupply");
        e.setAttribute("deliverTo", s.deliverTo); e.setAttribute("locationText", s.locationText);
        pos(e, "loc", s.lat, s.lon);
        e.setAttribute("remarks", s.remarks);
        for (Resupply.Line l : s.lines) {
            Element le = child(d, e, "line");
            le.setAttribute("cls", l.cls.name()); le.setAttribute("item", l.item); le.setAttribute("qty", l.quantity);
        }
    }

    private static void encodeSitrep(Document d, Element k, Sitrep s) {
        Element e = child(d, k, "sitrep");
        e.setAttribute("situation", s.situation); e.setAttribute("activity", s.activity);
        e.setAttribute("location", s.location); e.setAttribute("nextAction", s.nextAction);
        e.setAttribute("ammo", s.ammo.name()); e.setAttribute("equipment", s.equipment.name());
        e.setAttribute("casualties", String.valueOf(s.casualties)); e.setAttribute("aceRemarks", s.aceRemarks);
    }

    /** Returns null when the event is not one of our requests. */
    public static Request fromCot(String xml) {
        try {
            Element ev = parse(xml);
            Element k = first(ev, DETAIL);
            if (k == null) return null;
            Request r = new Request();
            r.uid = ev.getAttribute("uid");
            Element pt = first(ev, "point");
            r.lat = dbl(pt, "lat", 0); r.lon = dbl(pt, "lon", 0);
            r.rev = (int) lng(k, "rev", 1); r.updated = lng(k, "updated", 0); r.created = lng(k, "created", 0);
            r.editor = k.getAttribute("editor"); r.origin = k.getAttribute("origin"); r.title = k.getAttribute("title");
            r.kind = en(Enums.Kind.class, k.getAttribute("kind"), Enums.Kind.TRANSPORT);
            r.mode = en(Enums.Mode.class, k.getAttribute("mode"), Enums.Mode.REAL);
            r.status = en(Enums.Status.class, k.getAttribute("status"), Enums.Status.DRAFT);
            r.priority = en(Enums.Priority.class, k.getAttribute("priority"), Enums.Priority.ROUTINE);
            Element te = first(k, "transport"), re = first(k, "resupply"), se = first(k, "sitrep");
            if (te != null) r.transport = decodeTransport(te);
            else if (re != null) r.resupply = decodeResupply(re);
            else if (se != null) r.sitrep = decodeSitrep(se);
            // Make sure the detail block matches the declared kind even if data is missing.
            if (r.kind == Enums.Kind.TRANSPORT && r.transport == null) r.transport = new Transport();
            if (r.kind == Enums.Kind.RESUPPLY && r.resupply == null) r.resupply = new Resupply();
            if ((r.kind == Enums.Kind.SITREP || r.kind == Enums.Kind.ACE) && r.sitrep == null) r.sitrep = new Sitrep();
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static Transport decodeTransport(Element e) {
        Transport t = new Transport();
        t.carrier = en(Enums.Carrier.class, e.getAttribute("carrier"), Enums.Carrier.LAND);
        t.loadType = en(Enums.LoadType.class, e.getAttribute("load"), Enums.LoadType.PERSONNEL);
        t.pickupText = e.getAttribute("pickupText"); t.pickupLat = dbl(e, "pickupLat", 0); t.pickupLon = dbl(e, "pickupLon", 0);
        t.hasDest = "1".equals(e.getAttribute("hasDest"));
        t.destText = e.getAttribute("destText"); t.destLat = dbl(e, "destLat", 0); t.destLon = dbl(e, "destLon", 0);
        t.personnel = (int) lng(e, "personnel", 0); t.litterPatients = (int) lng(e, "litter", 0); t.vehicles = (int) lng(e, "vehicles", 0);
        t.cargoWeightKg = dbl(e, "weight", 0); t.cargoDescription = e.getAttribute("cargo");
        t.windowStart = lng(e, "winStart", 0); t.windowEnd = lng(e, "winEnd", 0);
        t.freq = e.getAttribute("freq"); t.callsign = e.getAttribute("callsign"); t.marking = e.getAttribute("marking");
        t.lzSize = e.getAttribute("lzSize"); t.lzSurface = e.getAttribute("lzSurface");
        t.lzObstacles = e.getAttribute("lzObstacles"); t.lzApproach = e.getAttribute("lzApproach");
        t.remarks = e.getAttribute("remarks");
        return t;
    }

    private static Resupply decodeResupply(Element e) {
        Resupply s = new Resupply();
        s.deliverTo = e.getAttribute("deliverTo"); s.locationText = e.getAttribute("locationText");
        s.lat = dbl(e, "locLat", 0); s.lon = dbl(e, "locLon", 0); s.remarks = e.getAttribute("remarks");
        NodeList ls = e.getElementsByTagName("line");
        for (int i = 0; i < ls.getLength(); i++) {
            Element le = (Element) ls.item(i);
            s.lines.add(new Resupply.Line(en(Resupply.SupplyClass.class, le.getAttribute("cls"), Resupply.SupplyClass.X_OTHER),
                    le.getAttribute("item"), le.getAttribute("qty")));
        }
        return s;
    }

    private static Sitrep decodeSitrep(Element e) {
        Sitrep s = new Sitrep();
        s.situation = e.getAttribute("situation"); s.activity = e.getAttribute("activity");
        s.location = e.getAttribute("location"); s.nextAction = e.getAttribute("nextAction");
        s.ammo = en(Enums.AceState.class, e.getAttribute("ammo"), Enums.AceState.GREEN);
        s.equipment = en(Enums.AceState.class, e.getAttribute("equipment"), Enums.AceState.GREEN);
        s.casualties = (int) lng(e, "casualties", 0); s.aceRemarks = e.getAttribute("aceRemarks");
        return s;
    }

    // ---------------------------------------------------------------- helpers

    static String iso(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }
    private static Document newDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }
    private static Element event(Document d, String uid, long now, long staleMs, double lat, double lon) {
        Element ev = d.createElement("event");
        d.appendChild(ev);
        ev.setAttribute("version", "2.0"); ev.setAttribute("uid", uid); ev.setAttribute("type", TYPE);
        ev.setAttribute("how", "h-g-i-g-o");
        ev.setAttribute("time", iso(now)); ev.setAttribute("start", iso(now)); ev.setAttribute("stale", iso(now + staleMs));
        Element pt = child(d, ev, "point");
        pt.setAttribute("lat", String.format(Locale.US, "%.7f", lat)); pt.setAttribute("lon", String.format(Locale.US, "%.7f", lon));
        pt.setAttribute("hae", "9999999.0"); pt.setAttribute("ce", "9999999.0"); pt.setAttribute("le", "9999999.0");
        return child(d, ev, "detail");
    }
    private static void pos(Element e, String prefix, double lat, double lon) {
        e.setAttribute(prefix + "Lat", String.format(Locale.US, "%.7f", lat));
        e.setAttribute(prefix + "Lon", String.format(Locale.US, "%.7f", lon));
    }
    private static Element child(Document d, Element parent, String name) {
        Element e = d.createElement(name); parent.appendChild(e); return e;
    }
    private static void num(Element e, String name, int v) { if (v != 0) e.setAttribute(name, String.valueOf(v)); }
    private static String write(Document d) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString();
    }
    private static Element parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // Events arrive from the network: refuse DTDs and external entities.
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) { }
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) { }
        try { f.setExpandEntityReferences(false); } catch (Exception ignored) { }
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
    private static Element first(Element parent, String name) {
        if (parent == null) return null;
        NodeList l = parent.getElementsByTagName(name);
        for (int i = 0; i < l.getLength(); i++) { Node n = l.item(i); if (n instanceof Element) return (Element) n; }
        return null;
    }
    private static long lng(Element e, String a, long def) {
        try { return e == null || !e.hasAttribute(a) ? def : Long.parseLong(e.getAttribute(a).trim()); } catch (NumberFormatException x) { return def; }
    }
    private static double dbl(Element e, String a, double def) {
        try { return e == null || !e.hasAttribute(a) ? def : Double.parseDouble(e.getAttribute(a).trim()); } catch (NumberFormatException x) { return def; }
    }
    private static <T extends Enum<T>> T en(Class<T> c, String s, T def) {
        try { return s == null || s.isEmpty() ? def : Enum.valueOf(c, s); } catch (IllegalArgumentException x) { return def; }
    }
}
