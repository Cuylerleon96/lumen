# Anycubic PW0 / PWS / PWMX resin format — implementation digest

Audited from the UVTools C# source (clean-room reference for a Lumen Kotlin writer).

**Provenance**
- Repo: `https://github.com/sn4k3/UVTools`, local clone at `/mnt/storage/uvtools` (build host `gaming-pc`).
- Commit: `c2d984c50a80409ec9ca11775e3687e9f3719caa` (`v7.1.2`, 2026-10-26).
- Primary file: `UVtools.Core/FileFormats/AnycubicFile.cs` (2624 lines). Supporting: `FileFormats/BitPlaneRle.cs`, `FileFormats/FileFormat.cs`, `Helpers.cs`, `Extensions/FileStreamExtensions.cs`, `Printer/Machine.cs`.
- Line citations below are `File:line` in that commit.

> **Read this first (two premises in the task that the source contradicts):**
> 1. **There is no per-variant magic.** Every Anycubic v1-family file (`pw0`, `pws`, `pwx`, `pwmx`, `pwms`, `pwmb`, …) begins with the *same* 12-byte ASCII mark `"ANYCUBIC"`. The variant is discriminated by **filename extension**, not by bytes. See §1.
> 2. **There is no XOR / encryption / obfuscation anywhere in this format.** The `XorKeystream` helper exists in `FileFormat.cs` but is used only by Chitubox, CrealityCXDLPv4, FDG and PHZ — **never** by `AnycubicFile`. See §9.

---

## 1. Format family, extensions, versions, and how the variant is encoded

`AnycubicFile.FileExtensions` (`AnycubicFile.cs:1221-1246`):

```
pws   Photon / Photon S (PWS)
pw0   Photon Zero (PW0)
pwx   Photon X (PWX)
dlp   Photon Ultra (DLP)
dl2p  Photon D2 (DL2P)
pwmx  Photon Mono X (PWMX)
pmx2  Photon Mono X2 (PMX2)
pwmb  Photon Mono X 6K / Photon M3 Plus (PWMB)
px6s  Photon Mono X 6Ks (PX6S)
pwmo  Photon Mono (PWMO)
pm3n  Photon Mono 2 (PM3N)
pm4n  Photon Mono 4 (PM4N)
pwms  Photon Mono SE (PWMS)
pwma  Photon Mono 4K (PWMA)
pmsq  Photon Mono SQ (PMSQ)
pm3 / pm3m / pm3r / pm5 / pm5s / m5sp / pwc   (M3 / M3 Max / M3 Premium / Mono M5 family / custom)
```

(`pwsd` does **not** exist in the source. `pwsz` is a *different, newer, ZIP/AES-based* format handled by `AnycubicZipFile.cs` — out of scope here.)

**The RLE codec is selected by extension, not by any header byte** (`AnycubicFile.cs:1871-1874`):

```csharp
public PhotonRleFormat RleFormat =>
    FileEndsWith(".pws")
        ? PhotonRleFormat.PWS
        : PhotonRleFormat.PW0;
```

So: **only `.pws` uses the bit-plane RLE; everything else (including `.pw0`, `.pwmx`, `.pwms`, `.pwmb`) uses the PW0 nibble RLE.** `PhotonRleFormat` enum is `{ PWS, PW0 }` (`AnycubicFile.cs:83-87`).

**Version constants** (`AnycubicFile.cs:32-36`) — stored as a **uint32** (see §4), not a byte:

```
VERSION_1   = 1     (0x00000001)
VERSION_515 = 515   (0x00000203)
VERSION_516 = 516   (0x00000204)
VERSION_517 = 517   (0x00000205)
VERSION_518 = 518   (0x00000206)
```

`AvailableVersions = [1, 515, 516, 517, 518]` (`:1329`). Per-extension allowed versions (`GetAvailableVersionsForExtension`, `:1331-1367`):

| extension | allowed versions |
|---|---|
| `pws`, `pw0`, `pwx` | `[1]` |
| `pwmx`, `pwmo`, `pwms`, `pmsq`, `dlp` | `[1, 515, 516]` |
| `pwma`, `pm3`, `pm3m` | `[515, 516]` |
| `pwmb`, `dl2p`, `pmx2`, `pm3r` | `[515, 516, 517]` |
| `pm3n`, `pm5`, `px6s` | `[517]` |
| `pm5s`, `m5sp` | `[518]` |

`DefaultVersion = VERSION_1` (`:1369`).

**Three things a printer can key off to identify a variant** (a writer must set all consistently with the extension):
1. the file extension (the printer picks the parser by extension);
2. `FileMark.Version` (uint32 at offset 12);
3. `Machine.LayerImageFormat` string = `"pw0Img"` for PW0-family or `"pwsImg"` for PWS (`:2053`: `MachineSettings.LayerImageFormat = $"{RleFormat.ToString().ToLower()}Img";`).

The only other literal magic in the whole Anycubic codebase is `"ANYCUBIC-PWSZ"` (`AnycubicZipFile.cs:923`) and uint `0x20251024` (`AnycubicZipFile.cs:1052`) — both belong to the newer ZIP format, not to PW0/PWS/PWMX.

