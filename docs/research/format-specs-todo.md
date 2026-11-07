# Remaining output formats — spec map & how to pull a spec

Item 3 of the tracker. `PhotonS` is done (`PhotonSWriter`). The rest, and **where the spec actually
lives** (the names in the original handoff were guesses and two were wrong):

| Format | Printers | Spec file in UVTools | Status |
|---|---|---|---|
| **PhotonS** `.photon` | Anycubic Photon S | `AnycubicPhotonSFile.cs` | ✅ done |
| **PW0 / PWS** | Anycubic Photon Workshop | `AnycubicFile.cs` (2 624 lines, `AnycubicZipFile.cs` for the container) | 📋 todo |
| **CBDDLP** | Creality LD-002R/H, early Chitubox machines | **`ChituboxFile.cs`** — CBDDLP is the *older Chitubox* container, not a Creality format. Uses the **bit-plane RLE** (now implemented in `:core`); the container is what remains | 🚧 codec ✅, container todo |
| **CXDLP** (Creality v1/v2/v4) | Creality Halot, LD-006, … | `CrealityCXDLPFile.cs`, `CrealityCXDLPv1File.cs`, `CrealityCXDLPv4File.cs` | 📋 todo |
| `.mdlp` | MakerJuice/Massive Dimension | `MDLPFile.cs` | 💤 backlog |
| `.zcode` / `.zcodex` | UniFormation / Kelant | `ZCodeFile.cs`, `ZCodexFile.cs` | 💤 backlog |
| `.lgs` | Longer | `LGSFile.cs` | 💤 backlog |
| `.phz` | Zongheng | `PHZFile.cs` | 💤 backlog |

## How to pull a spec (the workflow that worked)

Clone UVTools shallowly and read the spec out of it:

```bash
git clone --depth 1 https://github.com/sn4k3/UVTools.git /tmp/uvtools
F=/tmp/uvtools/UVtools.Core/FileFormats/<File>.cs
grep -nE "FieldOrder|FieldEndianness|FieldEncoding|FieldLength|public (uint|ushort|byte|float|double|string|bool)" $F | head -60
```

The `[FieldOrder(n)]` attributes **are** the byte layout, in order; `[FieldEndianness(...)]` and
`[FieldEncoding("UTF-16BE")]` tell you the representation. Read the `Encode`/`Decode` pair for the
per-layer compression, and `EncodeInternally` for the exact order the pieces are written.

## CBDDLP / Chitubox digest (pulled; **codec implemented**, container still todo)

**Correction to an earlier note in this file:** CBDDLP does **not** share the CTB writer's codec.
`CtbWriter` implements **RLE7** (one byte per pixel value, bit 7 = run flag, length in the low bits);
CBDDLP uses the **bit-plane RLE** (`BitPlaneRle`, `RLE8EncodingLimit = 0x7d = 125`). Two different
codecs in the same family of files — reaching for the wrong one yields a structurally valid file that
prints a wrong image. Same family, same magic neighbourhood, different compression.

- ✅ **`BitPlaneRle` is implemented and tested** in `:core` (`BitPlaneRle.kt`, `BitPlaneRleTest`):
  `encodePlane` / `decodePlane` / `countsToGray` / `encodeLayer`. Per byte: bit 7 = run is exposed,
  bits 6..0 = the run length stored **directly** (1..125, split above that). Contrast PhotonS, which
  stores `reverse8(len-1)` in the same bits — both round-trip against their own decoder, so only a
  hand-computed byte assertion distinguishes them. Decoding counts, per pixel, how many planes set it;
  `countsToGray` maps that count with `level = count * (256/aa) - 1` (so AA-off means any count ≥ 1 is
  255). A layer with AA off is a single plane (bit 0); with AA the table carries
  `layerCount * antiAliasLevel` records, one plane per level.
- **Remaining work is the container**, not the compression:
  magic distinguishes the variants — **CBDDLP `0x12FD0019`**, CTB `0x12FD0086`, CTBv4 `0x12FD0106`,
  CTBv4_GKtwo `0xFF220810`. Little-endian.
