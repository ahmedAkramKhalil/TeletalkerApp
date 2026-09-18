# TeleTalker — Android >12 Support Plan (Recording + Injection across device variants)

**Status:** Planning only — no code changes made.
**Author:** analysis compiled 2026-07-05.
**Scope:** Make the full call flow (privileged install → two-way **recording** → AI **audio injection**) work on Android 13/14(/15) and on device models beyond the current reference, **without regressing the known-good baseline**.

---

## 0. Known-good baseline (do not regress)

**Samsung, Android 12 (One UI 4.x / API 31) — FULL flow confirmed working:**
- Privileged install via the Magisk module (system priv-app).
- Two-way **recording** (`AudioSource.VOICE_CALL`, both parties captured).
- **Injection** — AI voice heard by the remote caller via tinyalsa in-call mixer (`CALL_REC Audio Mixer` path).

On this device the current chain is *sufficient as-is*: the Magisk `privapp-permissions` allowlist + root `pm grant` + `setenforce 0` + `detect_mixers.sh` Samsung/Exynos path all work together. **Any change below must be version-/device-gated so Samsung A12 behavior is provably unchanged.** This device is the regression baseline for every test cycle.

> Correction to earlier analysis: the "missing `DUMP` in the privapp allowlist → bootloop on enforce devices" concern is a **latent portability risk to verify per device**, NOT a defect that affects the baseline. It is demoted to a diagnostic check, not a blocking fix.

---

## 1. Objective & non-goals

**Objective:** the exact record+inject flow that works on Samsung A12 continues to work on:
- **Axis 1 — newer Android:** API 33 (13), API 34 (14), and a decision point on API 35 (15).
- **Axis 2 — other device models:** other Snapdragon/Exynos units, and a *graceful, honest* degradation path on MediaTek / devices without an in-call injection mixer, plus OEM process-killer resilience (Xiaomi/Oppo/Vivo).

**Non-goals (this phase):**
- Rewriting the injection mechanism or supporting non-root installs for injection (injection is inherently root + HAL bound).
- Changing the ElevenLabs/WebSocket audio pipeline.
- Play Store distribution / targetSdk-35 compliance deadlines (app is sideloaded via Magisk).

---

## 2. Mental model: two independent axes

Permissions/capabilities for this app come in two layers, and portability breaks along two axes. Keeping them separate prevents mis-diagnosis.

| | Axis 1: Android version (12→13→14→15) | Axis 2: device model (OEM/SoC) |
|---|---|---|
| **Recording privilege** (`CAPTURE_AUDIO_OUTPUT`, appops) | survives — privileged install unaffected by version | breaks if privapp enforce mode / SELinux differ, or HAL has 2-way fused off |
| **Injection** (tinyalsa mixer) | unaffected by Android version | **primary failure point** — needs a recognized in-call mixer |
| **Foreground services** | **breaks on API 34** (FGS type rules) even as priv-app | mostly consistent |
| **Runtime perms** (POST_NOTIFICATIONS, media) | changes on API 33 | consistent |
| **Lifecycle** (boot restart, alarms) | consistent | **breaks on Xiaomi/Oppo/Vivo** (autostart/battery killers) |

**Takeaway:** Axis 1 work is almost entirely *app code* (foreground services + runtime permissions). Axis 2 work is almost entirely *Magisk module + device detection + OEM guidance*. They can proceed independently.

---

## 3. Current-state reference (facts, with locations)

### 3.1 SDK config
- `compileSdk 34`, `targetSdk 34`, `minSdk 26` (`app/build.gradle:10,13-14`). Not targeting API 35.

### 3.2 The two privileged flows

