# Format + anti-aliasing + defect-detection notes (research)

Sourced 2026-11-07 from open references (catibo, UVTools, msla_format/mslicer) — clean-room specs,
not code. Deepens `docs/research/resin-formats.md` and `feature-methods.md` for D.5 (AA), D.8 (CTB),
K (detection).

## CTB / CBDDLP layer encoding (catibo `cbddlp-ctb.adoc`)
- Layer pixels are **8-bit grayscale**: `0x00` = fully off, `0xFF` = fully on.
- **With anti-aliasing**, each voxel spans **0 (empty) … 127 (full)**; **without AA** only `0` and `127`.
- **RLE** — one run per byte: **bit 7 = pixel value**, **bits 6:0 = run length**.
  - `0x81` = a single `1` pixel; `0x7F` = a single `127` pixel.
  - `0xFF 0x2a` = a run of 42 × `127`.
  - Long runs extend: `0xFF 0xEF 0xFF 0xFF 0xFF` = 268,435,455 × `127` (multi-byte length).
- **`ctb` encryption** (obfuscation only): per-layer data is encrypted with a random key stored in the
  header (`encryption_key`); the **layer index is the IV** (bottom layer IV=0, next IV=1, …).
  **Encryption is disabled when `encryption_key == 0`** (what catibo/UVTools write).
- CTB v4 conversion reference: `oelmekki/sl1toctb` (public domain; RLE + crypto ported from UVTools).
- UVTools v7 note: CTB layer encryption is processed 4 bytes at a time; encoders write layers in
  **batches** (not all in memory) — relevant to our memory budget on-device.

## Anti-aliasing (D.5)
- Method: supersample the layer mask (**3D SSAA**) or distance-field **edge weighting**, quantized to
  the 8-bit `0…127` grayscale the writer emits. `mslicer`/`msla_format` are the behavior reference.
- The engine path (PrusaSlicer `SLAPrint`) already produces grayscale masks; our writer must preserve
  the 0…127 range (not threshold to 0/127) when AA is on.

## Suction cups & resin traps (K — UVTools-class)
- **Resin trap**: a region that prints hollow and traps **liquid resin** (gives off gas → splits).
  Detection: scan each layer for enclosed **empty** areas not reachable to the outside (flood-fill
  from the border). Fix: **solidify** (fill with white pixels) or add a drain hole.
- **Suction cup**: air trapped in a hollow area that creates **suction force on the FEP** (can warp /
  detach). Computed **during** the resin-trap pass. **Not auto-fixed** — needs a **vent hole** (UVTools
  drills vertically, Z-axis only).
- **Thresholds matter**: single/two-layer "traps" are usually **false positives** (tiny support areas
  that get cured anyway); only **large / many-layer** traps are real. Make depth configurable.
- **AA interaction**: with anti-aliasing, gray border pixels can be mis-read as traps — treat gray as
  boundary, not solid, when flood-filling.
- Other issue classes to mirror: **islands** (unsupported), **overhangs**, **touching bounds**, **print
  height**, **empty layers** (remove). Colors/severity are UI convention.

## Takeaway for us
- **SL1** we already emit (engine). **CTB v3+/v4** is the next writer: RLE(bit7=value,6:0=len) +
  optional XOR (key=0 to disable) + RGB565 previews + SHA-256. **GOO** = chunked RLE + inverted checksum.
- **AA**: keep the grayscale range; **Detection (K)**: flood-fill empties per layer, classify trap vs
  suction cup, configurable depth threshold, vent-hole (Z-only) vs solidify fixes.
