# Manual Test Checklist — TimesynWarEngine 4.2

Target device: Tecno Pova, Android 15

## Install and launch
- [ ] Download the Debug APK from the GitHub Actions artifact.
- [ ] Install over the previous test build if Android permits the update; otherwise uninstall the old build first.
- [ ] Confirm app version is 4.2-WarEdition (versionCode 6).
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
