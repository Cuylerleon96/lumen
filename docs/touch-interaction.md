# Touch interaction design — mouse tools, reinvented for touch

A desktop slicer assumes **hover, right-click, scroll wheel, a pixel-precise cursor, and a keyboard**.
A phone/tablet has **none** of these, and adds its own problem: **your finger covers what you're
aiming at**. This doc maps every mouse idiom to a touch idiom and defines the model we build to.

Grounding: Shapr3D (finger = view, **stylus = create**; two-handed), and mobile-3D HCI research
(finger occlusion is the core constraint → offset cursor / loupe; separate DOF controls; "1.2-hand"
mode separation).

## Core principles
1. **Mode separation is mandatory.** One finger can't orbit *and* paint *and* move. A persistent
   **tool rail** picks the active tool; in a tool mode one finger does the tool, and the camera moves
   to **two-finger gestures** (pan + pinch + twist-to-orbit). In **Navigate** mode, one finger orbits.
   (This is the "pan in view-mode vs move-object in object-mode" separation the research calls for.)
2. **Solve occlusion explicitly.** Every placement/paint tool uses a **loupe** (magnifier inset above
   the finger showing the exact contact) and/or a **dual-finger offset cursor** (second finger offsets
   the cursor to the midpoint) for precision. The action commits on release, so the loupe is always
   visible while aiming.
3. **Stylus is first-class where present.** A pen restores what touch lacks: **hover = preview**,
   **pressure = brush size/strength**, **side button = erase**. Where no pen: **press-and-hold = peek**.
4. **Two-handed by default.** Tool rail sits in bottom thumb-reach; the non-dominant thumb switches
   tools/modes, the dominant hand acts on the model.
5. **Every mouse-only escape hatch gets a visible control.** No Ctrl+Z → Undo/Redo buttons. No
   right-click → long-press. No wheel → pinch. No keyboard → on-screen numeric pad + steppers.

## Global navigation (always available)
- **2-finger drag** = pan · **pinch** = zoom · **2-finger twist** = orbit (fallback when a tool is active).
- **Navigate mode** (default tool): **1-finger drag** = orbit.
- **Double-tap a face** = zoom-to-fit that face. **Orientation cube** (top-right) = axis/iso views.

## The tool rail (bottom, thumb-reach)
`Navigate · Select · Move · Support · Paint · Hollow · Cut · Measure · Text`
Active tool is accent-highlighted; a one-line hint names the gesture. Switching tools never loses
camera state.

## Precision toolkit (shared by all placement tools)
- **Loupe** — magnified inset above the finger on touch-down.
- **Offset cursor** — place a 2nd finger to move the cursor to the midpoint (fine aim), lift to commit.
- **Nudge pad** — after placing, arrow steppers move the last item in 0.1/1 mm steps.
- **Snapping** — supports snap to overhangs/minima; moves snap to grid/faces/edges.
- **Haptics** — a tick on place, a stronger pulse on delete, a warning buzz on invalid.

