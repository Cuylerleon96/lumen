# How HeyGears gets auto-supports so good — and how we'll approach it

Source: HeyGears' own algorithm notice (Blueprint Studio AI Models & Algorithms) + their wiki (Reflex/
BJD procedure). Not marketing — their stated mechanism.

## What they actually do
HeyGears' pre-processing is **three deep-neural-network systems** (their words: "based on deep neural
networks and feature extraction"):
1. **Intelligent 3D mesh classification** — recognise the model's geometric form / workpiece category.
2. **Intelligent 3D mesh orientation** — pick the print posture balancing stability + surface quality.
3. **Intelligent support generation** — detect **overhang features and load-bearing regions**, then
   place **pillars + contact points**; user can fine-tune locally.

Key details that explain the "always well-placed and right-sized":
- **Per-category ONNX models with cloud hot-updates** — supports are tuned per product category (a BJD
  head gets BJD-tuned support), and the models are incrementally improved via cloud + user error reports.
- **Placement is load-aware**: "Lowest Point Detection · Intersection Avoidance · Support Anchors ·
  Fewer Pillars" — anchor at lowest points/weak overhangs, avoid intersections, minimise pillar count.
- **Layered strategy engine (L1/L2/L3)**: L3 = full strategy from **material properties + printer
  parameters + layer thickness + final-result requirements + model features**; L2 = material + printer
  + layer. So parameters are *matched*, not one-size-fits-all.
- **Calibrated parameter sets** per material/use (their BJD example): lift 5 mm · trunk Ø 0.84 · overhang
  35° · lattice XY 4.5 / Z 6 · merge level 2 · anchor dist 2.5 · border anchor 2 · top Ø 0.3 · conn len
  1.5 · contact **sphere** Ø 0.5 · embedded 0.35. Plus a **Strut** tool (connect two surface points),
  selectable contact/strut shapes.

## What we can and can't do
- **Can't copy**: their trained models (closed, cloud). 
- **Can** get very good results by combining:
  1. **Open geometric methods** — island/overhang classification, load/peel-aware sizing, lowest-point
     anchors, intersection avoidance, bracing (PrusaSlicer `sla::SupportTree`, Encrust, DragonFruit).
  2. **Calibrated per-printer/resin presets** — our equivalent of their L2/L3 strategies and per-material
     parameter tables (ship sensible defaults per resin + expose the full param set).
  3. **A model-category hint / orientation strategy** — replicate the *effect* of classification with
     orientation presets (min-support / min-contacts / min-projection / face-up/down) and per-object
     support profiles.
  4. **Optionally, ML later** — we can train our own light model on user feedback (the "fewer pillars /
     right size" target) rather than copying theirs. AGPL-clean.
- **The single biggest lever** is (2): good **defaults per resin + printer** and a **strategy layer**
  (material+printer+layer+goal → parameters). That's what makes theirs feel "right every time" as much
  as the ML.

## Tracker impact
- Supports group (D.3): add **orientation strategies** (min-support / min-contacts / min-projection),
  **struts**, **contact/strut shapes**, **anchor/border-anchor distance**, **merge level**, **lattice
  XY/Z**, **intersection avoidance** — all in `docs/research/slicer-feature-discovery.md` + here.
- Profiles group (E): a **strategy layer** (goal + material + printer → parameters) and per-resin
  calibrated defaults — the HeyGears "right size every time" mechanism, minus the cloud.