---

## 2. Endianness

`FileFormat.FileFormatEndianness => Endianness.Little` (`FileFormat.cs:1994`); `AnycubicFile` does **not** override it. The global serializer is `new BinarySerializer { Endianness = Endianness.Little }` (`Helpers.cs:24`). Serialization goes through `WriteSerialize` → `Helpers.Serialize(value, FileFormatEndianness)` (`FileFormat.cs:5282-5284`).

**All multi-byte integers and floats in this format are little-endian IEEE-754 / two's-complement.** The only big-endian touches are the explicit `DATATYPE_*_BE` pixel packings, which Anycubic does not use (it uses plain `DATATYPE_RGB565`, little-endian).

Strings marked `[SerializeAs(SerializedType.TerminatedString)]` with a `[FieldLength(n)]` are written as ASCII bytes followed by a NUL terminator, zero-padded to exactly `n` bytes. Concretely the file mark is `'A','N','Y','C','U','B','I','C'` at bytes 0..7 and `0x00` at bytes 8..11 (terminator + padding are indistinguishable — all zero).

---

## 3. Top-level file layout

`EncodeInternally` (`AnycubicFile.cs:2017-2215`) writes blocks in this physical order (addresses are absolute file offsets):

```
0                         : FileMark            (written LAST, seek-back; §4)
FileMark.HeaderAddress    : HEADER              (§5)
FileMark.PreviewAddress   : PREVIEW + raw RGB565 data   (only if >=1 thumbnail; §6)
FileMark.LayerImageColorTableAddress : LayerImageColorTable  (only Version>=515; §7)
FileMark.LayerDefinitionAddress      : LAYERDEF  (§12)
FileMark.LayerImageAddress           : (for Version>=516) EXTRA (§8) then MACHINE (§9) then layer blobs
```

Exact control flow (verbatim excerpts):

```csharp
// :2056-2058
FileMarkSettings.HeaderAddress = (uint)Helpers.Serializer.SizeOf(FileMarkSettings);
FileMarkSettings.PreviewAddress = 0;
FileMarkSettings.Preview2Address = 0;
// :2062-2063
outputFile.Seek((int)FileMarkSettings.HeaderAddress, SeekOrigin.Begin);
outputFile.WriteSerialize(HeaderSettings);
// :2065-2072  preview 1
if (ThumbnailsCount >= 1) {
    FileMarkSettings.PreviewAddress = (uint)outputFile.Position;
    var preview = new Preview(Thumbnails[0]);
    outputFile.WriteSerialize(preview);
    outputFile.WriteBytes(preview.Data);
    preview.ResetData();
}
// :2074-2104  colour table (Version>=515)
FileMarkSettings.LayerImageColorTableAddress = (uint)outputFile.Position;
outputFile.WriteSerialize(LayerImageColorSettings);
// :2108-2114
LayersDefinition = new LayerDefinition(LayerCount);
SubLayersDefinition = new SubLayerDefinition(LayerCount);
FileMarkSettings.LayerDefinitionAddress = (uint)outputFile.Position;
FileMarkSettings.LayerImageAddress = FileMarkSettings.LayerDefinitionAddress
    + LayersDefinition.TableLength + AnycubicNamedTable.TableBaseLength;   // +16
outputFile.Seek(FileMarkSettings.LayerImageAddress, SeekOrigin.Begin);
// :2116-2155  Version>=516: EXTRA, MACHINE, (>=517) SOFTWARE, MODEL, (>=518) SUBIMGS + PREVIEW2
//   Version==515: ExtraAddress = LayerImageAddress (nothing written)
// :2159-2199  per layer: layerDef.DataAddress = outputFile.Position; write EncodedRle
// :2202-2203  seek back to LayerDefinitionAddress, write LAYERDEF
// :2205-2210  (Version>=518) seek back to SubLayerDefinitionAddress, write SUBIMGS
// :2213-2214  seek to 0, write FileMarkSettings
```

**The FileMark is written last** because all addresses are only known after the payload is laid down. A writer must either buffer the whole file or seek back to offset 0 (UVTools does the latter).

`NumberOfTables` is a fixed per-version constant (`:2019-2027`): `1→4, 515→5, 516→8, 517→9, 518→11`.

---

## 4. FileMark — the top-level header (always first, 12-byte magic)

Class `FileMark` (`AnycubicFile.cs:121-221`). Field order and on-disk layout (LE):

