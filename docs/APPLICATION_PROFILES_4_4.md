# Application Profiles & Custom Layout — 4.4

## Scope
Version 4.4 introduces locally stored application profiles. Each profile stores:
- Display name and Android package name.
- Target time in strict `HH:mm:ss:SSS` format.
- Execution offset (-500..+500 ms).
- Click interval (180..1000 ms).
- Target delay (180..1200 ms).
- Up to 10 ordered, enabled/disabled TAP actions with normalized X/Y coordinates and optional per-action delay (0 or 100..5000 ms; 0 uses the profile default).

## Storage
Profiles are stored as JSON in the app's private files directory in `application_profiles.json`. The repository has a schema version and supports create/update, list, delete, and duplicate. Duplicate profiles receive new profile and action IDs; profiles may share a package name.

## Coordinate model
X and Y are normalized to 0.0..1.0 relative to the layout preview. The execution service maps those values to the current display dimensions. Android status/navigation bars, display cutouts, app content insets, and device-specific scaling can introduce offsets. Always verify the marker position on the actual device before execution.

## Execution behavior
- A profile is manually selected and explicitly confirmed.
- The target app is opened using its launcher activity.
- The user must manually press MULAI after confirming the target app is foregrounded.
- The custom sequence executes enabled actions once, in ascending order.
- Each action uses its explicit delay when set; otherwise the first transition uses the profile Target Delay and later transitions use the profile Click Interval.
- The active window package is checked before each action; execution stops if the foreground package no longer matches.
- The TEST control sends only one tap to the first enabled action.
- There is no automatic loop/repeat in custom profile mode.

## Safety
Do not test on payment, purchase, submit, delete, or other consequential controls. Start with a harmless screen and one action. Keep the floating STOP control available. Accessibility gestures are subject to Android scheduling and are not guaranteed to land at exact millisecond precision.

## Existing behavior
The existing 4.3 Shopee workflow and Hybrid/Safe delay calibration remain available. Custom profiles do not invoke the Shopee-specific network warm-up request. Hybrid calibration metrics are not yet stored independently per profile; the profile's timing fields are stored independently.

## Known limitations for 4.4
- Coordinates are normalized against a simplified full-screen preview; app-specific content insets and display cutouts may require manual adjustment.
- The editor currently supports TAP actions only.
- Per-profile automatic calibration, import/export, swipe/long-press actions, and automatic app detection are not included in this first implementation.
