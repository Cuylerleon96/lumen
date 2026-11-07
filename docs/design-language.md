# Design language — dark, modern, beautiful

Applies to the whole app (ADR 0004). Dark-first (OLED-friendly), modern, restrained. The **3D plate
is the hero**; chrome recedes. Adapted for Jetpack Compose (Material 3) from the house UI-polish rules.

## Principles
- **Dark-first.** Near-black canvas, elevated surfaces by lightness (not heavy shadows). A light theme
  is a later toggle, not the default.
- **Every element earns its place.** No decorative gradients, no emoji, no fake data, no filler
  sections. Restraint reads as premium.
- **One accent, used sparingly.** Violet accent for primary actions, selection, and focus. Semantic
  colors (green/red/amber/blue) only for state.
- **Motion is quiet.** 150–220 ms `ease-out`; spring for sheets. Never bouncy for its own sake.
- **Hierarchy by type + space**, not by boxes and borders. Hairline borders only where needed.

## Color (dark)
| Token | Hex | Use |
|---|---|---|
| `bg` | `#0B0D13` | app background / 3D canvas |
| `surface` | `#13151D` | cards, bars |
| `surfaceVariant` | `#1E2130` | inputs, raised rows |
| `border` | `white @ 6%` | hairline separators |
| `onBg` | `#E8E8ED` | primary text |
| `onBgMuted` | `#8B8DA3` | secondary text/labels |
| `accent` | `#8B5CF6` | primary action, selection, focus glow |
| `success/error/warning/info` | `#34D399 / #F87171 / #FBBF24 / #60A5FA` | state only |

## Type
- Modern grotesque (Inter / Roboto Flex). Tight tracking on headings; **tabular numerals** for all
  settings, dimensions, exposure, times.
- Clear scale: display (screen titles) → title → body → label → caption. Muted color for labels.

## Shape, elevation, spacing
- Corner radius **10–12 dp** (cards/sheets), pills for chips/toggles.
- Elevation via surface lightness + a 1 px hairline border; shadows only on floating layers (FAB,
  bottom sheet) and soft: `0 4dp 12dp rgba(0,0,0,.25)`.
- 8 dp spacing grid; generous padding on the plate, tighter in lists.

## Motion
- Tab/segment indicator: animated underline with a soft accent glow.
- Screen + bottom-sheet transitions: 220 ms ease-out / spring.
- Press states on every tappable: subtle scale (0.98) + surface lighten; ripples on lists.
- Progress: determinate bars/lines; the slice dialog animates percent, not a spinner forever.

## Components
- **Plate canvas**: dark, subtle grid, accent-tinted selection outline, model in neutral material.
- **Cards** (printer/model/settings summaries): `surface`, hairline border, tap → slight lighten +
  lift; chevron affordance.
- **Settings**: a **folded accordion** of groups (collapsed by default) — never a wall of expanded
  controls. Numeric fields use tabular figures + steppers.
- **Bottom bar**: `+ / ⚙ / ▶ Slice` with the Slice action as the accent FAB.
- **Empty states**: one line + one action (e.g. "Open an STL file to start" + **Open STL**).
- **Dialogs**: minimal, left-aligned text, single accent action.

## Anti-slop checklist (enforced in review)
- No gratuitous gradients/glassmorphism; no emoji; no lorem/fake metrics; no rainbow palettes.
- No vague labels ("Insights", "Optimize", "Magic") without a concrete verb.
- Contrast AA minimum on text over dark surfaces.

## Implementation note
Replaces the fork's `OrcaMicroTheme` (`ui/Theme.kt`) with an `AppTheme` exposing these tokens via
Material 3 `ColorScheme` + a custom `ExtendedColors` (accent glow, border, state). Dark is default;
`dynamicColor` off (we want the brand, not the wallpaper).