| off | size | field | type | notes |
|----:|----:|-------|------|-------|
| 0 | 12 | `Mark` | terminated ASCII[12] | `"ANYCUBIC"` + NUL + zero pad (`SectionMarkFile = "ANYCUBIC"`, `MarkSize = 12`) |
| 12 | 4 | `Version` | uint32 | 1 / 515 / 516 / 517 / 518 |
| 16 | 4 | `NumberOfTables` | uint32 | see §3 |
| 20 | 4 | `HeaderAddress` | uint32 | absolute offset of HEADER (= size of FileMark) |
| 24 | 4 | `SoftwareAddress` | uint32 | absolute; 0 unless Version>=517 |
| 28 | 4 | `PreviewAddress` | uint32 | absolute; 0 if no thumbnail |
| 32 | 4 | `LayerImageColorTableAddress` | uint32 | absolute; 0 unless Version>=515 |
| 36 | 4 | `LayerDefinitionAddress` | uint32 | absolute |
| 40 | 4 | `ExtraAddress` | uint32 | absolute; written only Version>=516 |
| 44 | 4 | `MachineAddress` | uint32 | **only if Version>=516** |
| 48 | 4 | `LayerImageAddress` | uint32 | absolute start of first layer blob |
| 52 | 4 | `ModelAddress` | uint32 | **only if Version>=517** |
| 56 | 4 | `SubLayerDefinitionAddress` | uint32 | **only if Version>=518** |
| 60 | 4 | `Preview2Address` | uint32 | **only if Version>=518** |

Conditional fields are gated by `[SerializeWhen(nameof(Version), VERSION_5xx, ComparisonOperator.GreaterThanOrEqual)]` (`:185,198,205,212`). Therefore:

- `FileMark` size = **48** (V1, V515), **52** (V516), **56** (V517), **64** (V518).
- `HeaderAddress == FileMark size` exactly (`:2056`), e.g. 48 for `.pws`/`.pw0`/`.pwx` and for V515.

**Trap:** the on-disk *byte order* of the address fields is the declaration order above, so for V516 `MachineAddress` (off 44) precedes `LayerImageAddress` (off 48), but for V1/V515 `MachineAddress` is absent and `LayerImageAddress` sits at off 44. Do not hardcode a fixed layout across versions.

---

## 5. HEADER table (`"HEADER"`)

Class `Header : AnycubicNamedTable` (`AnycubicFile.cs:363-486`).

Every `AnycubicNamedTable` begins with a 12-byte NUL-padded name and a uint32 length (`:249-258`):

```
off 0  : TableName  ASCII[12]  ("HEADER" + NUL + pad)
off 12 : TableLength uint32
```

`TableBaseLength = MarkSize + 4 = 16` (`:229`). **`Header.IncludeBaseTableLength` is the default `false`, so `Header.TableLength` is the *derived* (payload-only) length, excluding the 16-byte name+length.**

Body (`FieldOrder(1)..(25)`, `:369-479`), in order, all LE:

| # | field | type | bytes |
|---|-------|------|------:|
| 1 | `PixelSizeUm` | float | 4 |
| 2 | `LayerHeight` | float (mm) | 4 |
| 3 | `ExposureTime` | float (s) | 4 |
| 4 | `WaitTimeBeforeCure` | float | 4 |
| 5 | `BottomExposureTime` | float | 4 |
| 6 | `BottomLayersCount` | float | 4 |
| 7 | `LiftHeight` | float | 4 |
| 8 | `LiftSpeed` | float (mm/s) | 4 |
| 9 | `RetractSpeed` | float (mm/s) | 4 |
| 10 | `VolumeMl` | float | 4 |
| 11 | `AntiAliasing` | uint32 | 4 |
| 12 | `ResolutionX` | uint32 | 4 |
| 13 | `ResolutionY` | uint32 | 4 |
| 14 | `WeightG` | float | 4 |
| 15 | `Price` | float | 4 |
| 16 | `PriceCurrencySymbol` | char[4] (`'$'`,`0`,`0`,`0`) | 4 |
| 17 | `PerLayerSettings` | bool as UInt4 | 4 |
| 18 | `PrintTime` | uint32 | 4 |
| 19 | `TransitionLayerCount` | uint32 | 4 |
| 20 | `TransitionLayerType` | uint32 | 4 |
| 21 | `AdvancedMode` | uint32 | 4 — only if TableLength>=84 |
| 22 | `Grey` | ushort | 2 — only if TableLength>=86 |
| 23 | `BlurLevel` | ushort | 2 — only if TableLength>=88 |
| 24 | `ResinType` | uint32 | 4 — only if TableLength>=92 |
| 25 | `IntelligentMode` | bool as UInt4 | 4 — only if TableLength>=96 |

`TableLength` values set by the writer (`:2029-2035`):

```csharp
HeaderSettings.TableLength = Version switch {
    >= VERSION_518 => 96,
    >= VERSION_517 => 92,
    >= VERSION_516 => 84,
    _              => 80
};
```

Base payload = 20 fields × 4 = 80 bytes; +4 (`AdvancedMode`) = 84 for ≥516; +2+2+4 = 92 for ≥517; +4 = 96 for ≥518. On disk the HEADER block is `16 + TableLength` bytes (96/100/108/112).

`PixelSizeUm` default `47.25f` (`:369`). `PerLayerSettings` and `IntelligentMode` use `[SerializeAs(SerializedType.UInt4)]` (a bool stored in a 4-byte word; 0/1).

