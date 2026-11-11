# TakRequest CoT format, schema v1

The contract shared by any client that reads TakRequest data (and the future WinTAK build). Both
sides must read and write exactly this.

## Request marker

- Event type `b-m-p-s-m` (spot marker), so clients without the plugin still show a marker.
- `<contact callsign>` is the request title, prefixed with the mode banner (EXERCISE/MILSIM/TRAINING)
  when the mode is not REAL.
- `<color argb>` follows priority (FLASH red, IMMEDIATE amber, PRIORITY blue, ROUTINE green); a
  completed/cancelled/denied request is grey.
- `<remarks>` holds the plain-text report, readable on any client.
- `<__takrequest v="1">` holds the structured request. Only the plugin reads it.

| Element | Attributes |
|---|---|
| `__takrequest` | `v`, `rev`, `updated`, `created` (epoch ms), `editor`, `origin`, `kind`, `mode`, `status`, `priority`, `title` |
| `transport` | `carrier`, `load`, `pickupText/Lat/Lon`, `hasDest`, `destText/Lat/Lon`, `personnel`, `litter`, `vehicles`, `weight`, `cargo`, `winStart`, `winEnd`, `freq`, `callsign`, `marking`, `lzSize/Surface/Obstacles/Approach`, `remarks` |
| `resupply` | `deliverTo`, `locationText`, `locLat/Lon`, `remarks`; children `line cls item qty` |
| `sitrep` | `situation`, `activity`, `location`, `nextAction`, `ammo`, `equipment`, `casualties`, `aceRemarks` |

`kind` is `TRANSPORT`, `RESUPPLY`, `SITREP` or `ACE`. A request carries exactly one detail block.
A reader must treat an unknown `kind`, `mode`, `status` or `priority` as the first enum value
(`TRANSPORT` / `REAL` / `DRAFT` / `ROUTINE` is the fallback shape, but kinds decode by their own block).

Conflict rule: higher `rev` wins; on a tie the later `updated`; then the greater `editor` string.
Every device applies the same rule, so all devices converge.

## Scope

TakRequest carries movement, supply and situation reports only. It has no fire-support, call-for-fire
or targeting content, and the format deliberately has no field for any of that.
