#!/usr/bin/env python3
"""Independently validate a CTB this project wrote, optionally against the SL1 it came from.

Why: a round-trip through our own encoder cannot catch a mistake our encoder and decoder share, and
the container's ORIENTATION is exactly that kind of mistake — a transposed raster produces a
structurally perfect file whose print comes out a quarter turn round. So this decodes the written
bytes with a parser written from the open spec (not from our Kotlin), and can compare the result
pixel-for-pixel against the source layers.

Usage:
  scripts/ctb_validate.py <file.ctb> [--sl1 <source.sl1>] [--layer N] [--png out.png]

Checks (each exits non-zero on failure):
  * magic + version
  * declared resolution against the expected panel (when --expect WxH is given)
  * every layer's RLE7 blob decodes to exactly width*height pixels
  * with --sl1: the decoded layer equals the source layer rotated 90 degrees CLOCKWISE — the
    measured engine->panel transform (see RasterOrient in the source for the measurement).

The SL1 rotation is the whole point: the engine emits the panel's TRANSPOSE, with world X running
down the image reversed, so a container is only correct if the mask is turned a quarter turn
clockwise. Getting that backwards mirrors/rotates the print.
"""
import argparse
import io
import struct
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

HEADER = 0x70
LAYER_HEADER = 36


def parse_ctb(path):
    with open(path, 'rb') as f:
        data = f.read()
    magic, version = struct.unpack_from('<II', data, 0)
    if magic != 0x12FD0086:
        sys.exit(f"bad magic 0x{magic:08X} (expected 0x12FD0086)")
    out = {
        'version': version,
        'printer_mm': struct.unpack_from('<fff', data, 0x08),
        'overall_height': struct.unpack_from('<f', data, 0x1C)[0],
        'layer_height': struct.unpack_from('<f', data, 0x20)[0],
        'bottom_layers': struct.unpack_from('<I', data, 0x30)[0],
        'resolution': tuple(int(v) for v in struct.unpack_from('<ff', data, 0x34)),
        'large_preview_offset': struct.unpack_from('<I', data, 0x3C)[0],
        'layer_table_offset': struct.unpack_from('<I', data, 0x40)[0],
        'layer_count': struct.unpack_from('<I', data, 0x44)[0],
        'small_preview_offset': struct.unpack_from('<I', data, 0x48)[0],
        'print_time_s': struct.unpack_from('<I', data, 0x4C)[0],
        'projection': struct.unpack_from('<I', data, 0x50)[0],
        'encryption_key': struct.unpack_from('<I', data, 0x64)[0],
    }
    layers = []
    t = out['layer_table_offset']
    for i in range(out['layer_count']):
        rec = t + i * LAYER_HEADER
        z, expo, off, data_offset, data_size = struct.unpack_from('<fffII', data, rec)
        layers.append({
            'z': z, 'exposure': expo, 'light_off': off,
            'offset': data_offset, 'size': data_size,
            'blob': data[data_offset:data_offset + data_size],
        })
    out['layers'] = layers
    out['_data'] = data
    return out