The reader validates with `HeaderSettings.Validate()` (`:2243`), which compares `TableLength` against the serializer's computed size (`AnycubicNamedTable.ValidateExpecting`, `:344-356`). **A wrong `TableLength` here throws on load.**

---

## 6. PREVIEW table (thumbnail 1)

Class `Preview : AnycubicNamedTable` (`AnycubicFile.cs:496-551`). `IncludeBaseTableLength => true`, so its `TableLength` **includes** the 16-byte base and the image data.

```
off 0  : TableName  ASCII[12]  "PREVIEW" + NUL + pad
off 12 : TableLength uint32    = 28 + DataSize
off 16 : ResolutionX uint32    default 224
off 20 : Mark        ASCII[4]  "x" + NUL + pad    ([FieldLength(4)], TerminatedString)
off 24 : ResolutionY uint32    default 168
off 28 : Data        uint8[ResolutionX*ResolutionY*2]   (raw, no length prefix)
```

`DataSize => ResolutionX * ResolutionY * 2` (`:521`). `Data = EncodeImage(DATATYPE_RGB565, mat)` (`:538`).

**Format = RGB565, little-endian 16-bit per pixel, no compression, no header inside the blob.** From `FileFormat.EncodeImage` (`FileFormat.cs:1389-1393`):

```csharp
case PixelPacking.Rgb565:
    var rgb565 = (ushort)(((r & 0b11111000) << 8) | ((g & 0b11111100) << 3) | (b >> 3));
    BitExtensions.ToBytesLittleEndian(rgb565, bytes, index);   // low byte first
    index += 2;
```

So the 16-bit value is `RRRRRGGG GGGBBBBB`; it is written **low byte first** (byte0 = `GGGBBBBB`, byte1 = `RRRRRGGG`). Source pixels are 3-channel BGR (`b,g,r` read in that order, `:1366-1378`). Rows are emitted in Mat memory order (top row first, no vertical flip).

`ThumbnailsOriginalSize => [new(224,168), new(330,190)]` (`:1321`). 224×168 ⇒ DataSize = 75264 bytes, block = 75292 bytes.

---

## 7. LayerImageColorTable (grayscale LUT)

Class `LayerImageColorTable` (`AnycubicFile.cs:608-633`) — **NOT** an `AnycubicNamedTable`: it has no name/length header, it is a raw 28-byte struct.

```
off 0  : UseFullGreyscale uint32
off 4  : GreyMaxCount     uint32   (default 16)
off 8  : Grey            uint8[GreyMaxCount]   (16 entries)
off 24 : Unknown         uint32
```

Default `Grey` LUT (`:615-624`): `15,31,47,63,79,95,111,127,143,159,175,191,207,223,239,255`.

When encoding, UVTools overwrites it with `Grey[i] = min((i+1) * (255 / max(1,AntiAliasing)), 255)` and forces `GreyMaxCount = 16` (growing only if AA>16) (`:2076-2100`). Present only for Version≥515 (`LayerImageColorTableAddress`).

---

## 8. EXTRA table (`"EXTRA"`) — Version≥516 only

Class `Extra : AnycubicNamedTable` (`AnycubicFile.cs:639-695`).

```
off 0  : TableName   ASCII[12] "EXTRA" + pad
off 12 : TableLength uint32    <-- HARDCODED to 24 in the ctor ("Don't know why...") :687
body (14 x 4 = 56 bytes):
  BottomLiftCount        uint32
  BottomLiftHeight1      float
  BottomLiftSpeed1       float
  BottomRetractSpeed2    float
  BottomLiftHeight2      float
  BottomLiftSpeed2       float
  BottomRetractSpeed1    float
  NormalLiftCount        uint32
  LiftHeight1            float
  LiftSpeed1             float
  RetractSpeed2          float
  LiftHeight2            float
  LiftSpeed2             float
  RetractSpeed1          float
```

**Trap / inconsistency:** the length *field* is 24, but the table body is 56 bytes and the whole block on disk is `12 + 4 + 56 = 72` bytes. The reader deserializes all 14 fields and its length check is commented out (`//ExtraSettings.ValidateExpecting(24);`, `:2277`). It is address-driven, so a wrong length is tolerated on read. **UNRESOLVED:** whether real printers read 24 or 56 bytes here; UVTools writes 56 and sets the field to 24. A writer matching UVTools should write 72 bytes with the length field = 24.

---

## 9. MACHINE table (`"MACHINE"`) — Version≥516 only

Class `Machine : AnycubicNamedTable` (`AnycubicFile.cs:701-802`). `IncludeBaseTableLength => true`, so **`Machine.TableLength` INCLUDES the 16-byte base** — the opposite convention from HEADER.