**Recording** — `services/CallRecorder.java` (and `services/ai/CallRecorder.java`):
- Audio source priority `VOICE_CALL → VOICE_COMMUNICATION → MIC` (`services/CallRecorder.java:53-63`). Only `VOICE_CALL`/`VOICE_UPLINK`/`VOICE_DOWNLINK` capture both sides and require the privileged `CAPTURE_AUDIO_OUTPUT`.
- Per-recording appops via `su`: `RECORD_AUDIO`, `PHONE_CALL_MICROPHONE`, `RECORD_AUDIO_OUTPUT` + `setprop persist.vendor.radio.enable_voicecall_recording true` (`services/ai/CallRecorder.java:309-316`).
- Android-12 branch already present (`services/CallRecorder.java:780` `isAndroid12Plus`).

**Injection** — `services/CallAudioInjector.java` + module `scripts/inject_stream.sh`:
- App writes ElevenLabs PCM to FIFO `/data/local/tmp/call_injector/audio_stream.pipe` (`CallAudioInjector.java:34`), runs `su -c inject_stream.sh stream` (`CallAudioInjector.java:31`).
- Module: `tinymix set "$MIXER_CONTROL" 1` then `cat pipe | tinyplay -r 16000 -c1 -b16 -d $DEVICE_ID` (`scripts/inject_stream.sh:68-77`).
- Injection is gated by: **root**, **SELinux** (FIFO relabel + `setenforce 0`), and **a recognized in-call mixer** — NOT by any Android permission.

### 3.3 What the Magisk module grants (`~/Downloads/BCR-1/`, git repo)
- **Privileged perms** via `system/etc/permissions/privapp-permissions-com.teletalker.app.xml` (tracked, 6 perms) — plus a conflicting untracked `privapp-permissions-com.chiller3.bcr.xml` (23 perms, same package).
- **Hidden-API / power exemptions** via `system/etc/sysconfig/config-com.teletalker.app.xml` (`hidden-api-whitelisted-app`, `allow-in-power-save`).
- **appops / pm grant / props / SELinux** in `service.sh` + `post-fs-data.sh` (incl. `setenforce 0`, `service call audio 7 i32 3`, SoC-specific `setprop`).
- **Mixer detection** `scripts/detect_mixers.sh` — Qualcomm `Incall_Music …`, Samsung/Exynos `CALL_REC Audio Mixer`, MediaTek `CALL_RECORD Audio Mixer`; hardcoded `MultiMediaN→device-id` (Qualcomm) map.
- Ships the app itself as priv-app: `system/priv-app/com.teletalker.app/app-release.apk`.

### 3.4 Shipped APK permission set (verified via aapt on the bundled APK)
Declares the modern set already: `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_PHONE_CALL`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `MANAGE_OWN_CALLS`, plus privileged `CAPTURE_AUDIO_OUTPUT`, `MODIFY_PHONE_STATE`, `CALL_PRIVILEGED`, `CONTROL_INCALL_EXPERIENCE`, `DUMP`. So the manifest is Android-14-aware; the gaps are in *service type wiring* and *runtime request logic*, not in declarations.

---

## 4. Gap analysis (prioritized)

### 4.1 CRITICAL — Android 14 foreground-service breakage (Axis 1, app code)
These bite on API 34 **even as a privileged app**, and will break the baseline flow the moment it runs on Android 14.

- **G1 — `CallDetector` FGS has no `foregroundServiceType`.** Calls `startForeground()` (`CallDetector.java:378,390,1256`) with no type declared → `MissingForegroundServiceTypeException` on targetSdk 34. Needs `microphone` (it records) and/or `phoneCall`.
- **G2 — `ServiceManager.startCallDetectorService` uses `startService()`** not `startForegroundService()` (`ServiceManager.java:38-44`) → `BackgroundServiceStartNotAllowedException` when started from background on API 26+/12+.
- **G3 — `phoneCall` FGS started before a call exists.** `ScheduledCallExecutor` (type `phoneCall`) calls `startForeground()` (`ScheduledCallExecutor.java:87`) from an alarm broadcast (`ScheduledCallAlarmReceiver.java:105`) *before* the call is placed and while not default dialer → `ForegroundServiceStartNotAllowedException` on API 34. `tools:ignore` only silences lint.

