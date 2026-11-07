# Architecture (proposed)

## Goal
One offline mobile app that slices **FDM → G-code** and **resin → SL1/CTB/GOO**,
on-device, AGPL, no cloud. Reuse mature engines; build only the glue + formats + UI.

## Layer cake
```
┌──────────────────────────────────────────────────────────────┐
│  UI  — Android (Kotlin + Jetpack Compose) / iOS (SwiftUI)     │
│        plate view, model transform, layer preview, settings   │
├──────────────────────────────────────────────────────────────┤
│  JNI / FFI bridge  (slic3r::mobile API)                        │
│        config assemble → run → stream layers → export         │
├──────────────────────────────────────────────────────────────┤
│  ENGINE  = one libslic3r fork carrying BOTH:                   │
│    • OrcaSlicer FDM (walls, tree supports, profiles…)          │
│    • PrusaSlicer SLA  (SLAPrint, Voronoi supports, hollowing)  │
│    shared types: Model, ExPolygons, TriangleMesh, slicer cfg   │
├──────────────────────────────────────────────────────────────┤
│  FORMAT WRITERS  (our code, from published specs):             │
│    SL1 → CTB → GOO → (PW*/Photon/…)                            │
│    + RLE1/RLE4/CTB-variable-RLE/RLE128, XOR/AES, handles       │
├──────────────────────────────────────────────────────────────┤
│  DEPS (cross-compiled arm64): Boost, TBB, CGAL, OCCT,          │
│    OpenVDB (SLA hollowing), Eigen, Clipper2, libpng, zlib      │
└──────────────────────────────────────────────────────────────┘
```

## Engine decision (see adr/0001)
Two viable shapes:
- **(A) Single fork:** start from **PrusaSlicer's `libslic3r`** (has FFF + SLA
  natively), and merge in Orca's FDM features/profiles. One build. ✅ simplest,
  guaranteed resin.
- **(B) Two engines:** Orca `libslic3r` for FDM (best profiles) + PrusaSlicer SLA
  for resin, behind one `Slicer` interface. Best-of-both FDM, but heavier build.

**Recommendation:** start **(A)** to de-risk resin end-to-end fast (proves the
hard, novel part), then optionally pull Orca FDM richness into the fork. Both are
AGPL and share types, so (A)→(B) is not a rewrite.

## Mobile build recipe (proven by prior ports)
1. Cross-compile `libslic3r` **engine-only** for `arm64-v8a` with the **Android
   NDK (pin a version — u1-slicer needed NDK 26/clang 17 for correct output).**
   Add a `-DSLICER_ENGINE_ONLY=ON` CMake option (OrcaMicro's proposed upstream
   patch) that skips GUI/CLI.
2. Provide the JNI bridge: assemble config JSON the way the CLI does → call
   `Print::process()` → `export_*()`. Stream progress + layer previews to JVM.
3. Ship a **prebuilt `.so` in `jniLibs/`** so normal app builds don't need the NDK.
4. **Keep OpenVDB in** (unlike OrcaMicro) — required for SLA hollowing.
5. iOS: same engine, CMake toolchain for `arm64-apple-ios`; SwiftUI front-end.

## Data flow (resin path)
```
STL/3MF/OBJ → Mesh
  → orient + (hollow: OpenVDB boolean) 
  → supports (sla::SupportTree, Voronoi, overhang/island driven)
  → build slice grid (layer heights)
  → mesh_slice → ExPolygons per layer
  → raster each layer to grayscale bitmap  (+ SSAA anti-aliasing)
  → pack: SL1(zip+png) | CTB(RLE+encrypt) | GOO(chunked RLE)
  → preview + metadata (time, resin volume, cost)
```

## Compute & performance
- Rasterization is cheap; **mesh slicing + supports + hollowing are the cost**.
- Target: mirror mslicer's speed mindset (scanline slicing, GPU RLE where useful).
- Phone limits: RAM for high-poly meshes; cap triangle counts / stream layers.
- Background slicing via a **foreground service** (Android) like existing ports.

## Threading / cancellation
- Engine is multi-threaded (TBB). Bridge must pass a **cancel** token and stream
  progress (both prior ports do this).

## What we deliberately reuse (vs build)
| Build (our code) | Reuse |
|---|---|
| Format writers, JNI/FFI glue, UI | Orca FDM slicing, PrusaSlicer SLA slicing + supports + hollowing, profile JSON, mesh ops |
| Print-failure analysis (islands/suction) | concepts from Encrust/UVTools |
| Profiles | Orca/Prusa bundled printer+resin profiles (parse their JSON/INI) |