```
off 0   : TableName          ASCII[12] "MACHINE" + pad
off 12  : TableLength        uint32    (whole: 156 base, or 224 for >=518)
off 16  : MachineName        ASCII[96] (TerminatedString, NUL-padded)
off 112 : LayerImageFormat   ASCII[16] ("pw0Img" / "pwsImg")
off 128 : MaxAntialiasingLevel uint32  (default 16)
off 132 : PropertyFields     uint32    (15 for >=518, 7 for >=517, else 1)  :2043-2048
off 136 : DisplayWidth       float
off 140 : DisplayHeight      float
off 144 : MachineZ           float
off 148 : MaxFileVersion     uint32    (default VERSION_518)
off 152 : MachineBackground  uint32    (default 6506241 = 0x00634701)
--- the following appear only when TableLength >= the stated threshold ---
off 156 : PixelWidthUm       float    (>=160)
off 160 : PixelHeightUm      float    (>=164)
off 164 : Padding1           uint32   (>=168)
off 168 : Padding2           uint32   (>=172)
off 172 : Padding3           uint32   (>=176)
off 176 : Padding4           uint32   (>=180)
off 180 : Padding5           uint32   (>=184)
off 184 : Padding6           uint32   (>=188)
off 188 : Padding7           uint32   (>=192)
off 192 : Padding8           uint32   (>=196)
off 196 : DisplayCount       uint32   (>=200, default 1)
off 200 : Padding9           uint32   (>=204)
off 204 : ResolutionX        ushort   (>=206)
off 206 : ResolutionY        ushort   (>=208)
off 208 : Padding10          uint32   (>=212)
off 212 : Padding11          uint32   (>=216)
off 216 : Padding12          uint32   (>=220)
off 220 : Padding13          uint32   (>=224)
```

`Machine.TableLength` set by writer (`:2037-2041`): `224` for Version≥518, else `156`. Note `DisplayCount`, `ResolutionX`/`ResolutionY` (ushort) and the PaddingN fields only exist in the ≥518 (224-byte) layout; a V516/V517 MACHINE is 156 bytes.

`MachineSettings.Validate()` is called on read (`:2302`), so `TableLength` must be exactly right.

---

## 10. SOFTWARE table (`"SOFTWARE"`) — Version≥517 only

Class `Software` (`AnycubicFile.cs:808-839`) — **NOT** named; raw 164-byte struct with an internal length field.

```
off 0   : SoftwareName     ASCII[32]
off 32  : TableLength      uint32 = 164 (TableSize)
off 36  : Version          ASCII[32]
off 68  : OperativeSystem  ASCII[64]
off 132 : OpenGLVersion    ASCII[32]  (default "3.3-CoreProfile")
```

All strings NUL-padded to their field width.

---

## 11. MODEL table (`"MODEL"`) — Version≥517 only

Class `Model : AnycubicNamedTable` (`AnycubicFile.cs:845-878`). `IncludeBaseTableLength => true`.

```
off 0  : TableName       ASCII[12] "MODEL" + pad
off 12 : TableLength     uint32 = 48
off 16 : MinX float
off 20 : MinY float
off 24 : MinZ float
off 28 : MaxX float
off 32 : MaxY float
off 36 : MaxZ float
off 40 : SupportsEnabled uint32
off 44 : SupportsDensity float
```

Bounds are relative to the platform centre (`Parse`, `:866-877`).

---

## 12. LAYERDEF table and the per-layer record

Class `LayerDefinition : AnycubicNamedTable` (`AnycubicFile.cs:1096-1139`), name `"LAYERDEF"`, `IncludeBaseTableLength => false` (derived length).

```
off 0  : TableName   ASCII[12] "LAYERDEF" + pad
off 12 : TableLength uint32    = 4 + 32*LayerCount     (payload only)
off 16 : LayerCount  uint32
off 20 : Layers      LayerDef[LayerCount]              (each 32 bytes)
```

Whole on-disk block = `16 + 4 + 32*N = 20 + 32*N`.

`LayerDef` (`AnycubicFile.cs:884-1051`), `ClassSize = 32`, **each record 32 bytes**:

| off | field | type | notes |
|----:|-------|------|-------|
| 0 | `DataAddress` | uint32 | **absolute file offset** of the encoded layer blob |
| 4 | `DataLength` | uint32 | length in bytes of the encoded blob |
| 8 | `LiftHeight` | float | |
| 12 | `LiftSpeed` | float | |
| 16 | `ExposureTime` | float | |
| 20 | `LayerHeight` | float | relative Z (mm) for this layer |
| 24 | `NonZeroPixelCount` | uint32 | |
| 28 | `Padding1` | uint32 | |

**`DataAddress` is a plain absolute file offset** — *not* a page/offset pair (`[FieldOrder(0)] public uint DataAddress` with comment "Gets the layer image offset to encoded layer data", `:888-892`). The writer sets it directly: `layerDef.DataAddress = (uint)outputFile.Position;` before appending the blob (`:2189,2195`). No CRC is appended to the blob (see §13).

---

## 13. SUBIMGS / SubLayerDef — Version≥518 only

`SubLayerDefinition : AnycubicNamedTable` (`AnycubicFile.cs:1141-1187`), name `"SUBIMGS"`, `IncludeBaseTableLength => true`:

