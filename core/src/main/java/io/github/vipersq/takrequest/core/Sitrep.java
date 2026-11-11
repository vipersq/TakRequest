package io.github.vipersq.takrequest.core;

/**
 * SITREP and ACE/LACE report content. A request carries whichever block matches its kind.
 * ACE = Ammunition, Casualties, Equipment; LACE adds Location at the front.
 */
public class Sitrep {
    // --- SITREP free-text lines
    public String situation = "";   // enemy / friendly picture
    public String activity = "";    // what we are doing
    public String location = "";    // grid/text
    public String nextAction = "";

    // --- ACE / LACE
    public Enums.AceState ammo = Enums.AceState.GREEN;
    public Enums.AceState equipment = Enums.AceState.GREEN;
    public int casualties;          // count in this period
    public String aceRemarks = "";
}
