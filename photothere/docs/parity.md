# PhotoThere: parity with the PhotoCraft desktop front end

Status: 26 November 2026, branch `clippy-1-100` at `5ca99a7fc`. Measured against the engine's own
command list (1009 commands, `app/src/jvmTest/resources/engine-commands.json`) and the egui front end
(`crates/ui-egui`, whose numbers are in [`ROADMAP.md`](../../ROADMAP.md) and
[`docs/scorecard.md`](../../docs/scorecard.md)).

**What was run for this document:** `gradle :app:jvmTest --tests *PointerGapsTest* --tests *CoverageTest*`, offline (both suites pass, 3 tests each), and a check that `PointerGapsTest` fails when a built gesture is left in `POINTER_GAPS`. Nothing else. The counts below come from reading the Kotlin sources
(`Workspace.kt`, `AppParts.kt`, `Panels.kt`, `AdjustDialogs.kt`, `Profile.kt`, `dialogs/`) and the
tests' own sources. The rest of `:app:jvmTest` and `cargo test -p loki-ffi` were **not** run, and no
screenshots were looked at. Rows marked *estimate* are judgement, not measurement. Before relying on a
number, run the commands under [How parity is measured](#how-parity-is-measured) and correct this file.

## Summary

<!-- parity-counts tools=69 notYet=11 panels=9 dialogs=40 dialogsCommand=14 dialogsOwn=4 dialogsHidden=22 adjustSpecs=3 gaps=18 gestures=27 -->

PhotoThere shares PhotoCraft's engine, so every engine command is *in* the app; what differs is how
much of the desktop's interface has been rebuilt on top of it.

| | PhotoCraft (egui) | PhotoThere (Compose) |
|---|---|---|
| Engine commands | 1009 | 1009 reachable or named as gestures; 18 pointer gestures are real gaps (see below) |
| Menu items | 664 of 664 live | menus generated from the engine's command registry; not individually hand-built |
| Tools | 56 of 68 live | 58 of 69 declared tools in the rail; 11 held back (`NOT_YET`) |
| Panels | 30 of 35 Window-menu panels | 9 dock panels |
| Dialogs | hand-built, with live previews | 40 translated from egui: 14 bound to a command, 4 run their own command, 22 hidden; 3 hand-wired adjustment dialogs with live preview; the rest open the kit's generated form |
| Platforms | Linux, macOS, Windows, web | JVM desktop, Android; iOS not started |

*Estimate of overall user-facing parity with the desktop app: roughly 25–35%.* By dimension, also
estimates: toolbox ~75%, panels ~30%, dialogs ~25–30%, canvas gestures' depth ~35–40%, workspace,
preferences, themes and localization ~10%. PhotoCraft is itself ~45% "ready for real work" against
Photoshop (ROADMAP.md), so this is parity with an alpha app.

## How parity is measured

Same idea as Loki Text: everything is an engine command, so the first question has a number for an
answer: of the commands the engine has, how many can a person reach?

* `ffi/src/tests.rs::kotlin_command_fixture` writes `engine.commands` to
  `app/src/jvmTest/resources/engine-commands.json` and fails when the engine and the copy differ
  (`LOKI_UPDATE_FIXTURES=1 cargo test -p loki-ffi kotlin_command_fixture` refreshes it).
* `CoverageTest.everyCommandHasAWayToIt` fails when a command is neither listed (menus, all-commands
  list, icon, panel) nor a named pointer gesture, and when `POINTER_GAPS` grows past `maxGaps = 18`.
* `PointerGapsTest` keeps that number honest. `Profile.kt` has two lists: `POINTER_GESTURES` (27
  commands a gesture on the surface issues: strokes, bucket, wand, lasso, quick select…) and
  `POINTER_GAPS` (18 that nothing issues yet). The test fails when a gap's id is a string literal in the
  app's sources (the gesture was built, move it), when a gesture's id is in no source (it was never
  built, or was removed), and when an id is in both. Before this test the generated list held 45 ids,
  27 of them already built, so "1009 of 1009" meant only "reachable or declared a gap". The 18 real
  gaps: `view.setRulerOrigin`, `view.colorSamplers.add/move`, `select.refineEdge`,
  `select.magneticLasso`, `paint.mixerLoad`, `brush.mixer.sample`, `notes.add/set`, `count.add/move/remove`,
  `layer.smartObjects.warp`, `layer.selectInRect`, `layer.pickAt`, `layer.new.frame`,
  `layer.layerStyle.contours.new`, `document.pixel`. The test checks that the id appears in a source, not that the
  gesture works.
* `DialogsWiredTest`: a dialog in the menu must name a real command or run one on OK, else it is hidden.
* `ToolRailTest`: no tool the canvas cannot use is offered, and every hidden tool is one PhotoCraft has.
* `PanelsTest`: every panel call uses a key its command documents, on a command the engine has.
* `AdjustDialogsTest`: the adjustment dialogs' keys and ranges match the params doc.
* Pictures: `LOKI_SHOTS=/tmp/shots gradle :app:jvmTest --tests '*CoverageTest*' --rerun` draws phone
  (390 dp), tablet (800 dp) and desktop (1280 dp) on the real engine.

## What gives a command a way in

| Piece | Where | What it does |
|---|---|---|
| Command form | Loki Kit (`CommandFormDialog`) | Builds fields from a command's params doc. Correct by construction, plain in layout. Every command without a hand-built dialog opens it. |
| All-commands list | Loki Kit | Every command by place, with search. |
| Menus | `Profile.kt`, from the engine's registry | Nine tabs in the desktop's order: File, Edit, Image, Layer, Type, Select, Filter, View, Help. On a phone or tablet they open as a sheet. |
| File extras | `AppParts.kt` | Open, Save, Save As, Export as PNG: the desktop's own commands, on no engine menu. |
| Surface | `EngineSurface.kt`, `ArtSurface.kt` | The engine's composite, with zoom, pan and pinch; marching ants from `loki.selectionOutline`; transform box from `loki.transformFrame`. |

## Tools

69 tools are declared in `Workspace.kt` (PhotoCraft counts 68). `NOT_YET` in `AppParts.kt` keeps 11 out
of the rail until the canvas acts on them; a slot whose every tool is held back disappears.

| State | Tools |
|---|---|
| Held back (`NOT_YET`) | Artboard, Slice, Slice Select, Frame, Ruler, Note, Count, Patch, Content-Aware Move, Custom Shape, Rotate View |
| Built, selection | Rectangular, Elliptical, Single Row, Single Column marquees; Lasso, Polygonal Lasso, Magnetic Lasso; Quick Selection, Object Selection, Magic Wand (modes New/Add/Subtract/Intersect; Shift and Alt, or the Shift and Alt chips on touch) |
| Built, painting and retouch | Brush, Pencil, Mixer Brush, Color Replacement, Eraser, Background Eraser, Magic Eraser, Clone and Pattern Stamp, History and Art History Brush, Spot Healing, Healing, Remove, Red Eye, Blur, Sharpen, Smudge, Dodge, Burn, Sponge, Gradient, Paint Bucket (pressure for size and opacity; pen eraser end erases; side button samples) |
| Built, vector | Pen, Freeform Pen, Curvature Pen, Path Selection, Direct Selection, Add/Delete Anchor Point, Convert Point (the finer anchor edits are listed as unfinished in the README), Rectangle, Ellipse, Triangle, Polygon, Star, Line |
| Built, other | Move with transform box (eight handles and a rotate knob), Crop, Perspective Crop, Eyedropper, Color Sampler, Type (places and edits text in a field), Hand, Zoom |

Not verified here: how complete each built tool is against the desktop's options (the desktop has, for
example, spring-loaded tools, flick panning, tool shortcut editing, corner-radius handles, Path
Operations menus and blend-mode keys; `docs/ui-parity.md` lists them). *Estimate: the Compose canvas
has the basic gesture for most tools and few of those refinements.*