def decode_rle7(blob, expected_pixels):
    """RLE7: bits 6:0 = value, bit 7 = run flag; the run length follows big-endian with MSB prefixes."""
    out = bytearray()
    i = 0
    n = len(blob)
    while i < n:
        b = blob[i]; i += 1
        value = b & 0x7F
        if b & 0x80:
            if i >= n:
                raise ValueError("truncated run length")
            p = blob[i]; i += 1
            if p & 0x80 == 0:
                run = p
            elif p & 0xC0 == 0x80:
                run = ((p & 0x3F) << 8) | blob[i]; i += 1
            elif p & 0xE0 == 0xC0:
                run = ((p & 0x1F) << 16) | (blob[i] << 8) | blob[i + 1]; i += 2
            else:
                run = ((p & 0x0F) << 24) | (blob[i] << 16) | (blob[i + 1] << 8) | blob[i + 2]; i += 3
        else:
            run = 1
        out.extend(bytes([value]) * run)
    if len(out) != expected_pixels:
        raise ValueError(f"decoded {len(out)} pixels, expected {expected_pixels}")
    return np.frombuffer(bytes(out), dtype=np.uint8)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('ctb')
    ap.add_argument('--sl1', help='source SL1 to compare against (must be the engine output)')
    ap.add_argument('--layer', type=int, default=None, help='layer index (default: middle)')
    ap.add_argument('--expect', help='expected panel as WxH, e.g. 5760x3600')
    ap.add_argument('--png', help='write a comparison/diagnostic image here')
    args = ap.parse_args()

    c = parse_ctb(args.ctb)
    w, h = c['resolution']
    print(f"CTB {args.ctb}")
    print(f"  version {c['version']} · {w} x {h} px · {c['layer_count']} layers · "
          f"layer height {c['layer_height']} mm · bottom {c['bottom_layers']}")
    print(f"  plate {c['printer_mm'][0]:.2f} x {c['printer_mm'][1]:.2f} x {c['printer_mm'][2]:.1f} mm · "
          f"print time {c['print_time_s']}s · projection {c['projection']} · key {c['encryption_key']}")
    if c['encryption_key'] != 0:
        print("  !! encryption key is not 0 — layer data would be ciphered")
        return 1

    failures = []
    if args.expect:
        ew, eh = (int(v) for v in args.expect.lower().split('x'))
        if (w, h) != (ew, eh):
            failures.append(f"declared {w}x{h} but the panel is {ew}x{eh}")
        else:
            print(f"  resolution matches the panel {ew}x{eh} (NOT its transpose {eh}x{ew})")

    # Every layer must decode to exactly the declared pixel count — this catches a blob written for a
    # different geometry, which is what a transposed raster looks like from the container's side.
    bad = 0
    for i, l in enumerate(c['layers']):
        try:
            decode_rle7(l['blob'], w * h)
        except ValueError as e:
            bad += 1
            if bad <= 3:
                print(f"  !! layer {i}: {e}")
    if bad:
        failures.append(f"{bad} of {c['layer_count']} layers do not decode to {w}x{h}")
    else:
        print(f"  all {c['layer_count']} layer blobs decode to exactly {w}x{h} pixels")

    idx = c['layer_count'] // 2 if args.layer is None else args.layer
    idx = max(0, min(c['layer_count'] - 1, idx))
    mask = decode_rle7(c['layers'][idx]['blob'], w * h).reshape(h, w)
    ink = int((mask > 63).sum())
    print(f"  layer {idx}: z={c['layers'][idx]['z']:.2f} mm exposure={c['layers'][idx]['exposure']}s "
          f"ink={ink} px")

    comparison = None
    if args.sl1:
        z = zipfile.ZipFile(args.sl1)
        names = sorted(n for n in z.namelist() if n.endswith('.png'))
        src = np.array(Image.open(io.BytesIO(z.read(names[idx]))).convert('L'))
        # THE MEASURED TRANSFORM: rotate the engine's raster 90 degrees clockwise into the panel frame.
        expected = np.rot90(src, k=3)
        print(f"  source SL1 layer {idx}: {src.shape[1]}x{src.shape[0]} px"
              f" -> rotated 90deg CW: {expected.shape[1]}x{expected.shape[0]} px")
        if expected.shape != mask.shape:
            failures.append(f"rotated source is {expected.shape[::-1]} but the CTB layer is {mask.shape[::-1]}")
        else:
            same = np.array_equal(expected > 127, mask > 63)
            print(f"  pixel-for-pixel against the rotated source: {'MATCH' if same else 'MISMATCH'}")
            if not same:
                diff = int((np.array(expected > 127) != (mask > 63)).sum())
                failures.append(f"{diff} pixels differ from the rotated source")
            # Also report the control: the UNROTATED raster must NOT match, or the test proves nothing.
            if src.shape == mask.shape and np.array_equal(src > 127, mask > 63):
                failures.append("the UNROTATED engine raster also matches — the comparison is not discriminating")
        comparison = (src, expected, mask)

    if args.png and comparison is not None:
        src, expected, mask = comparison
        tiles = []
        for arr, title in ((src, f"engine SL1 layer {idx}: {src.shape[1]}x{src.shape[0]} (transposed)"),
                           (expected, f"rotated 90deg CW: {expected.shape[1]}x{expected.shape[0]} (panel frame)"),
                           (mask, f"decoded from OUR CTB: {mask.shape[1]}x{mask.shape[0]}")):
            im = Image.fromarray(np.clip(arr.astype(np.int16) * (255 // max(1, int(arr.max() or 1))), 0, 255).astype(np.uint8)).convert('RGB')
            tiles.append((im.resize((im.width // 4, im.height // 4), Image.NEAREST), title))
        diff = Image.fromarray(((expected > 127) != (mask > 63)).astype(np.uint8) * 255).convert('RGB')
        tiles.append((diff.resize((diff.width // 4, diff.height // 4), Image.NEAREST),
                      "difference (black = identical)"))
        pad = 24
        W = sum(t[0].width for t in tiles) + 12 * (len(tiles) - 1)
        H = max(t[0].height for t in tiles) + pad
        canvas = Image.new('RGB', (W, H), (24, 26, 30))
        d = ImageDraw.Draw(canvas)
        x = 0
        for im, title in tiles:
            canvas.paste(im, (x, pad))
            d.text((x + 4, 7), title, fill=(235, 235, 240))
            x += im.width + 12
        canvas.save(args.png)
        print(f"  wrote {args.png}")

    print()
    if failures:
        for f in failures:
            print(f"FAIL: {f}")
        return 1
    print("OK: the CTB is internally consistent" + (" and matches the engine's raster" if args.sl1 else ""))
    return 0


if __name__ == '__main__':
    sys.exit(main())
