TakRequest

_________________________________________________________________
PURPOSE AND CAPABILITIES

TakRequest is an ATAK plugin for requests and reporting: transport (air/sea/land), resupply, SITREP
and ACE reports, shown on a board and on the map, shared over CoT with a simple approval workflow.
It has EXERCISE, MILSIM and TRAINING modes that mark every request and marker.

This is a coordination and reporting tool. It is not a targeting or fire-support system and has no
call-for-fire or weapon-tasking content.

_________________________________________________________________
STATUS

In progress. Version 0.1, first test build. Not released.

_________________________________________________________________
POINT OF CONTACTS

Kriss (GitHub: vipersq)

_________________________________________________________________
PORTS REQUIRED

None. The plugin opens no ports. Requests are sent as CoT through ATAK's existing connections.

_________________________________________________________________
EQUIPMENT REQUIRED

An Android device running ATAK-CIV.

_________________________________________________________________
COMPILATION

    ./gradlew assembleCivRelease

For a local build, place this folder in <ATAK SDK>/samples/takrequest and create local.properties
with sdk.dir=<path to Android SDK>. The debug build loads only in the developer ATAK in the SDK.

_________________________________________________________________
DEVELOPER NOTES

- core/ holds the platform-neutral logic and the CoT format (docs/COT-SPEC.md). No Android deps.
- Requests are saved under atak/tools/takrequest/ through the ATAK IO provider.
- No third-party libraries are bundled.