### 4.2 HIGH — runtime permission logic bugs (Axis 1, app code)
- **G4 — `READ_MEDIA_AUDIO` requested but not declared.** `PermissionManager.getAllRequiredPermissions` adds it for TIRAMISU+ (`PermissionManager.java:86-87`) but it is absent from the manifest → `areAllPermissionsGranted()` can never be true on API 33+ (`PermissionManager.java:162`).
- **G5 — `READ_EXTERNAL_STORAGE` lacks `maxSdkVersion="32"`** (`AndroidManifest.xml:52-54`).
- **G6 — Non-runtime perms in the runtime request bundle.** `BIND_INCALL_SERVICE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` are pushed into `ActivityCompat.requestPermissions` (`PermissionManager.java:66,70,83-84`) → always "denied," firing spurious `onPermissionsDenied` and permanently blocking the "all granted" gate.
- **G7 — POST_NOTIFICATIONS (API 33):** requested in the bundle, but confirm the recording/call FGS still surfaces its notification on 13+; without the grant the FGS runs but the notification is hidden (visibility for the user during a call).

### 4.3 HIGH — device-model portability (Axis 2, module)
- **G8 — `detect_mixers.sh` is Qualcomm/Samsung/MediaTek-name-bound with a hardcoded Qualcomm device-id map.** Devices without a matching in-call mixer → "injection not supported" → feature dead on that model. This is a *capability ceiling*, not a permission bug.
- **G9 — Injection relies on `setenforce 0`** (global SELinux permissive, `service.sh`). Works on baseline but is non-persistent, a security hole, and silently ineffective on hardened OEM policy. Should become a scoped SELinux rule (`magiskpolicy`/`sepolicy.rule`) allowing `untrusted_app` → the FIFO.
- **G10 — Two conflicting `privapp-permissions` XMLs for the same package**, and `addon.d/51-*.sh` backs up only the incomplete one. Not fatal on baseline; a portability + maintainability hazard.
- **G11 — Recording 2-way depends on a vendor HAL flag** (`persist.vendor.radio.enable_voicecall_recording`), which is fused off on some models regardless of permission → silent `VOICE_CALL`, undetected fallback to MIC (local only).

### 4.4 MEDIUM — lifecycle resilience (Axis 2, app code)
- **G12 — No battery-optimization / OEM autostart handling.** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declared but never used; no MIUI/ColorOS/FuntouchOS autostart deep-links → boot restart + scheduled calls killed on Xiaomi/Oppo/Vivo.
- **G13 — Duplicate manifest declarations** (`FOREGROUND_SERVICE_PHONE_CALL`, `USE_EXACT_ALARM`) — cosmetic.

### 4.5 Notes on privileged assumptions (context, not necessarily bugs)
- Recording's privileged grant and all appops/`setprop`/`RemoveHardRestrictions.kt` paths assume a privileged/root install and no-op on a normal install. Acceptable given the product ships as a Magisk priv-app; documented so no one "fixes" it into breaking.

---

## 5. Work plan (phased, baseline-preserving)

Each phase lists: intent, files, version/device gating, risk, and acceptance criteria. Phases 1–2 (Axis 1) and Phase 4 (Axis 2) are independent and can be parallelized.

### Phase 0 — Instrumentation & safety net (do first)
- **0.1 Device diagnostic script** (`scripts/diagnose.sh` in the module, or an in-app debug screen): report per device — `ro.build.version.sdk`, `ro.product.manufacturer/model`, `ro.control_privapp_permissions`, SELinux mode, whether each privileged perm is granted (`dumpsys package`), each appop state (`appops get`), the mixer-detection result, and a VOICE_CALL silence check. Output one line per link in the record/inject chains.
- **0.2 Capture baseline logs** on Samsung A12 (`logcat`, `injection.log`, `mixer_config.txt`, appops dump) as the golden reference to diff against after each change.
- **Acceptance:** diagnostic runs on baseline and prints all-green for the working chain.

