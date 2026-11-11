#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "TakRequest",
   plugin-version: "0.1",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
  = Overview

#toolbox.side-by-side(columns: (.75fr, 9fr))[
#image("plugin_icon.png", width: 70%)
][
TakRequest is a request and reporting board for ATAK. It covers transport (air, sea and land),
resupply, SITREP and ACE reports, shown on a shared board and on the map, exchanged over CoT.

This is a coordination and reporting tool. It is not a targeting or fire-support system.
]
]

#tak-slide[
  = Request board

Select the TakRequest toolbar button to open the board. Requests are sorted with open items first,
by priority. Each row shows the kind, priority, status, who raised it and how long ago.

Select *New request* and choose a kind. Select any row to open its editor.
]

#tak-slide[
  = Transport, resupply, SITREP, ACE

- *Transport*: carrier and load, pickup and destination (filled from your position or the map centre),
  personnel, vehicles, cargo and weight, time window, comms, and a landing-zone block for air moves.
- *Resupply*: items by NATO class of supply, with a delivery point.
- *SITREP*: situation, activity, location, next action.
- *ACE*: ammunition, casualties and equipment state, with an optional location for LACE.
]

#tak-slide[
  = Status, modes and sharing

Use the status buttons to move a request through its workflow: sent, acknowledged, approved or denied,
in progress, complete or cancelled. *Save + send* shares it with the team; the map marker shows the
kind, priority and status, and carries a plain-text report any client can read.

The mode (REAL, EXERCISE, MILSIM, TRAINING) is shown on every request and marker, with a dashed ring
on the marker for any non-real mode, so a drill is never mistaken for real tasking.
]