## Per-tool mapping (mouse → touch)
| Tool | Mouse idiom | Touch idiom |
|---|---|---|
| Orbit / pan / zoom | LMB-drag · MMB · wheel | 2-finger drag / pinch (always); 1-finger orbit in Navigate |
| Select object / face | click | tap; **long-press = context menu** (right-click); drag empty = marquee |
| Move / rotate / scale | drag gizmo arrows | tap → **fat gizmo handles**; or direct: drag = move, 2-finger twist = rotate, pinch = scale |
| Support **add** | click on surface | **tap to place** (raycast + loupe); **drag along model = place a row**; snap to overhang |
| Support **remove** | click / erase | **eraser brush** drag, or tap a support → trash; "clear island" |
| Support **paint** (light/med/heavy) | brush drag | brush drag (size slider + pen pressure); tag chips |
| Support **"no-support" faces** | paint | paint the protection tag |
| **Masking** | brush / lasso | brush paint (protect); **freehand lasso**; box-select; fill/clear; mask-border sheet |
| **Hollow** | panel | sheet (thickness, closing distance) + live preview toggle |
| **Drain hole** place | click on surface | **tap on surface** → hole placed ⟂ to surface (raycast + loupe); drag to reposition |
| **Drain hole** edit | select + params | tap hole → sheet (diameter/depth) + trash |
| **Cut** (planar) | drag plane gizmo | **big draggable handle** to slide + **rotate ring** + keep top/bottom toggles |
| **Measure** | click 2 points | tap A, tap B = distance; tap face = area; 3 taps = angle |
| **Text / emboss** | click + type | tap to place anchor → **on-screen keyboard** → size/depth sliders |
| Hover preview | hover | **stylus hover**; else **press-and-hold peek** + loupe |
| Right-click | RMB | **long-press** |
| Wheel zoom | wheel | **pinch** |
| Numeric entry | keyboard | **on-screen keypad + steppers** (tabular numerals) |
| Undo / redo | Ctrl+Z / Ctrl+Y | **top-bar buttons** + **2-finger tap = undo** |

## Undo, context menu, entry
- **Undo/redo**: persistent top-bar icon buttons (stack of edits), plus a 2-finger tap shortcut.
- **Context menu**: long-press an object/support/hole → sheet with the actions for that selection.
- **Numbers**: any field opens a compact keypad with steppers and unit labels; no typing required.

## Stylus & two-handed
- Pen = creation/edit; finger = view (Shapr3D model). Auto-detect pen vs finger via `MotionEvent`
  tool type; a pen stroke paints/places, a finger stroke orbits/pans.
- Palm rejection while a pen is in range; pressure → brush size/strength; hover → preview.

## Alignment & movement toolset
Movement basics (move/rotate/scale) already exist (FDM `PlacementSheet`); the full arrange/align/orient
set is designed here — matches OrcaSlicer's Arrange / Alignment / Auto-orient / Auto-Drop + Lychee's
Arrange / Auto-orient.

**Per object**
- **Move**: X/Y/Z drag (or gizmo handles) · **drop-to-plate (Auto-Drop)** · **center on plate** ·
  **place-on-face / lay-flat** · nudge pad (0.1/1 mm) · numeric keypad.
- **Rotate**: per-axis drag + rotate ring · **align to face/axis** · free rotate · **auto-orient**
  (minimise overhang/support; deterministic — same result for the same model).
- **Scale**: uniform / per-axis · **% or mm** · **fit-to-build-volume**.
- **Mirror**: X / Y / Z. **Duplicate / array / clone** (rows × cols × layers, spacing).

**Multi-object (plate)**
- **Arrange / auto-arrange**: pack the plate (spacing, allow-rotate-for-arrangement, per-plate or all);
  warn on objects that can't fit. Auto-arrange after clone.
- **Align** selected (X/Y centers, edges) · **distribute** evenly · **center coincidence**.
- **Multi-select**: marquee/box-select, tap-to-add, **group-transform** (move/rotate/scale together).

**Support structures**: FDM **brim** (width/type/ears/gap) · resin **pad/raft** (`pad_enable`, wall
thickness/height/slope, object gap, around-object).

**Snapping**: grid · plate · other objects · faces/edges; collision warnings.

**Touch mapping**: drag = move · 2-finger twist = rotate · pinch = scale · tap → **fat gizmo handles**
for axis-locked transforms · long-press = context menu (Duplicate/Mirror/Drop/Lay-flat/Delete) ·
on-screen keypad for exact mm/°/%.

## Open questions (to test with real hardware)
- Orbit in a tool mode: 2-finger twist vs a temporary "hold to orbit" toggle — which feels better?
- Loupe placement: fixed offset vs follows the finger with a leader line.
- Support painting granularity: per-face tags vs a brush-radius falloff.
- Left-handed mode: mirror the tool rail.
