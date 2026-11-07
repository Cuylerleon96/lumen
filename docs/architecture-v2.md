# Target architecture v2 — one app, pluggable engines

## Layers
```
┌─ :app  (Android) ───────────────────────────────────────────────────────────┐
│  one UI: PlateScreen · SettingsSheet · PreviewScreen · Import/Export        │
│  ViewModel (unified) · EngineService (hosts backends) · GL renderer         │
└───────────────▲─────────────────────────────────────────────────────────────┘
                │ SliceJob  →  SliceOutcome   (technology-agnostic)
┌─ :core  (pure Kotlin domain) ───────────────────────────────────────────────┐
│  Project / Plate / Placement / Mesh      PrintSettings (sections)           │
│  SlicingBackend (interface)  ── registry                                    │
│     ├─ FdmBackend   → Orca libslic3r  → G-code + Toolpaths                  │
│     ├─ ResinBackend → Prusa SLA       → per-layer images (SL1/CTB/GOO)      │
│     └─ <experimental backends>                                              │
│  profiles · gcode · resin writers · estimation · prep (mesh ops)            │
└─────────────────────────────────────────────────────────────────────────────┘
                │ JSON request / JNI
┌─ native (arm64 .so) ────────────────────────────────────────────────────────┐
│  liborcamicro_engine.so (FDM)   libsla_engine.so (resin, ADR 0003-B)        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key types (in `:core`)
- `SlicingBackend` — `technology`, `slice(job, onProgress): SliceOutcome`, `cancel()`.
- `SliceJob` — `technology`, `placed: Mesh`, `settings: PrintSettings`, `outputName`.
- `SliceOutcome` — `Gcode(path, toolpaths, warnings)` **or** `Layers(archive, layerCount, warnings)`.
- `PrintSettings` — one object; technology sections filled per backend. No `resin*`/`fdm*` split in UI state.

## UI (one, not two)
- **PlateScreen** — import, place/transform, orient, supports, hollow, slice. Works for any backend.
- **SettingsSheet** — renders the sections the active backend declares (backend publishes its schema).
- **PreviewScreen** — renders G-code toolpaths *or* layer images from the same `SliceOutcome`.

## Feature build order (unified, backend-agnostic)
1. Land the domain + `SlicingBackend` + registry; port the FDM and resin paths onto it (parity).
2. Everyday-slicer baseline: orient, supports, hollowing, estimation, layer preview (shared).
3. Formats: SL1 → CTB → GOO.
4. Experimental slicing: new backends plug in here.

## Migration strategy (no big-bang)
- Add `:core` domain + `SlicingBackend` **additively** (old code keeps compiling).
- Port resin first (smallest, self-contained), then FDM.
- Fold `Screen.RESIN` into the unified `PlateScreen`; delete the parallel `resin*` state.
- Rename namespace/branding last (mechanical).
