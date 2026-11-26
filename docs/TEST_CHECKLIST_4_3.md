# Manual Test Checklist — TimesynWarEngine 4.3

Target device: Tecno Pova, Android 15

## Install and launch
- [ ] Confirm the GitHub Actions build and unit-test job is green before using its APK artifact.
- [ ] Download the Debug APK from the GitHub Actions artifact.
- [ ] Install over the previous test build if Android permits the update; otherwise uninstall the old build first.
- [ ] Confirm app version is 4.3-WarEdition (versionCode 7).
- [ ] Open the app and confirm the main screen renders without crashing.

## Manual millisecond input
- [ ] Confirm the delay field opens a numeric keyboard.
- [ ] Enter 180, 320, and 1200 ms and confirm values are retained.
- [ ] Enter a value below 180 or above 1200; confirm launch clamps it to the supported range.
- [ ] Relaunch the app and confirm the last applied delay is retained.

## Hybrid / Safe calibration
- [ ] Start calibration while Auto Click is idle.
- [ ] Confirm progress is shown and no touch is sent to Shopee or any other app.
- [ ] Confirm result shows successful network samples, median RTT, p95 RTT, jitter, local p95, and recommendation.
- [ ] Confirm automatic recommendation is between 320 and 1200 ms.
- [ ] Press Close after completion; confirm only the dialog closes.
- [ ] Run calibration again and press Apply; confirm the recommendation appears in the delay field and is saved.
- [ ] Turn off internet and repeat; confirm calibration reports failure/insufficient samples, does not crash, and does not overwrite the saved delay.
- [ ] Try calibration while countdown/Auto Click is active; confirm it is blocked.

## Existing behavior regression
- [ ] Confirm NTP sync still works.
- [ ] Confirm overlay permission and Accessibility status display correctly.
- [ ] Confirm overlay launch, target handles, Start/Stop, and Auto-Stop still behave as before.
- [ ] Do not validate click speed against a live checkout/payment flow; use a harmless test screen only.

Record actual recommended delay, Wi-Fi/mobile network, Android version, and any error text when reporting results.

## Target time and click tuning (4.3)
- [ ] Confirm target time accepts exactly HH:mm:ss:SSS, for example 20:00:00:250.
- [ ] Confirm milliseconds 000 and 999 are accepted and malformed/out-of-range values are rejected.
- [ ] Confirm the target time is scheduled using the entered millisecond component.
- [ ] Confirm execution correction accepts -500 to +500 ms; positive values mean earlier execution and negative values mean later execution.
- [ ] Confirm repeat click interval accepts 180–1000 ms and the configured value is used consistently.
- [ ] Confirm Target 1 → Target 2 delay remains separately adjustable at 180–1200 ms.
- [ ] Confirm all three settings persist after closing and reopening the app.
- [ ] Test scheduling only on a harmless test screen; Android scheduling and Accessibility dispatch cannot guarantee physical tap precision to 1 ms.
