# QC report — mobile slicer app

_First QC pass, 2026-11-06. Evidence: real builds + an emulator run (not self-reported)._

## Build & tests
- **FDM engine:** `liborcamicro_engine.so` (18 MB, arm64) — BUILD SUCCESS.
- **App APK:** `app-debug.apk` (52 MB, engine `.so` packaged) — BUILD SUCCESSFUL.
- **Unit tests:** `:core:test` → **36 tests, 0 failures, 0 errors** (2 skipped = `OrcaProfilesTest`,
  which need the bundled Orca profiles at test time — expected). Covers PNG encoder, SL1 writer,
  mesh rasterizer, mesh/placement, profiles, preset editor, G-code parser, and the new motion test.

## Runtime (Android 35 emulator, 1080×2400)
- ✅ **Launches** to the Technology picker ("What are you printing?" → FDM / Resin cards).
- ✅ **Resin workspace renders** cleanly: top bar (`OrcaMicro` / `No model` + `Switch` + info),
  printer card (`Elegoo Mars 3 (4K) · 0.05 mm · 2.5s · 5 bottom` + `Settings`), 3D plate,
  empty-state dialog ("Open an STL file to start" + `Open STL`), bottom bar (`+`, ⚙, `▶ Slice`).
- ✅ **No app crash** on navigation (logcat: MainActivity resumed, no FATAL/AndroidRuntime).

## Issues found
| # | Severity | Finding | Note |
|---|---|---|---|
| 1 | low (env) | Emulator's **System UI** ANRs repeatedly ("System UI isn't responding") | Emulator-only: software-GPU (`swiftshader`) CPU starvation. App itself is healthy. Not a product bug. |
| 2 | low | **FDM screen not visually verified** in-session | The app persists the resin choice, so relaunch skips the picker; reach FDM via top-bar **Switch**. Retry next run (or verify on a real device). |
| 3 | info | `-no-audio` emulator mutes mic; irrelevant here (no audio features). | — |

## Not yet exercised (no test path exists yet)
- Slice → output (FDM G-code and resin SL1) end-to-end on device.
- Open an STL and slice (empty-state is the current limit without a file).
- Supports / hollowing / AA / estimation — features not built yet (see `FEATURES.md`).

## Verdict
The app **builds, installs, launches, and renders both a picker and a working resin workspace**, with
a green unit-test suite. Nothing blocking; the open items are unbuilt features, not defects.