### Phase 1 — Android 14 foreground services (Axis 1, fixes G1–G3)
- **1.1** Add `android:foregroundServiceType="microphone"` (and where appropriate `phoneCall`) to `CallDetector`'s `<service>`; call the typed `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MICROPHONE)` overload on API 29+/34. Gate with `Build.VERSION.SDK_INT` so ≤API 33 keeps the current call.
- **1.2** Fix `ServiceManager.startCallDetectorService` to use `startForegroundService()` on API 26+ (`ServiceManager.java:38-44`).
- **1.3** Reorder `ScheduledCallExecutor` so the `phoneCall`-typed `startForeground` happens only once there is an active/self-managed call, or acquire the default-dialer role for scheduled outbound; on API <34 keep current behavior.
- **Gating:** all changes wrapped in `SDK_INT` checks; API 31 path byte-for-byte unchanged.
- **Risk:** low-medium; FGS type must match the appop the OS checks at runtime.
- **Acceptance:** on an A14 device the recording FGS starts without exception and records; on A12 the diagnostic diff is unchanged.

### Phase 2 — Runtime permission logic (Axis 1, fixes G4–G7)
- **2.1** Declare `READ_MEDIA_AUDIO`; add `maxSdkVersion="32"` to `READ_EXTERNAL_STORAGE`.
- **2.2** Remove `BIND_INCALL_SERVICE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` from the `requestPermissions` bundle in `PermissionManager.getAllRequiredPermissions`; handle exact-alarm via the existing Settings-intent path only.
- **2.3** Verify POST_NOTIFICATIONS request path shows the FGS notification on API 33+.
- **2.4** De-duplicate manifest lines (G13).
- **Risk:** low.
- **Acceptance:** `areAllPermissionsGranted()` returns true on API 33/34 when the real dangerous perms are granted; no spurious `onPermissionsDenied`.

### Phase 3 — Android 15 decision gate (Axis 1)
- Decide scope (see §8 open questions). If in scope: bump `targetSdk 35`, audit FGS `phoneCall`/`microphone` API-35 constraints, edge-to-edge, and any tightened background-start rules. Otherwise document 14 as the ceiling and cap testing there.

### Phase 4 — Device-model portability (Axis 2, module; fixes G8–G11)
- **4.1 Consolidate privapp allowlist (G10):** one authoritative `privapp-permissions-com.teletalker.app.xml` containing every signature|privileged perm the APK requests (audit with aapt); delete the duplicate `...bcr.xml`; fix `addon.d/51-*.sh` to back up the correct file.
- **4.2 Extend `detect_mixers.sh` (G8):** broaden control-name patterns and the device-id map; when no mixer is found, write `DETECTION_SUCCESS=false` and have the app fall back to speaker-playback injection instead of silent failure. Log the full `tinymix controls` dump for unknown devices to grow the pattern list.
- **4.3 Scope SELinux (G9):** replace `setenforce 0` with a targeted `sepolicy.rule`/`magiskpolicy` allowing `untrusted_app` to write the FIFO; keep `setenforce 0` behind a per-device fallback flag only if the scoped rule proves insufficient. Verify baseline still injects.
- **4.4 Silent-recording detection (G11):** after starting `VOICE_CALL` capture, detect near-silence/one-sided audio and surface "two-way recording unsupported on this device (HAL)" rather than appearing to record; fall back to MIC explicitly.
- **Risk:** medium — must re-verify baseline injection after 4.3.
- **Acceptance:** baseline unchanged; on a second Snapdragon/Exynos device the diagnostic identifies exactly which link fails; MediaTek/no-mixer device degrades to speaker-playback without a hard failure.

