package io.github.vipersq.takrequest.core;

import java.util.ArrayList;
import java.util.List;

/** A supply request, itemised by class of supply. */
public class Resupply {
    /** NATO classes of supply, plain-language. */
    public enum SupplyClass {
        I_RATIONS("I Rations/water"), II_CLOTHING("II Clothing/equipment"), III_FUEL("III Fuel/POL"),
        IV_FORTIFICATION("IV Fortification"), V_AMMO("V Ammunition"), VI_PERSONAL("VI Personal"),
        VII_MAJOR("VII Major end items"), VIII_MEDICAL("VIII Medical"), IX_REPAIR("IX Repair parts"), X_OTHER("X Other");
        public final String label;
        SupplyClass(String l) { label = l; }
    }

    public static class Line {
        public SupplyClass cls = SupplyClass.I_RATIONS;
        public String item = "";
        public String quantity = "";
        public Line() {}
        public Line(SupplyClass c, String item, String qty) { this.cls = c; this.item = item; this.quantity = qty; }
        public String summary() { return cls.name().replaceAll("_.*", "") + " " + item + (quantity.isEmpty() ? "" : " x" + quantity); }
    }

    public List<Line> lines = new ArrayList<>();
    public String deliverTo = "";       // grid/text of the delivery point
    public double lat, lon;
    public String locationText = "";
    public String remarks = "";
}
