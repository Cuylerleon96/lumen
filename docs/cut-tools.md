# Cut & assembly tools — sectioning large prints + glue-ready tabs

For splitting a model that won't fit (or shouldn't print in one piece) into **parts that reassemble
cleanly** — the pro convenience in OrcaSlicer and Lychee. Evidence mined from OrcaSlicer's
`CutUtils.cpp` / `GLGizmoCut.cpp` (real connector system) and Lychee 8's **Assembly Keys** + Planar Cut.

## 1. Planar cut
- **Plane**: position along an axis (mm) + rotation (X/Y/Z degrees) — or a **draggable handle** in the
  viewport. Type exact values for symmetrical trims ("take 3 mm off the bottom").
- **Cut modes**: *cut into objects* (two separate objects) vs *cut into parts* (still one object);
  keep **upper / lower / both**.
- **Result is watertight** — the cut faces are capped into solid bodies.
- **Multiple cuts**: repeat or a grid of planes (`cut_x`, `cut_grid`) to section into N pieces.
- Apply to the selected object, a selection, or all objects.

## 2. Connectors / Assembly Keys (the key pro feature)
Matching **pegs + sockets** generated along the cut so the parts align and hold for gluing — like
building blocks. Orca's real model:
- **Types** (`CutConnectorType`): **Plug** (peg on one half + matching socket in the other), **Dowel**
  (a separate rod printed on its own, inserted after), **Snap** (snap-fit). Lychee calls these
  "Assembly Keys."
- **Shape**: round / dovetail / square / triangular (Orca `connector_shape`); dovetail resists pull-out.
- **Size & fit**: `connector_size` (diameter) + **`connector_size_tolerance`** = the **glue
  clearance**; `connector_depth_ratio` + `depth_ratio_tolerance`; `connector_angle`.
- **Placement**: connectors sit **on the cut plane** — auto-**distribute by count** OR **manually
  place/drag/delete** (add connector · move connector · delete connector · select-all). Overlap is
  detected and flagged. This is the "configurable and placeable tabs" ask.
- **Output**: upper object, lower object, + dowels as separate objects when used.

## 3. Related conveniences (sectioning workflow)
- **Split to objects / split to parts** — separate a bundled multi-part mesh.
- **Auto-split to fit build volume** — section a too-big model across the plate automatically.
- **Hollowing-aware cut** (Lychee `planarCutHollowingMode`) + **pocket detection** (find internal
  cavities / drain holes) so hollowed parts still drain.
- **Per-piece export & arrange**; export selection as a new scene (Lychee).

## 4. Touch interaction (see `docs/touch-interaction.md`)
- **Plane**: big draggable handle to slide + a **rotate ring** for angle; tap a value → numeric keypad
  (mm / degrees). Live **ghost preview** of both halves.
- **Connectors (placeable tabs)**: **tap the cut plane to add** a connector (raycast + loupe); **drag**
  to move; **tap** to select → sheet (type / shape / size / tolerance / depth / angle); **trash** to
  delete; a **"distribute N"** action for even spacing; **long-press** = context menu; overlap shown
  in a warning color. All the add/move/delete/select-all actions from Orca, on touch.

## 5. Method / open reference
- Geometry: plane–mesh intersection + cap (Manifold/CGAL) — already our `cut`/boolean path.
- Connectors: generate the peg/socket solid (a primitive shaped by type), position on the plane,
  subtract from one half / add to the other (CSG). Dowel = keep as its own object.
- Reference: OrcaSlicer `CutUtils.cpp` (AGPL) — the plug/dowel/snap + tolerance logic. Reimplement
  clean-room against the same behavior.

## 6. Principle
**All of it free and unlocked** — no paywalled "pro" cut/assembly features.
