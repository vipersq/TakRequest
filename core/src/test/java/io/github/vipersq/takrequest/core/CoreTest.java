package io.github.vipersq.takrequest.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class CoreTest {
    static final long T0 = 1794340800000L; // 2026-11-10T20:00:00Z

    static Request transport() {
        Request r = Request.of(Enums.Kind.TRANSPORT);
        r.title = "LIFT.1"; r.priority = Enums.Priority.IMMEDIATE; r.mode = Enums.Mode.EXERCISE;
        r.status = Enums.Status.SENT; r.origin = "HAWK"; r.lat = 51.0688; r.lon = -1.7945;
        Transport t = r.transport;
        t.carrier = Enums.Carrier.AIR; t.loadType = Enums.LoadType.MIXED;
        t.pickupText = "30U WB 844 581"; t.pickupLat = 51.0688; t.pickupLon = -1.7945;
        t.hasDest = true; t.destText = "FOB ROCK"; t.destLat = 51.1; t.destLon = -1.8;
        t.personnel = 6; t.vehicles = 1; t.cargoWeightKg = 250; t.cargoDescription = "Ammo & water <A&B>";
        t.windowStart = T0; t.windowEnd = T0 + 1800_000;
        t.freq = "45.500"; t.callsign = "ROCK TOC"; t.marking = "VS-17 panel";
        t.lzSize = "2 ship"; t.lzSurface = "firm grass"; t.lzObstacles = "wires N"; t.lzApproach = "from S";
        t.remarks = "Line 1\nżółć";
        r.touch(T0, "M6OER");
        return r;
    }

    @Test void transportRoundTrip() {
        Request a = transport();
        String xml = CotCodec.toCot(a, T0 + 1000, 3_600_000);
        assertTrue(xml.contains("type=\"b-m-p-s-m\""));
        assertTrue(xml.contains("EXERCISE LIFT.1"), xml);
        Request b = CotCodec.fromCot(xml);
        assertNotNull(b);
        assertEquals(a.uid, b.uid); assertEquals(2, b.rev); assertEquals("M6OER", b.editor);
        assertEquals(Enums.Kind.TRANSPORT, b.kind); assertEquals(Enums.Mode.EXERCISE, b.mode);
        assertEquals(Enums.Status.SENT, b.status); assertEquals(Enums.Priority.IMMEDIATE, b.priority);
        Transport t = b.transport;
        assertEquals(Enums.Carrier.AIR, t.carrier); assertEquals(Enums.LoadType.MIXED, t.loadType);
        assertEquals("30U WB 844 581", t.pickupText); assertEquals(51.0688, t.pickupLat, 1e-6);
        assertTrue(t.hasDest); assertEquals("FOB ROCK", t.destText);
        assertEquals(6, t.personnel); assertEquals(1, t.vehicles); assertEquals(250.0, t.cargoWeightKg, 0.1);
        assertEquals("Ammo & water <A&B>", t.cargoDescription); assertEquals("Line 1\nżółć", t.remarks);
        assertEquals(T0, t.windowStart); assertEquals("wires N", t.lzObstacles);
        assertEquals(xml, CotCodec.toCot(b, T0 + 1000, 3_600_000));
    }

    @Test void resupplyRoundTrip() {
        Request r = Request.of(Enums.Kind.RESUPPLY);
        r.title = "RESUP.2"; r.resupply.deliverTo = "CP 3"; r.resupply.lat = 51.07; r.resupply.lon = -1.79;
        r.resupply.lines.add(new Resupply.Line(Resupply.SupplyClass.V_AMMO, "5.56 ball", "2000"));
        r.resupply.lines.add(new Resupply.Line(Resupply.SupplyClass.I_RATIONS, "water", "40 L"));
        r.touch(T0, "A");
        Request b = CotCodec.fromCot(CotCodec.toCot(r, T0, 1000));
        assertEquals(Enums.Kind.RESUPPLY, b.kind); assertEquals(2, b.resupply.lines.size());
        assertEquals(Resupply.SupplyClass.V_AMMO, b.resupply.lines.get(0).cls);
        assertEquals("water", b.resupply.lines.get(1).item);
        assertTrue(Readout.resupply(b).contains("V 5.56 ball x2000"), Readout.resupply(b));
    }

    @Test void aceRoundTripAndText() {
        Request r = Request.of(Enums.Kind.ACE);
        r.title = "ACE.1"; r.sitrep.ammo = Enums.AceState.AMBER; r.sitrep.equipment = Enums.AceState.GREEN;
        r.sitrep.casualties = 2; r.sitrep.location = "grid 123"; r.touch(T0, "B");
        Request b = CotCodec.fromCot(CotCodec.toCot(r, T0, 1000));
        assertEquals(Enums.Kind.ACE, b.kind); assertEquals(Enums.AceState.AMBER, b.sitrep.ammo);
        assertEquals(2, b.sitrep.casualties);
        String txt = Readout.sitrep(b);
        assertTrue(txt.contains("ACE REPORT")); assertTrue(txt.contains("A (ammo): AMBER")); assertTrue(txt.contains("C (casualties): 2"));
    }

    @Test void foreignAndHostileInput() {
        assertNull(CotCodec.fromCot("<event uid='x' type='a-f-G'><point lat='1' lon='2'/><detail/></event>"));
        assertNull(CotCodec.fromCot("not xml"));
        String xxe = "<!DOCTYPE e [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><event uid='x'><detail><__takrequest kind='TRANSPORT'><transport remarks='&x;'/></__takrequest></detail></event>";
        assertNull(CotCodec.fromCot(xxe));
        // unknown enums fall back instead of throwing
        Request r = CotCodec.fromCot("<event uid='x'><point lat='bad' lon='2'/><detail><__takrequest kind='NOPE' status='FUTURE' priority='X'><transport carrier='ORBIT' personnel='abc'/></__takrequest></detail></event>");
        assertNotNull(r);
        assertEquals(Enums.Kind.TRANSPORT, r.kind); assertEquals(Enums.Status.DRAFT, r.status);
        assertEquals(Enums.Priority.ROUTINE, r.priority); assertEquals(Enums.Carrier.LAND, r.transport.carrier);
        assertEquals(0, r.transport.personnel);
    }

    @Test void mergeIsLastWriterWins() {
        Request base = transport();
        Request x = CotCodec.fromCot(CotCodec.toCot(base, T0, 1000)); x.status = Enums.Status.APPROVED; x.touch(T0 + 1000, "ALPHA");
        Request y = CotCodec.fromCot(CotCodec.toCot(base, T0, 1000)); y.status = Enums.Status.DENIED; y.touch(T0 + 2000, "BRAVO");
        RequestStore s1 = new RequestStore(), s2 = new RequestStore();
        s1.putLocal(base); s2.putLocal(base);
        assertTrue(s1.mergeRemote(x)); assertTrue(s1.mergeRemote(y));
        assertTrue(s2.mergeRemote(y)); assertFalse(s2.mergeRemote(x));
        assertEquals(Enums.Status.DENIED, s1.get(base.uid).status);
        assertEquals(Enums.Status.DENIED, s2.get(base.uid).status);
        assertFalse(s1.mergeRemote(base), "stale revision must not overwrite");
    }

    @Test void sortingOpenBeforeDoneThenPriority() {
        Request flash = Request.of(Enums.Kind.SITREP); flash.priority = Enums.Priority.FLASH; flash.created = 10;
        Request routine = Request.of(Enums.Kind.SITREP); routine.priority = Enums.Priority.ROUTINE; routine.created = 20;
        Request doneFlash = Request.of(Enums.Kind.SITREP); doneFlash.priority = Enums.Priority.FLASH; doneFlash.status = Enums.Status.COMPLETE; doneFlash.created = 30;
        RequestStore s = new RequestStore(); s.putLocal(routine); s.putLocal(doneFlash); s.putLocal(flash);
        assertEquals(Arrays.asList(flash.uid, routine.uid, doneFlash.uid),
                Arrays.asList(s.sorted().get(0).uid, s.sorted().get(1).uid, s.sorted().get(2).uid));
    }

    @Test void modeBannerAndStatus() {
        assertEquals("", Enums.Mode.REAL.banner);
        assertEquals("MILSIM", Enums.Mode.MILSIM.banner);
        assertTrue(Enums.Status.SENT.isOpen()); assertFalse(Enums.Status.DENIED.isOpen());
        assertTrue(Enums.Status.CANCELLED.isDone());
        Request r = transport();
        assertTrue(Readout.transport(r).startsWith("*** EXERCISE *** LIFT.1"), Readout.transport(r));
    }

    @Test void timeFormatting() {
        assertEquals("0 min", Util.until(T0, T0 - 5000));
        assertEquals("45 min", Util.until(T0, T0 + 45 * 60000L));
        assertEquals("2 h 05 min", Util.until(T0, T0 + 125 * 60000L));
        assertEquals("102000ZNOV26", Util.dtg(T0));
    }
}