### Phase 5 — Lifecycle resilience (Axis 2, app code; fixes G12)
- **5.1** Implement `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow (`isIgnoringBatteryOptimizations` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- **5.2** Add `Build.MANUFACTURER`-branched autostart deep-links for MIUI/ColorOS/FuntouchOS/One UI with in-app guidance; no-op on Pixel/AOSP.
- **Acceptance:** on a Xiaomi/Oppo device, scheduled calls survive a reboot after the user completes the guided setup.

---

## 6. Device test matrix

| Device class | Android | Recording | Injection | Priority | Notes |
|---|---|---|---|---|---|
| **Samsung (reference)** | 12 | ✅ baseline | ✅ baseline | P0 | must never regress |
| Samsung | 13 | verify | verify | P1 | FGS notif + POST_NOTIFICATIONS |
| Samsung / Pixel | 14 | Phase 1 required | verify | P1 | FGS type wall |
| Snapdragon (other OEM) | 12–14 | verify HAL flag | verify mixer name | P1 | second injection data point |
| Exynos (non-Samsung) | 13–14 | verify | mixer likely differs | P2 | extend detect_mixers |
| MediaTek | 12–14 | HAL flag varies | likely no mixer | P2 | expect speaker fallback |
| Xiaomi/Oppo/Vivo | 12–14 | verify | verify | P2 | autostart/battery (Phase 5) |
| Android 15 | 15 | decision gate | — | P3 | Phase 3 |

For each cell record: privapp enforce mode, SELinux mode, granted privileged perms, appop states, mixer detection result, VOICE_CALL two-way vs silent.

---

## 7. Risk register
- **R1 — regressing the A12 baseline.** Mitigation: every Axis-1 change `SDK_INT`-gated; Phase-0 golden logs diffed after each phase.
- **R2 — FGS type vs appop mismatch on A14** (declaring `microphone` but the OS wants `phoneCall`, or vice-versa). Mitigation: test both, may need both types.
- **R3 — scoped SELinux rule insufficient on some OEM** → keep `setenforce 0` as an explicit, logged per-device fallback.
- **R4 — injection unsupported on a target device (no mixer).** Not fixable via permissions; product decision whether speaker-playback fallback is acceptable there.

---

## 8. Open questions / decisions needed
1. **Android 15 in scope?** Sets whether Phase 3 runs and whether we bump `targetSdk 35`.
2. **Available test hardware** beyond Samsung A12 (which Android 13/14 devices, which SoCs)? Drives Phase 4 sequencing.
3. **Injection fallback policy:** on devices with no in-call mixer, is speaker-playback (both parties hear via speakerphone capture) an acceptable degraded mode, or is such a device simply "unsupported"?
4. **Which repo owns this work's commits** — app (`~/FlutterProject/TeletalkerApplication`) and module (`~/Downloads/BCR-1`) are separate git repos; module backend (`~/Ai-Projects`) is not versioned.

---

## 9. File reference index
**App (`~/FlutterProject/TeletalkerApplication`):**
- `app/build.gradle` — SDK levels.
- `app/src/main/AndroidManifest.xml` — permissions, service `foregroundServiceType`.
- `services/CallDetector.java` — recording InCallService FGS (G1).
- `services/ServiceManager.java` — FGS start (G2).
- `services/scheduler/ScheduledCallExecutor.java`, `services/scheduler/ScheduledCallAlarmReceiver.java` — phoneCall FGS (G3).
- `services/CallRecorder.java`, `services/ai/CallRecorder.java` — audio source, appops (G11).
- `services/CallAudioInjector.java` — injection FIFO/su bridge.
- `PermissionManager.java`, `services/scheduler/CallPermissionManager.java`, `ScheduledCallPermissionsHelper.java` — runtime perms (G4–G7).
- `standalone/RemoveHardRestrictions.kt` — root/system reflection (context).

**Magisk module (`~/Downloads/BCR-1`):**
- `system/etc/permissions/privapp-permissions-com.teletalker.app.xml` (+ untracked `...chiller3.bcr.xml`) — G10.
- `system/etc/sysconfig/config-com.teletalker.app.xml` — hidden-api / power exemptions.
- `service.sh`, `post-fs-data.sh` — appops, props, `setenforce 0`, `service call audio` (G9).
- `scripts/detect_mixers.sh` — mixer detection (G8).
- `scripts/inject_stream.sh` — mixer set + tinyplay injection.
- `system/addon.d/51-com.teletalker.app.sh` — OTA backup (G10).
</content>
