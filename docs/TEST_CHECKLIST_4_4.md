# TimesynWarEngine 4.4 — Device Test Checklist

Device target: Tecno Pova, Android 15.

## Profile management
- [ ] Open Application Profiles from the main screen.
- [ ] Create a profile with a valid package name.
- [ ] Reject malformed package names and invalid target times.
- [ ] Save two profiles and verify their timing values remain independent.
- [ ] Edit, duplicate, and delete a profile.
- [ ] Close/reopen the app and confirm profiles persist.

## Layout editor
- [ ] Open Layout for a profile.
- [ ] Tap preview to add 1–3 points.
- [ ] Drag a point and verify coordinates update.
- [ ] Rename an action and adjust its delay.
- [ ] Reorder and delete actions.
- [ ] Confirm the 10-action limit and coordinate validation.
- [ ] Save, reopen, and confirm layout persistence.

## Safe execution test
- [ ] Use a harmless test application/screen, not a payment or purchase flow.
- [ ] Confirm overlay and Accessibility permissions.
- [ ] Select PAKAI and confirm the target app opens.
- [ ] Confirm numbered markers align with the intended harmless controls.
- [ ] Press TES and verify only the first enabled action is sent once.
- [ ] Press MULAI and verify enabled actions run once in order.
- [ ] Change away from the target app and verify execution stops.
- [ ] Press STOP during a countdown/sequence and verify no further action is sent.

## Regression
- [ ] Existing 4.3 target time parser and timing controls still work.
- [ ] Existing Hybrid/Safe calibration still refuses to run during an active sequence.
- [ ] Main app launch, overlay permission, and Accessibility service remain functional.

Do not test against real transactions. Android gesture dispatch and UI rendering are not millisecond-exact.
