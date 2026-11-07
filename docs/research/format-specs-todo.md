# Remaining output formats — spec map & how to pull a spec

Item 3 of the tracker. `PhotonS` is done (`PhotonSWriter`). The rest, and **where the spec actually
lives** (the names in the original handoff were guesses and two were wrong):

| Format | Printers | Spec file in UVTools | Status |
|---|---|---|---|
| **PhotonS** `.photon` | Anycubic Photon S | `AnycubicPhotonSFile.cs` | ✅ done |
| **PW0 / PWS** | Anycubic Photon Workshop | `AnycubicFile.cs` (2 624 lines, `AnycubicZipFile.cs` for the container) | 📋 todo |
| **CBDDLP** | Creality LD-002R/H, early Chitubox machines | **`ChituboxFile.cs`** — CBDDLP is the *older Chitubox* container, not a Creality format | 📋 todo |
| **CXDLP** (Creality v1/v2/v4) | Creality Halot, LD-006, … | `CrealityCXDLPFile.cs`, `CrealityCXDLPv1File.cs`, `CrealityCXDLPv4File.cs` | 📋 todo |
| `.mdlp` | MakerJuice/Massive Dimension | `MDLPFile.cs` | 💤 backlog |
| `.zcode` / `.zcodex` | UniFormation / Kelant | `ZCodeFile.cs`, `ZCodexFile.cs` | 💤 backlog |
| `.lgs` | Longer | `LGSFile.cs` | 💤 backlog |
| `.phz` | Zongheng | `PHZFile.cs` | 💤 backlog |

## How to pull a spec (the workflow that worked)

A shallow UVTools clone already exists on the build host — **don't re-clone it**:

```bash
ssh build-host 'cd /mnt/storage/uvtools && git pull --depth 1'   # or re-clone if it's gone
ssh build-host bash -s <<'SH'
F=/mnt/storage/uvtools/UVtools.Core/FileFormats/<File>.cs
grep -nE "FieldOrder|FieldEndianness|FieldEncoding|FieldLength|public (uint|ushort|byte|float|double|string|bool)" $F | head -60
SH
```

The `[FieldOrder(n)]` attributes **are** the byte layout, in order; `[FieldEndianness(...)]` and
`[FieldEncoding("UTF-16BE")]` tell you the representation. Read the `Encode`/`Decode` pair for the
per-layer compression, and `EncodeInternally` for the exact order the pieces are written.

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
