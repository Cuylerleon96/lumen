# Licensing — AGPL/GPL implications

## The hard constraint
The engine we want to reuse is **AGPL-3.0**:
- **OrcaSlicer** — AGPL-3.0.
- **Bambu Studio** — AGPL-3.0.
- **PrusaSlicer** — AGPL-3.0.
- **Slic3r** — AGPL-3.0.

AGPL §13: if you run a modified version **over a network**, users must be offered
the source. For a **local mobile app** the practical effect is: **the whole app
(including our UI) must be released under AGPL-3.0**, and we must ship/offer the
full corresponding source. There is no way to keep the UI closed if it links the
engine. That's fine — this project is open source — but it is non-negotiable, and
it means:

- **License everything AGPL-3.0-or-later** (our code + any engine patches).
- Keep a **`LICENSE` + per-file headers + a NOTICE** crediting Slic3r →
  PrusaSlicer → Bambu Studio → OrcaSlicer, per the lineage they all document.
- Do **not** statically link any **non-free** component. ⚠️ OrcaSlicer's bundled
  **Bambu networking plugin** is based on non-free BambuLab libraries and is
  optional — **we exclude it** (keeps the app FDM+resin generic and portable).

## Component licenses to respect
| Component | License | Compatibility with AGPL-3.0 app |
|---|---|---|
| libslic3r (Orca/Prusa) | AGPL-3.0 | ✅ same license |
| **UVTools** | *verify* (GPL-3.0 family) | ✅ GPL is one-way compatible into AGPL only if we don't mix GPL into AGPL source we relicense... **treat as reference, not as linked code.** |
| **mslicer** | **GPL-3.0** | ⚠️ GPL-3.0 code **cannot** be linked into an AGPL project without making combined work GPL-3.0-or-AGPL (both copyleft; practically fine to *relicense our copies* as AGPL if author permits, but **safest: clean-room reimplement formats from docs**). |
| **Encrust** | AGPL-3.0 | ✅ compatible |
| **catibo** | (check) | reverse-eng docs; use as documentation. |
| **OpenVDB** | MPL-2.0 | ✅ file-level copyleft; okay to link. Needed for SLA hollowing. |
| Boost (BSL), TBB (Apache-2.0), CGAL (GPL-3 / LGPL), OCCT (LGPL) | permissive-ish | ✅ (watch CGAL: GPL vs LGPL build option). |

**Rule of thumb:** base the code on **AGPL** engines (Orca/Prusa/Encrust), and
**reimplement format writers from the published documentation/spec** (UVTools
behaviour, catibo doc, grid.space tables) rather than copying GPL-3.0 Rust code
(mslicer). Reusing mslicer as a *reference* is fine; porting its code in is a
licensing decision to make explicitly.

## Action items
- [ ] Add `LICENSE` (AGPL-3.0) + `NOTICE` crediting the lineage.
- [ ] Confirm exact UVTools license text before copying any of its code.
- [ ] Decide: clean-room the format writers (recommended) vs. vendor GPL-3.0 code.
- [ ] AGPL "source offer" is trivial for a local app — just keep the repo public.