```
off 0  : TableName   ASCII[12] "SUBIMGS" + pad
off 12 : TableLength uint32    = 24 + 44*LayerCount   (whole)
off 16 : LayerCount  uint32
off 20 : Index       uint32    (default 1)
off 24 : Layers      SubLayerDef[LayerCount]          (each 44 bytes)
```

`SubLayerDef` (`:1053-1090`): `DataAddress` uint32, `DataLength` uint32, `NonZeroPixelCount` uint32, then 8 × float padding = **44 bytes**. For ≥518 the writer seeks past this table (`:2137`) and writes it back later; the SubLayerDef records mirror the LayerDef `DataAddress`/`DataLength`/`NonZeroPixelCount` (`SetFrom`, `:1084-1089`).

---

## 14. PREVIEW2 table (thumbnail 2) — Version≥518 only

Class `Preview2 : AnycubicNamedTable` (`AnycubicFile.cs:553-602`). `IncludeBaseTableLength => true`.

```
off 0  : TableName         ASCII[12] "PREVIEW2" + pad
off 12 : TableLength       uint32    = 28 + DataSize
off 16 : ResolutionX       uint32    (field default 320; writer uses actual thumbnail width)
off 20 : BackgroundColor1  ushort    (default 4293 = 0x10C5)
off 22 : BackgroundColor2  ushort
off 24 : ResolutionY       uint32    (field default 190)
off 28 : Data              uint8[ResolutionX*ResolutionY*2]  (RGB565, little-endian, raw)
```

Same RGB565 little-endian packing as PREVIEW (§6). `ThumbnailsOriginalSize` lists `330x190` for the second thumbnail while the field default is `320x190` — the stored `ResolutionX/Y` must equal the actual blob dimensions; use whatever your thumbnail generator produces and keep `DataSize` consistent.

---

## 15. Layer image encoding — PW0 (nibble RLE) — used by `.pw0`, `.pwmx`, `.pwms`, `.pwmb`, `.pwx`, all non-`.pws`

The layer image is a single-channel 8-bit grayscale Mat of `ResolutionX × ResolutionY` bytes (`CreateMat` → `new Mat(Resolution, DepthType.Cv8U, 1)`, `FileFormat.cs:8165-8170`). Runs are computed on the **high nibble** of each byte.

### 15.1 Encode — `EncodePW0` (`AnycubicFile.cs:2544-2621`)

```csharp
var color = span[pixel] >> 4;                        // 0..15 = high nibble
var runLength = GetHighNibbleRunLength(span, pixel); // count of consecutive bytes with same high nibble
```

`GetHighNibbleRunLength` (`FileFormat.cs:1235-1242`) counts bytes sharing the same `& 0xf0`. `PutReps` (`:2555-2584`) emits the code word(s):

```csharp
static void PutReps(ref BufferWriterSlim<byte> rawData, ref int reps, int lastColor) {
    while (reps > 0) {
        var done = reps;
        if (lastColor is 0 or 0xf) {                 // 2-byte extended form
            if (done > RLE4EncodingLimit) done = RLE4EncodingLimit;   // 0xfff = 4095
            var more = (ushort)(done | (lastColor << 12));
            rawData.Add((byte)(more >> 8));          // high byte: [color:4][count_hi:4]
            rawData.Add((byte)more);                 // low byte : count_lo:8
        } else {                                     // 1-byte form, colors 1..14
            if (done > 0xf) done = 0xf;              // 15
            rawData.Add((byte)(done | (lastColor << 4)));
        }
        reps -= done;
    }
}
```

**Codec rules:**
- **1-byte form** (nibble color 1..14): `byte = done | (color << 4)` — high nibble = `color` (1..14), low nibble = `done` (run length 1..15).
- **2-byte form** (nibble color exactly `0x0` or `0xf`): byte0 = `(color<<4) | (count>>8)`; byte1 = `count & 0xff`. Count is 12 bits, max **4095** (`RLE4EncodingLimit = 0xfff`, `:40`).
- **Runs longer than the limit are split** into repeated code words of the same color: for colors 1..14 max 15 per byte; for 0/15 max 4095 per 2 bytes. The `while (reps > 0)` loop emits as many words as needed.
- Color codes are quantised to the high nibble and reconstructed as `(code<<4)|code`, i.e. gray levels `0x00, 0x11, 0x22, …, 0xff`.

### 15.2 Decode — `DecodePW0` (`AnycubicFile.cs:2468-2542`)

```csharp
var b = encodedRle[i];
var code = b >> 4;
var repeat = b & 0xf;
switch (code) {
    case 0x0:  color = 0;   i++;
               if (i >= encodedRle.Length) { repeat = imageLength - pixelPos; break; }
               repeat = (repeat << 8) + encodedRle[i];   break;
    case 0xf:  color = 255; i++;
               if (i >= encodedRle.Length) { repeat = imageLength - pixelPos; break; }
               repeat = (repeat << 8) + encodedRle[i];   break;
    default:   color = (byte)((code << 4) | code); break;   // 0x11..0xee
}
mat.FillSpan(ref pixelPos, repeat, color);   // fill `repeat` bytes with `color`, advance position
```

