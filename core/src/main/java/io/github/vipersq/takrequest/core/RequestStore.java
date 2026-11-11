package io.github.vipersq.takrequest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory set of requests with last-writer-wins merge for records arriving from the network. */
public class RequestStore {
    public interface Listener { void onChanged(Request r, boolean fromNetwork); }

    private final Map<String, Request> byUid = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public synchronized void addListener(Listener l) { listeners.add(l); }
    public synchronized Request get(String uid) { return byUid.get(uid); }
    public synchronized int size() { return byUid.size(); }

    public synchronized void putLocal(Request r) { byUid.put(r.uid, r); fire(r, false); }

    public synchronized boolean remove(String uid) { return byUid.remove(uid) != null; }

    /** Merge a record received from another device. Returns true if it replaced or added a record. */
    public synchronized boolean mergeRemote(Request incoming) {
        Request mine = byUid.get(incoming.uid);
        if (mine != null && !mine.isSupersededBy(incoming)) return false;
        byUid.put(incoming.uid, incoming);
        fire(incoming, true);
        return true;
    }

    /** Open requests first, then done; within a group most urgent first, then newest first. */
    public synchronized List<Request> sorted() {
        List<Request> out = new ArrayList<>(byUid.values());
        Collections.sort(out, new Comparator<Request>() {
            @Override public int compare(Request a, Request b) {
                int g = Integer.compare(a.status.isDone() ? 1 : 0, b.status.isDone() ? 1 : 0);
                if (g != 0) return g;
                int p = a.priority.compareTo(b.priority);
                if (p != 0) return p;
                return Long.compare(b.created, a.created);
            }
        });
        return out;
    }

    private void fire(Request r, boolean net) { for (Listener l : listeners) l.onChanged(r, net); }
}
