# Remaining output formats — spec map & how to pull a spec

Item 3 of the tracker. `PhotonS` is done (`PhotonSWriter`). The rest, and **where the spec actually
lives** (the names in the original handoff were guesses and two were wrong):

| Format | Printers | Spec file in UVTools | Status |
|---|---|---|---|
| **PhotonS** `.photon` | Anycubic Photon S | `AnycubicPhotonSFile.cs` | ✅ done |
| **PW0 / PWS** | Anycubic Photon Workshop | `AnycubicFile.cs` (2 624 lines, `AnycubicZipFile.cs` for the container) | 📋 todo |
| **CBDDLP** | Creality LD-002R/H, early Chitubox machines | **`ChituboxFile.cs`** — CBDDLP is the *older Chitubox* container, not a Creality format. **Same family as our CTB writer**: shares the bit-plane RLE codec, differs in magic + header layout | 📋 todo (cheapest of the three) |
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

## CBDDLP / Chitubox digest (pulled, not yet implemented)

**Key finding: CBDDLP is the *same Chitubox family* as the CTB writer we already ship.** Its layer
payload is the **bit-plane RLE** (`BitPlaneRle.DecodePlane`, `RLE8EncodingLimit = 0x7d = 125`) — the
same codec family as `CtbWriter`, *not* the byte-RLE PhotonS uses. So adding CBDDLP is mostly a
different magic + header/layer-def layout on top of a codec we have, rather than new compression
work. (Contrast: PhotonS needed a from-scratch encoder.)

- Magic distinguishes the variants: **CBDDLP `0x12FD0019`**, CTB `0x12FD0086`, CTBv4 `0x12FD0106`,
  CTBv4_GKtwo `0xFF220810`. Little-endian.
- Header, in `[FieldOrder]` order: u32 Magic · u32 Version · f32 BedSizeX/Y/Z · u32 Unknown1 ·
  u32 Unknown2 · f32 TotalHeightMillimeter · f32 LayerHeightMillimeter · f32 LayerExposureSeconds ·
  f32 BottomExposureSeconds · f32 LightOffDelay · u32 BottomLayersCount · u32 ResolutionX ·
  u32 ResolutionY · u32 **PreviewLargeOffsetAddress** · u32 **LayersDefinitionOffsetAddress** ·
  u32 LayerCount · u32 **PreviewSmallOffsetAddress** · u32 PrintTime · u32 ProjectorType ·
  u32 PrintParametersOffsetAddress · u32 PrintParametersSize · u32 AntiAliasLevel · u16 LightPWM ·
  u16 BottomLightPWM · u32 **EncryptionKey** · u32 SlicerOffset · u32 SlicerSize.
- The offsets are absolute file positions, so the writer must lay the file out first and backfill
  them (previews, then the layer-definition table, then the print-parameters block).
- Then a print-parameters block: bottom/lift/retract heights and speeds (plus the `*2` variants),
  rest times, machine-name address+size, anti-alias flag, per-layer settings, timestamps, software
  version (0x1060300 = CTB v3, 0x1090000 = v4, 0x2000000 = v5), transition layer count, and a v4
  print-parameters address.
- **`EncryptionKey > 0` XOR-encrypts the layer data** — the same trap as CTB: writing plaintext
  layers with a non-zero key produces a file the printer rejects.

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