`FillSpan` (external `EmguExtensions` package, not in this repo) fills `repeat` consecutive bytes with the single value `color` and advances `pixelPos`. Decoding stops when `pixelPos == imageLength`; overshoot/undershoot throws `FileLoadException`. `imageLength = ResolutionX * ResolutionY`.

### 15.3 Bit order

**No bit reversal of any kind.** The nibble codec reads/writes plain high/low nibbles; there is no 7-bit/8-bit reversal, no byte-swap, no MSB/LSB flipping. The only endianness concern is the little-endian scalar fields elsewhere.

---

## 16. Layer image encoding — PWS (bit-plane RLE) — used only by `.pws`

Class `BitPlaneRle` (`FileFormats/BitPlaneRle.cs`). Anti-aliased layers are stored as **one 1-bit run-length plane per AA threshold level**, concatenated.

### 16.1 Encode — `LayerDef.EncodePWS` (`AnycubicFile.cs:992-1026`)

```csharp
for (byte aalevel = 1; aalevel <= slicerFile.AntiAliasing; aalevel++) {
    var threshold = (byte)(255 * aalevel / (slicerFile.AntiAliasing + 1) + 1);
    BitPlaneRle.EncodePlane(ref rawData, span, threshold, RLE1EncodingLimit);   // 125
}
```

`BitPlaneRle.EncodePlane` (`BitPlaneRle.cs:45-60`):

```csharp
var on = pixels[pixel] >= threshold;
var runLength = GetRunLength(pixels, pixel, threshold);
var onFlag = on ? 0x80 : 0;
for (; runLength > 0; runLength -= maxRunLength)
    output.Add((byte)(Math.Min(runLength, maxRunLength) | onFlag));
```

**Codec rules:**
- Each byte: **bit 7 (`0x80`) = pixel state (1 = "on"/white, 0 = "off"/black); bits 6..0 = run length.**
- `RLE1EncodingLimit = 0x7d = 125` (`:39`) is the max run the encoder writes per byte. **Runs longer than 125 are split into multiple bytes**, each repeating the same on-flag (`for (; runLength > 0; runLength -= maxRunLength)`).
- The decoder accepts run lengths `1..127` (`& 0x7f`); the encoder never emits 126/127 (it caps at 125).
- A "run" is a maximal stretch of pixels all `>= threshold` (on) or all `< threshold` (off), computed on the 8-bit grayscale image (`GetRunLength`, `BitPlaneRle.cs:26-36`).
- Planes are emitted in order `aalevel = 1 .. AntiAliasing`, concatenated with no separator. `DataLength` = total bytes over all planes.

### 16.2 Decode — `LayerDef.DecodePWS` (`AnycubicFile.cs:965-990`) + `BitPlaneRle`

```csharp
for (byte bit = 0; bit < slicerFile.AntiAliasing; bit++)
    index += BitPlaneRle.DecodePlane(EncodedRle.AsSpan(index), span);
BitPlaneRle.CountsToGray(span, slicerFile.AntiAliasing);
```

`DecodePlane` (`BitPlaneRle.cs:89-115`) masks `reps = byte & 0x7f`, and for `byte & 0x80 != 0` increments every pixel in the run (each pixel holds a running count of how many planes it was "on" in). `CountsToGray` (`:122-136`) maps `count → count*step - 1` where `step = 256 / AntiAliasing`. Decoding each plane returns `index+1` at the exact image end and throws if a plane overruns.

`AntiAliasing` must be ≥1; `DecodePWS` throws `FileLoadException` if it is 0 (`:967-968`).

### 16.3 Bit order

**No bit reversal.** Bit 7 is a state flag and bits 6..0 are a plain little-endian-ish run count. This is the "7-bit RLE" family — the low 7 bits are the count, the top bit is the flag — *not* a bit-reversed variant.

---

## 17. Encryption / obfuscation

**None.** Exhaustive greps for `xor|encrypt|decrypt|obfuscat` across `UVtools.Core/` return nothing for `AnycubicFile.cs` or `AnycubicZipFile.cs`'s v1 paths. The generic helper:

```csharp
// FileFormat.cs:1203-1217
internal static void XorKeystream(Span<byte> data, uint key, uint step) { … }
```

is referenced **only** by `ChituboxFile.cs:2054`, `CrealityCXDLPv4File.cs:1690`, `FDGFile.cs:1141`, `PHZFile.cs:1163` — **never** by Anycubic. The PW0/PWS layer blobs are stored in plain RLE with no XOR, no key, no per-file salt, no region masking. If your previous notes assumed an XOR step for Anycubic, that assumption is wrong for this format family.

---

## 18. Checksums / CRCs

A CRC-16-ANSI table and function exist but are **not emitted**:

- `CRC16Table` (`AnycubicFile.cs:43-77`), `LayerDef.CRCRle4` (`:1028-1039`), `LayerDef.CRCEncodedRle` (`:1041-1044`).
- The only call site is inside a **commented-out block** in `EncodePW0` (`:2613-2620`):