- Header, in `[FieldOrder]` order: u32 Magic · u32 Version · f32 BedSizeX/Y/Z · u32 Unknown1 ·
  u32 Unknown2 · f32 TotalHeightMillimeter · f32 LayerHeightMillimeter · f32 LayerExposureSeconds ·
  f32 BottomExposureSeconds · f32 LightOffDelay · u32 BottomLayersCount · u32 ResolutionX ·
  u32 ResolutionY · u32 **PreviewLargeOffsetAddress** · u32 **LayersDefinitionOffsetAddress** ·
  u32 LayerCount · u32 **PreviewSmallOffsetAddress** · u32 PrintTime · u32 ProjectorType ·
  u32 PrintParametersOffsetAddress · u32 PrintParametersSize · u32 AntiAliasLevel · u16 LightPWM ·
  u16 BottomLightPWM · u32 **EncryptionKey** · u32 SlicerOffset · u32 SlicerSize.
- The offsets are absolute file positions, so the writer must lay the file out and backfill them.
  Write order: header → previews (large then small, each an image header + RGB565 RLE15 payload) →
  print parameters → **layer-definition table** (`layerDefSize * layerCount * antiAliasLevel`) → layer
  data blobs. `SlicerOffset = 0` for CBDDLP (no slicer-info block).
- LayerDef (36 bytes, little-endian): f32 PositionZ · f32 ExposureTime · f32 LightOffSeconds ·
  u32 DataAddress · u32 DataSize · u32 **PageNumber** · u32 TableSize · u32 Unknown3 · u32 Unknown4.
  `DataAddress`/`PageNumber` are a **paged** offset pair:
  `pageNumber = offset / PageSize; dataAddress = offset - PageSize * pageNumber`.
- Print parameters block: bottom/lift/retract heights and speeds (+ the `*2` variants), rest times,
  machine-name address+size, AA flag, per-layer settings, timestamps, software version
  (0x1060300 = CTB v3, 0x1090000 = v4, 0x2000000 = v5), transition layer count, v4 params address.
- **`EncryptionKey > 0` XOR-encrypts the layer data** — the same trap as CTB: plaintext layers with a
  non-zero key produce a file the printer rejects. Write 0.

## Creality CXDLP digest (pulled, not yet implemented)

- Magic: `"CXSW3DV2"` (9 bytes) at the start **and** as a footer; `MagicSize` u32 = 9 first.
- Header: u32 magicSize · 9 B magic · u16 version · u16 **LayerCount** (yes, a `ushort` — 65 535
  layer cap) · u16 ResolutionX · u16 ResolutionY · 64 B `Offset` (reserved).
- Then a settings block whose **strings are UTF-16BE with an explicit length prefix**:
  `uint len` + `len` bytes of `DisplayWidth`, same for `DisplayHeight`, `LayerHeight`
  (`len` = 8, value formatted `InvariantCulture`), then a run of `ushort`s:
  ExposureTime · WaitTimeBeforeCure (**min 1 or it will not print**) · BottomExposureTime ·
  BottomLayersCount · BottomLiftHeight · BottomLiftSpeed · LiftHeight · LiftSpeed · RetractSpeed ·
  BottomLightPWM (255) · LightPWM (255).
- Per-layer: a `LayerArea` u32 followed by that layer's compressed data.
- A newer variant adds material name (big-endian length-prefixed string) and a compensation block
  (distortion compensation, XY axis profile, Z penetration …).
- Endianness is **little-endian** for the header integers — the opposite of PhotonS. Check
  `[FieldEndianness]` per field rather than assuming per file.

## Rules that carried over from PhotonS

- Hand-compute the expected bytes for one run of each length class (1, <limit, exactly limit,
  >limit) and assert the literal array. **A round-trip test cannot catch a mistake the encoder and
  decoder share** — that is how the 7-bit-vs-8-bit reverse bug survived a passing round-trip.
- Size the header buffer from the field list and name the constant; a short buffer only shows up as
  a runtime `BufferOverflowException`.
- Stream layers one at a time (`layerAt: (Int) -> LayerImage`), never hold a full-res print.