## Panels

| Panel | PhotoCraft | PhotoThere |
|---|---|---|
| Layers (show, select, new, duplicate, delete, blend mode, opacity, reorder) | yes | yes, no filter, groups-in-depth or panel menu verified |
| Properties | yes | yes |
| Channels | yes, with panel menu | yes (basic) |
| History | yes, with snapshots | yes (tap a row to go back) |
| Swatches, Gradients, Styles, Paths, Actions | yes | yes (`Panels.kt`, read from engine state) |
| Color, Character, Paragraph, Adjustments, Navigator, Histogram, Info, Layer Comps | yes | **no** |
| Brushes, Brush Settings, Patterns, Libraries-adjacent and the other remaining Window-menu panels | most exist | **no** |
| Dock drag, regroup, float | partial / floating missing | none (a dock with an icon strip; a sheet over the lower half on a phone) |

PhotoThere: 9 of the desktop's 30. The foreground and background colours with the colour picker,
brush options and tool options live in the options bar and rail instead of a Color panel.

## Dialogs

`dialogs/` holds 40 dialogs translated from egui by `egui2compose.py`, with the desktop's controls
in its order. Parameter names were guessed, then partly fixed (commit `6dfeb74d3`, "send the keys the
command's params doc names"). Current wiring, counted from `TranslatedDialogs.kt`:

| Wiring | Count |
|---|---|
| Bound to a command (`command = …`), which opens that command's generated form | 14 |
| Own OK runs a command | 4 |
| Hidden (no command to run) | 22 |

Hand-wired dialogs with sliders and a live preview (`AdjustDialogs.kt`): Brightness/Contrast,
Hue/Saturation, Levels. The preview applies and undoes through the engine, since it has no preview
command; OK keeps the last application as one history step. The rest of Image › Adjustments
(Curves, Color Balance, Black & White, Photo Filter, Channel Mixer, Gradient Map, Selective Color,
…) opens the generated form. `dialog-commands.json` maps 16 translated dialogs to commands.

Not rebuilt: Camera Raw, Filter Gallery, Select and Mask, Export As / Save for Web, Layer Style
(the dialog exists; behaviour is unchecked), Preferences, New Document presets, Color Range live
preview, Liquify interaction. Dialog starting values, enabling rules and live previews were never
part of the translation.

## What the app has that the desktop does not

* A responsive shell (phone, tablet, desktop) with finger-sized handles and a menu sheet.
* Android with low-latency ink on Android 10+ (front-buffer `InkLayer`) and pen tilt there.
* Palm rejection, and Shift and Alt chips for touch screens.

## Responsive layout

One layout engine on Loki Kit's `CanvasWorkspace`; the desktop's three widths are the checks.

| Feature | Phone (390 dp) | Tablet (~800 dp) | Desktop (1280 dp) |
|---|---|---|---|
| Menus | one button opens a sheet from the bottom | sheet from the side | text menu bar |
| Tools and panels | share a row under the canvas; a panel opens over its lower half | rail and dock | rail left, dock right with icon strip |
| Options bar | scrolls sideways | scrolls | fits |
| Canvas | two-finger pan and pinch, zoom pill | same | Ctrl+wheel zooms, wheel scrolls, zoom pill |

*Not checked for this document:* no screenshots were taken, and the per-width rows come from the README.

## What is left

In order of leverage:

1. **Close the 18 pointer gaps** (list above); each one built moves from `POINTER_GAPS` to
   `POINTER_GESTURES` and lowers `maxGaps` in `CoverageTest`. They are the Ruler, Note, Count and
   colour-sampler tools, Magnetic Lasso, Refine Edge, smart-object warp and a few layer pickers.
2. **Dialog parameter names.** Re-run the "names matched" count from `REPORT.md` (it was 1 of 94 at
   generation time and has since improved by an unmeasured amount) and make `DialogsWiredTest` fail
   on a dialog whose keys the params doc does not name.
3. **Panels.** Color, Character, Paragraph, Navigator, Histogram, Info, Adjustments, Layer Comps.
4. **Typing on the canvas** (Type opens a field; typing in place is not built) and the type controls.
5. **Command state and engine requests** (playbook phase 6): `loki.commandState` reports disabled commands
   but `on` (toggles) is empty, and `loki.uiRequests` is always empty, so commands that ask the front
   end for a picker or dialog do nothing.
6. **Held-back tools:** Frame, Ruler, Note, Count and Rotate View look cheapest; Slice, Artboard,
   Patch, Content-Aware Move and Custom Shape need more.
7. **Desktop-depth interaction:** anchor add/delete/convert, Path Operations, spring-loaded tools,
   pen tilt on the desktop and iOS.
8. **Workspace:** preferences, themes, localization, context menus, drag and drop.
9. **Platforms:** iOS (phase 9); Android signing key, R8 keep rules for JNA, a Save As picker.

## Keeping this document true

The counts in the Summary come from the sources listed at the top, and `ParityDocTest` checks them: it
recomputes each one from the code and fails when the `parity-counts` line, or the Summary row that
repeats it, disagrees. After a change to tools, panels, dialogs or the pointer lists, run the test, then
update that line, the matching table, the date and branch, and say what was run. The
README's status section and `REPORT.md` are older than this file: `REPORT.md` is the generator's
output at the time it ran, and the README predates the Swatches, Gradients, Styles, Paths and Actions
panels and the tool rail.
