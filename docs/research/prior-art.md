# Prior art — slicers & mobile ports (what to reuse)

## The Slic3r family tree (all C++, all AGPL-3.0)
```
Slic3r (Ranellucci, 2011)
  └─ PrusaSlicer (Prusa Research)      ← FDM **and** SLA/resin
        ├─ Bambu Studio (Bambu Lab)     ← dropped SLA
        │     └─ OrcaSlicer (SoftFever) ← FDM only, NO SLA
        └─ SuperSlicer (supermerill)    ← fork of PrusaSlicer
```
Same settings model, same `libslic3r`, same 3MF project format. OrcaSlicer is the
most advanced **FDM** engine; PrusaSlicer is the only one carrying **SLA/resin**.

### Key fact: OrcaSlicer has NO resin, and won't
- Orca is FDM-only. Resin/SLA requests were **rejected/parked** — issues
  **#12003** (Jan 2026) and **#13132** (Apr 2026). No roadmap.
- So "use what Orca figured out" = its **FDM engine + profiles + AI/plugin
  architecture** — *not* a resin path. Resin must be sourced separately.

## Mobile FDM slicers (all FDM-only — this is the gap)
| Project | Engine | Stack | Notes |
|---|---|---|---|
| **OrcaMicro** (`whoxllm/OrcaMicro`) | Orca libslic3r v2.4.2 | NDK r27c, JNI | Proposed `-DORCA_ENGINE_ONLY=ON`; OpenSSL→MD5 shim; **drops OpenVDB → SLA hollowing = no-op**. ~950 profiles. |
| **SliceBeam** (`utkabobr/SliceBeam`) | PrusaSlicer core | Kotlin | Most mature; Play Store; has a web build; AGPL. |
| **u1-slicer-for-android** (`taylormadearmy`) | Snapmaker Orca 2.2.4 | Kotlin+Compose+JNI | **NDK 26 (clang 17)** matters; prebuilt `.so` in `jniLibs`. Good architecture reference. |
| **OrcaSlicer Mobile** | Orca (ported onto SliceBeam) | Kotlin | Multi-color paint. |
| **DuckySlicer** (`ashcastle`) | Orca | C++/Qt/NDK | Early solo project. |
| **Slicer Mobile** | PrusaSlicer 2.9.6 | Swift/Kotlin | iPad/iPhone/Android, beta. |

**Takeaway:** the Android+libslic3r path is *proven and well-documented*. Copy the
build recipe (engine-only libslic3r, NDK pinning, JNI bridge, shipped `.so`).

## Open-source RESIN slicers + libraries
| Project | Lang | License | What to take |
|---|---|---|---|
| **PrusaSlicer SLA** | C++ | AGPL-3.0 | **The resin engine.** `SLAPrint`, Voronoi tree supports, hollowing+drain. 2.9.5 refactored to analytic supports + on-demand raster (big memory win). |
| **UVTools** (`sn4k3/UVtools`) | C#/.NET | (check — see licenses.md) | **The format bible.** Reads/writes every MSLA format; SL1→Anycubic etc. Reference for writers. |
| **mslicer** (`connorslade`) | Rust | GPL-3.0 | Fast, modern, hackable. Outputs CTB/GOO/NANODLP/SVG. 3D SSAA AA. Format crate is a clean Rust reference. |
| **Encrust** (`vvvlladimir`) | Rust | AGPL-3.0 | New (Oct 2026). Supports + hollowing + **print-failure analysis** (islands, suction cups, thin sections) + 20 formats + browser build. |
| **catibo** (`cbiffle`) | C | — | Semi-authoritative **CTB/CBDDLP** reverse-engineering docs (`doc/cbddlp-ctb.adoc`). |
| **uv3dp** (`ezrec`) | Rust | — | pw0/pws reader/writer. |
| **sl1toctb / SL1toPhoton** | Python/C | — | SL1→CTB/Photon converters (format reference). |

## The opportunity
No mobile **resin** slicer exists. Resin slicing is *simpler* than FDM at the core
(raster an image per layer — PrusaSlicer devs say so) but the **hard parts are
supports, hollowing, and the proprietary output formats**. Reusing PrusaSlicer's
SLA module + UVTools/mslicer format logic collapses most of that risk.
