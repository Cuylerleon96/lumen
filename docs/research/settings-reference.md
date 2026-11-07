# Settings reference — the complete tuning surface

Power-user slicers live or die on their settings surface. These are the **exhaustive key lists** we
must implement against (mined from the engines we already vendor).

## FDM — `orca-settings-keys.txt` (863 keys)
Every OrcaSlicer print/filament/printer setting key. The **Speed/tuning** families are all present,
e.g.: `initial_layer_speed`, `initial_layer_infill_speed`, `initial_layer_acceleration`,
`initial_layer_travel_speed/acceleration/jerk`, `inner_wall_speed/acceleration`,
`outer_wall_speed`, `sparse_infill_speed`, `internal_solid_infill_speed`, `top_surface_speed`,
`gap_infill_speed`, `travel_speed`, `default_acceleration`, `default_jerk`,
`inner_wall_jerk`/`infill_jerk`, `accel_to_decel_enable`/`_factor`,
`filament_max_volumetric_speed`, `filament_adaptive_volumetric_speed`,
`bridge_speed`/`bridge_acceleration`/`bridge_flow`, `enable_overhang_speed` (+ per-overhang),
`fan_min_speed`/`fan_max_speed`/`fan_speedup_time`/`fan_speedup_overhangs`,
`full_fan_speed_layer`, `first_x_layer_fan_speed`, `additional_cooling_fan_speed`,
`filament_flow_ratio`, `*_flow_ratio` (per-feature), `filament_loading/unloading_speed`,
`filament_cooling_initial/final_speed`, `filament_ironing_flow/speed`, `filament_retraction_*`,
`filament_z_hop*`, `extrusion_rate_smoothing_*`, `seam_*`, `*_line_width`, `layer_height`,
`adaptive_layer_height`, `ironing_*`, `spiral_vase`, `fuzzy_skin*`, `post_process`, `machine_*_gcode`,
`nozzle_diameter`, `bed_temperature*`, `chamber_temperature`, `adaptive_bed_mesh*`, `clumping_detection*`,
`timelapse*`, `flush_volume*`, `wipe_tower*`, `prime_tower*`, `*_extruder`, `*_tool`.

## Resin (SLA) — `sla-settings-keys.txt` (73 keys)
`layer_height`, `initial_layer_*`, exposure/motion/burn-layer keys, the full **`support_*`** set
(pillars/base/head/points/tree/enforcers/bridges), `hollowing_*`, `pad_*`, `material_*`
(density, correction XYZ, print speed, type/vendor), `sla_archive_format`, `sla_output_precision`.
Plus the Lychee resin keys already captured in `lychee-feature-surface.md` (normalLayer*/burnLayer*,
TSMC `slowSection*`, tilt, `antialiasV2`, mask*, island*, resinEstimation*, pocketDetection*).

## How this maps to the tracker
- The tracker's **settings editor must render the full schema**, not a subset — these files are the
  exhaustive list to cover. The FDM engine (Orca `libslic3r`) already *defines* all 863 keys, so the
  editor can be **schema-driven** (read the option definitions from the engine → render controls).
- Same for resin once the PrusaSlicer SLA engine lands (it defines the 73 + support/pad/hollowing).
- **Tuning settings (speed/accel/jerk/flow/volumetric/cooling/retraction) are first-class** — captured
  in full in `complete-feature-surface.md` (Speed page) and the key files here.

## Principle
**Nothing hidden behind "Advanced" is dropped.** Modes (Simple/Advanced/Develop) *filter* the UI; the
app still implements and can edit every key. Everything free and unlocked.
