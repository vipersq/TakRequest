package io.github.vipersq.takrequest.atak;

import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.github.vipersq.takrequest.core.CotCodec;
import io.github.vipersq.takrequest.core.Request;
import io.github.vipersq.takrequest.core.RequestStore;

/** Glue between the platform-neutral core and ATAK: storage, map markers and the network. */
class Repo implements CotServiceRemote.CotEventListener {
    private static final String TAG = "TakRequest";
    static final long STALE_MS = 24L * 60 * 60 * 1000;
    private static final String META_ICON = "takrequest_icon";

    final RequestStore store = new RequestStore();
    private File dir;
    Runnable onRemoteChange;
    private boolean started;

    private final MapEventDispatcher.MapEventDispatchListener iconListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override public void onMapEvent(MapEvent event) {
            try {
                MapItem item = event.getItem();
                if (item != null) applyIcon(item, false);
            } catch (Exception e) {
                Log.e(TAG, "could not set marker icon", e);
            }
        }
    };

    void start() {
        if (started) return;
        started = true;
        dir = FileSystemUtils.getItem("tools/takrequest");
        try {
            if (!IOProviderFactory.exists(dir)) IOProviderFactory.mkdirs(dir);
            File[] files = IOProviderFactory.listFiles(dir);
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().endsWith(".cot")) continue;
                    Request r = CotCodec.fromCot(read(f));
                    if (r != null) store.mergeRemote(r);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "could not load saved requests", e);
        }
        CommsMapComponent.getInstance().addOnCotEventListener(this);
        MapEventDispatcher med = MapView.getMapView().getMapEventDispatcher();
        med.addMapEventListener(MapEvent.ITEM_ADDED, iconListener);
        med.addMapEventListener(MapEvent.ITEM_REFRESH, iconListener);
        for (Request r : store.sorted()) refreshIcon(r.uid);
    }

    void stop() {
        if (!started) return;
        started = false;
        CommsMapComponent.getInstance().removeOnCotEventListener(this);
        MapEventDispatcher med = MapView.getMapView().getMapEventDispatcher();
        med.removeMapEventListener(MapEvent.ITEM_ADDED, iconListener);
        med.removeMapEventListener(MapEvent.ITEM_REFRESH, iconListener);
    }

    String callsign() {
        MapView mv = MapView.getMapView();
        String cs = mv == null ? null : mv.getDeviceCallsign();
        return cs == null ? "" : cs;
    }

    /** Own position if there is a fix, otherwise the centre of the map, as {lat, lon}. */
    double[] here(boolean preferSelf) {
        MapView mv = MapView.getMapView();
        GeoPoint p = null;
        if (preferSelf) {
            Marker self = mv.getSelfMarker();
            GeoPoint sp = self == null ? null : self.getPoint();
            if (sp != null && sp.isValid() && !(sp.getLatitude() == 0 && sp.getLongitude() == 0)) p = sp;
        }
        if (p == null) p = mv.getCenterPoint().get();
        return new double[] { p.getLatitude(), p.getLongitude() };
    }

    String mgrs(double lat, double lon) {
        try {
            return CoordinateFormatUtilities.formatToString(new GeoPoint(lat, lon), CoordinateFormat.MGRS);
        } catch (Exception e) {
            return "";
        }
    }

    private void pullPointFromMap(Request r) {
        MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(r.uid);
        if (mi instanceof com.atakmap.android.maps.PointMapItem) {
            GeoPoint p = ((com.atakmap.android.maps.PointMapItem) mi).getPoint();
            if (p != null && p.isValid()) { r.lat = p.getLatitude(); r.lon = p.getLongitude(); }
        }
    }

    boolean save(Request r, boolean send) {
        if (store.get(r.uid) != null) pullPointFromMap(r);
        r.touch(System.currentTimeMillis(), callsign());
        store.putLocal(r);
        String xml = CotCodec.toCot(r, System.currentTimeMillis(), STALE_MS);
        write(new File(dir, safe(r.uid) + ".cot"), xml);
        boolean ok = publish(xml, send);
        refreshIcon(r.uid);
        return ok;
    }

    void delete(String uid) {
        store.remove(uid);
        try {
            File f = new File(dir, safe(uid) + ".cot");
            if (IOProviderFactory.exists(f)) IOProviderFactory.delete(f);
        } catch (Exception e) {
            Log.e(TAG, "could not delete saved request", e);
        }
        MapItem mi = MapView.getMapView().getRootGroup().deepFindUID(uid);
        if (mi != null) mi.removeFromGroup();
    }

    private boolean publish(String xml, boolean external) {
        CotEvent ev = CotEvent.parse(xml);
        if (ev == null || !ev.isValid()) {
            Log.e(TAG, "generated CoT was rejected by ATAK");
            return false;
        }
        CotMapComponent.getInternalDispatcher().dispatch(ev);
        if (external) CotMapComponent.getExternalDispatcher().dispatch(ev);
        return true;
    }

    @Override
    public void onCotEvent(CotEvent ev, Bundle extras) {
        try {
            if (ev == null || !CotCodec.TYPE.equals(ev.getType()) || ev.getDetail() == null
                    || ev.getDetail().getFirstChildByName(0, CotCodec.DETAIL) == null) return;
            final Request r = CotCodec.fromCot(ev.toString());
            if (r == null) return;
            if (!store.mergeRemote(r)) return;
            write(new File(dir, safe(r.uid) + ".cot"), CotCodec.toCot(r, System.currentTimeMillis(), STALE_MS));
            refreshIcon(r.uid);
            final MapView mv = MapView.getMapView();
            mv.post(new Runnable() {
                @Override public void run() {
                    Toast.makeText(mv.getContext(), (r.mode.banner.isEmpty() ? "" : r.mode.banner + " ")
                            + "Request " + r.label() + ": " + r.status, Toast.LENGTH_SHORT).show();
                    if (onRemoteChange != null) onRemoteChange.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "failed to handle incoming request", e);
        }
    }

    private static boolean isRequestUid(String uid) { return uid != null && uid.startsWith("TAKREQ-"); }

    private void applyIcon(MapItem item, boolean force) {
        if (!(item instanceof Marker) || !isRequestUid(item.getUID())) return;
        Request r = store.get(item.getUID());
        if (r == null) return;
        String key = MarkerIcons.key(r);
        if (!force && key.equals(item.getMetaString(META_ICON, ""))) return;
        Marker m = (Marker) item;
        m.setMetaBoolean("adapt_marker_icon", false);
        m.setIcon(new Icon.Builder().setImageUri(Icon.STATE_DEFAULT, MarkerIcons.uri(r))
                .setSize(MarkerIcons.DISPLAY, MarkerIcons.DISPLAY).build());
        m.setMetaString(META_ICON, key);
    }

    private void refreshIcon(final String uid) {
        final MapView mv = MapView.getMapView();
        Runnable r = new Runnable() {
            @Override public void run() {
                try {
                    MapItem mi = mv.getRootGroup().deepFindUID(uid);
                    if (mi != null) applyIcon(mi, true);
                } catch (Exception e) {
                    Log.e(TAG, "could not refresh marker icon", e);
                }
            }
        };
        mv.postDelayed(r, 400);
        mv.postDelayed(r, 2500);
    }

    private static String safe(String uid) { return uid.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static void write(File f, String s) {
        try (OutputStream os = IOProviderFactory.getOutputStream(f)) {
            os.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "could not save " + f.getName(), e);
        }
    }

    private static String read(File f) throws Exception {
        try (InputStream is = IOProviderFactory.getInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
