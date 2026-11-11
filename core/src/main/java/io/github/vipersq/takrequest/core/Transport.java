package io.github.vipersq.takrequest.core;

/** Transport-specific detail of a movement request (air, sea or land). */
public class Transport {
    public Enums.Carrier carrier = Enums.Carrier.LAND;
    public Enums.LoadType loadType = Enums.LoadType.PERSONNEL;

    /** Pickup point. lat/lon come from the map; locationText is the grid as shown (MGRS), set by the host app. */
    public double pickupLat, pickupLon;
    public String pickupText = "";
    /** Destination; optional. */
    public boolean hasDest;
    public double destLat, destLon;
    public String destText = "";

    public int personnel;
    public int litterPatients;        // non-ambulatory, for medical moves
    public String cargoDescription = ""; // free text
    public double cargoWeightKg;
    public int vehicles;

    /** Requested pickup window, epoch ms; 0 when unset. */
    public long windowStart, windowEnd;

    /** Radio/marking details for the pickup, e.g. HLZ marking for an air move. */
    public String freq = "", callsign = "", marking = "";
    /** Landing zone description for an air move (surface, obstacles, approach, size). */
    public String lzSurface = "", lzObstacles = "", lzApproach = "", lzSize = "";
    public String remarks = "";

    public boolean isAir() { return carrier == Enums.Carrier.AIR; }
}