```csharp
/*EncodedRle = rawData.ToArray();
DataLength = (uint)rawData.Count;
ushort crc = CRCRle4(EncodedRle);
rawData.Add((byte)(crc >> 8));
rawData.Add((byte)crc);
return EncodedRle;*/
```

So the writer must **not** append a CRC to layer blobs. No file-level checksum, no length-must-equal-file-size field, and no required trailing block exist (the FileMark is at offset 0 and is written last; nothing follows the last layer blob).

---

## 19. Silent-failure traps / writer checklist

1. **Extension, not magic, selects the codec and machine.** Write `.pws` only for the bit-plane RLE; `.pw0`/`.pwmx`/`.pwms`/`.pwmb` use the nibble RLE. Same 12-byte `"ANYCUBIC"` mark for all.
2. **`Header.TableLength` is the payload length (80/84/92/96); `Machine.TableLength` is the whole length (156/224).** The two named tables use opposite conventions. Getting either wrong makes `Validate()` throw on load.
3. **`EXTRA.TableLength` field = 24 but the block is 72 bytes** (14×4-byte body). Reader ignores it. Match UVTools or flag it — UNRESOLVED.
4. **Version-dependent field presence in the FileMark itself** (MachineAddress ≥516, ModelAddress ≥517, SubLayer/Preview2 ≥518). FileMark size is 48/48/52/56/64. Compute `HeaderAddress = FileMark size`, never hardcode.
5. **All addresses are absolute file offsets** and must be patched in after the payload is written (UVTools writes the FileMark last via seek-back).
6. **`LayerDef.DataAddress` is absolute**, `DataLength` is the exact RLE byte count. No CRC, no padding, no alignment.
7. **PW0 nibble codec:** extended 2-byte form only for nibble colors `0x0` and `0xf`; single byte caps at 15; extended caps at 4095; quantise via high nibble, reconstruct `(code<<4)|code`.
8. **PWS bit-plane codec:** top bit = state, low 7 bits = run (encoder caps at 125); one plane per AA level; concatenated; `AntiAliasing >= 1`.
9. **No bit reversal anywhere.** Do not apply the 7-bit/8-bit swap some other vendors' RLEs need.
10. **Preview is raw RGB565 little-endian**, no compression, top-down, byte0 = `GGGBBBBB`. `TableLength` includes the 16-byte base + 12 header bytes + data.
11. **No XOR, no checksum, no trailing block.** Don't add them.
12. `PerLayerSettings`/`IntelligentMode` are 4-byte (UInt4) bools, not 1 byte.

---

## 20. Target printer parameters (from `Printer/Machine.cs:276-309`)

| printer | extension | resolution | display (mm) | Z (mm) | allowed versions |
|---|---|---|---|---|---|
| Photon Mono X | `pwmx` | 3840×2400 | 192 × 120 | 245 | 1 / 515 / 516 |
| Photon Mono X 6K | `pwmb` | 5760×3600 | 198.15 × 123.84 | 245 | 515 / 516 / 517 |
| Photon Mono X 6Ks | `px6s` | 5760×3600 | 195.84 × 122.40 | 200 | 517 |
| Photon Mono SE | `pwms` | 1620×2560 | 82.62 × 130.56 | 160 | 1 / 515 / 516 |

Display mirror is always `FlipDirection.Horizontally` (`:1503-1507`). Both Mono X targets are PW0-family (nibble RLE), **not** PWS. Recommended target version for both: **516** (or 515 for the Mono X if you want to skip EXTRA/MACHINE).

`PixelSizeUm` (header field 1) default is `47.25f`; the MACHINE table's `PixelWidthUm`/`PixelHeightUm` (only in the ≥518 layout) carry the real per-axis pixel pitch. For a 3840×2400 / 192×120 machine the pitch is 192/3840 = 0.05 mm = 50 µm; for 5760×3600 / 198.15×123.84 it is 198.15/5760 = 0.0344… mm ≈ 34.4 µm. UVTools sets `HeaderSettings.PixelSizeUm = PixelSizeMicronsMax` (`:2050`).

---

## 21. UNRESOLVED items

- **EXTRA body size** — field says 24, actual serialized body is 56 bytes / 72-byte block; whether printers read 24 or 56 is not determinable from this source (validation disabled). [§8]
- **PREVIEW2 second-thumbnail dimensions** — `ThumbnailsOriginalSize` says 330×190 while the field default is 320×190; the stored resolution simply mirrors whatever blob you write, so pick one and stay consistent. [§14]
- **`FillSpan`** is defined in the external `EmguExtensions` package (not vendored in this repo); its exact semantics are inferred from call sites (`fill N bytes with value, advance cursor`). [§15.2]
- **String terminator vs padding split** inside the fixed-width terminated strings is unobservable (all padding is `0x00`). [§2]
- Whether real printer firmware strictly requires `Machine.MaxFileVersion`, `MachineBackground`, `PropertyFields` and the `PaddingN` fields to be specific values (vs. any value) is not asserted by the reader — it only validates `TableLength`. [§9]
