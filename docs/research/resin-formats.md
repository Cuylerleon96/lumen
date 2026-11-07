# Resin (MSLA) slicing — formats & encodings (deep dive)

## How resin slicing differs from FDM
A masked-SLA slicer is **simpler at the core**: instead of planning a toolpath, it
**rasterizes one image per layer** and tells the printer how long to expose it.
Complexity lives in: **supports**, **hollowing/drain**, **anti-aliasing**, and the
**proprietary output formats**.

Pipeline: `mesh → orient → hollow (optional) → supports → slice-height grid →
per-layer 2D contour → raster to grayscale bitmap → anti-alias → pack into a
proprietary container (+ print/lift/exposure settings)`.

## The open reference container: SL1 / SL1S
Prusa's format — **just a ZIP**:
```
config.ini          # expTime, expTimeFirst, layerHeight, numFade/Fast/Slow, printTime, usedMaterial, ...
prusaslicer.ini     # full printer/slicer settings
prusaslicer.json
config.json         # tilt options
*.png               # ONE grayscale PNG PER LAYER (enumerated)
thumbnail/*.png
```
Example `config.ini`:
```
action = print
expTime = 8.5      ; normal layer exposure (s)
expTimeFirst = 35  ; bottom/first layers (s)
layerHeight = 0.05
numFade = 10
numFast = 300
# + printTime, usedMaterial, printerModel, ...
```
PrusaSlicer writes SL1 via zlib + `tdefl_write_image_to_png_*` (see
`src/.../ResultExport/SLA/SL1.cpp`). **Start here** — fully documented, trivial to
verify by unzipping.

## Proprietary output formats (the real work)
| Format | Vendor/board | Notes |
|---|---|---|
| `.ctb` | Chitu (Anycubic, Creality, Phrozen, Elegoo…) | **v5**, AES-256-CBC + XOR (`DEFAULT_XOR_KEY = 0x67`), SHA-256, RGB565 previews. Magic `0x12FD0107`. |
| `.cbddlp` | Chitu (older Mars) | Predecessor of CTB. Magic `0x12FD0019`. |
| `.photon` / `.photons` | Anycubic | |
| `.pw0/.pws/.pwma/.pwms/.pwmx/.pw5/.pm5` | Anycubic Photon Workshop | Several RLE4 variants. |
| `.goo` | Elegoo | Chunked RLE + inverted checksum, `0x55` stream. |
| `.nanodlp` | NanoDLP | |
| `.cxdlp` | Creality (gcode-ish) | |
| `.zcode` | Longer | |
| `.lgs` | Creality | |
| `.sl1/.sl1s` | Prusa | **open, start here** |

## Layer encodings (the bit-level details)
Freshly re-derived / mirrored from UVTools + catibo + grid.space + mslicer:

- **RLE1 / RLE125** (PWS/CBDDLP): 1 bit/pixel. Each byte = `[value:1][runLen:7]`,
  max run 125 encoded / 127 stored (`0x81`=1 px on, `0x8F`=15 px on).
- **RLE4** (PW0): nibble-coded, 1–2 B/run; max repeat 4095 (B/W), 15 (gray).
- **CTB variable RLE** (v3+): 7-bit grayscale. First byte = `[isRun:1][level:7]`.
  Run length is **variable-length, big-endian**: `0xxxxxxx`=7b, `10xxxxxx`=14b,
  `110xxxxx`=21b, `1110xxxx`=28b (max 268,435,455). `0x7F`=single px 127;
  `0xFF 0x2A`=run of 42×127. Then the whole layer data is **XOR-encrypted** with
  the file's `encryption_key` (IV = layer index; key=0 disables).
- **RLE128** (PhotonS): 2 B fixed, **big-endian** length, 8-bit color. `0x00`..`0xFF`.
- **GOO RLE**: 0x55 magic, chunked (`type:2 | lenenc:2 | lenlow:4`), single fresh
  checksum byte (bitwise-NOT of byte sum), layer delimiter `0x0D 0x0A`.
- **GOO header** (confirmed from open UVTools `GooFile.cs`; whole file is **big-endian**):
  Version 4 B `"V3.0"` · Magic 8 B `07 00 00 00 44 4C 50 00` · SoftwareName 32 B ·
  SoftwareVersion 24 B · FileCreateTime 24 B (`yyyy-MM-dd HH:mm:ss`) · MachineName 32 B ·
  MachineType 32 B (`"DLP"`) · ProfileName 32 B (all NUL-terminated) · AntiAliasingLevel u16 (8) ·
  GreyLevel u16 (1) · BlurLevel u16 (0) · SmallPreview565 116×116×2 B + `0D 0A` ·
  BigPreview565 290×290×2 B + `0D 0A` · then print params + layer defs + per-layer RLE (layer magic
  `0x55`). *(Layer-table offsets still to be pulled from the same file before a writer is coded.)*
- **CTBEncrypted**: AES-256-CBC (hardcoded key+IV to the format spec) + the XOR layer.

Grayscale AA: CTB stores **128 levels** (0x00 masked → 0x7F full); anti-aliasing =
intermediate values = partial exposure. mslicer uses **3D supersampling (SSAA)**.

## Difficulty tiers (from grid.space) — implement in this order
1. **Tier 1 (start):** `ImageFile`, **`UVJ`** (JSON+PNG), **`SL1`** (INI+PNG).
2. **Tier 2:** `CBDDLP` v1 (simplest binary), **`CTB` v3** (most common), `PWS`.
3. **Tier 3:** `PW0` (RLE4), `PhotonS` (BE RLE128), `GOO` (chunked).
4. **Tier 4:** `CTBEncrypted` (AES), `AnycubicFile` v517+, `CXDLP`.

## Reference sources (read these, don't reinvent)
- **UVTools** `UVtools.Core/FileFormats/ChituboxFile.cs` — magic numbers, layer
  table (`LayerDef`, 36 B: PositionZ, ExposureTime, LightOffSeconds, DataAddress,
  DataSize, PageNumber...), per-layer setting flags.
- **catibo** `doc/cbddlp-ctb.adoc` — the clearest CTB/CBDDLP writeup.
- **grid.space** `docs/refs/MSLA_FORMAT_REFERENCE` — comparison table + worked RLE
  examples.
- **mslicer** `format/` crate (Rust) — modern, clean reader/writer.
- **PrusaSlicer SLA profiles** — `resources/profiles/PrusaResearchSLA.ini`,
  `Anycubic.ini` (support params: `support_head_front_diameter`,
  `support_critical_angle`, `support_density_at_45`, `support_object_elevation`...).

## Resin settings that MUST be in the output
Per-layer: **position Z, exposure time, light-off delay**. Global: **bottom-layer
count + bottom exposure + bottom light-off**, transition (fade) layer count, layer
height, resolution (X/Y px), **pixel size (µm)** → build area, lift/retract
height+speed (2-stage), rest times, anti-alias levels, brand/resin metadata.
Bottom-layer over-exposure + "elephant foot" compensation and Z-correction are the
correctness details that separate a good slicer from a bad one.
