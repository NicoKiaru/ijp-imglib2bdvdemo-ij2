# Tutorial-Video Automation

Tools to drive Fiji's UI from Java using `java.awt.Robot`, so tutorial videos
can be recorded reproducibly (window in a fixed place, cursor visibly easing
onto each target, commands invoked the same way every run).

All code lives under `src/test/java/ch/epfl/biop/docs/videos/`.

## Files

| File | Role |
|---|---|
| `RobotHelper` | Robot wrapper: target-screen handling, frame placement, sin-eased `moveTo`, click/key/`type(String)`, `pause`. |
| `ComponentInspector` | Recursive AWT/Swing tree walker. Returns a `LinkedHashMap<String, ComponentInfo>` with hierarchical path keys, local bounds and absolute screen positions. Includes `prettyPrint`. |
| `FijiCommands` | Fiji-specific actions: `startFiji()` (boots Fiji + places the IJ frame), `searchAndRun(String query)` (drives the legacy search bar). Holds the `LegacyInjector.preinit()` static block. |
| `InspectMainFrame` | Driver: starts Fiji, dumps the IJ main frame's component tree to stdout. |
| `SearchBarTest` | Driver: starts Fiji, runs `"show bdv play"` via the search bar. |

## What works today

- **Multi-screen positioning.** `RobotHelper.DEFAULT_SCREEN` (default `1` =
  secondary), `DEFAULT_FRAME_X` / `DEFAULT_FRAME_Y` give a reproducible
  origin on the chosen monitor. Edit the defaults once in `RobotHelper` —
  drivers should not override them.
- **Visible mouse motion.** `RobotHelper.moveTo(x, y)` interpolates with a
  sin ease-in / ease-out so the cursor reads naturally on video.
- **Component inspection.** `ComponentInspector.inspect(frame)` returns every
  component reachable from a `Frame`, with class name, label, local bounds
  and (when realized) absolute screen position.
- **Running any Fiji command.** `FijiCommands.searchAndRun("show bdv play")`
  focuses the legacy search bar, types, and presses Enter. This sidesteps
  the AWT-menu limitation (see below) and works for anything reachable via
  Fiji's command finder.

## Known limitations

- **AWT menus have no bounds.** The IJ1 main frame uses `java.awt.MenuBar` /
  `Menu` / `MenuItem`, which extend `MenuComponent`, not `Component` — so
  `ComponentInspector` can list them by label but cannot give a screen
  position. Use `searchAndRun` or keyboard mnemonics instead of trying to
  click menu items.
- **Toolbar tools are painted, not separate components.** `ij.gui.Toolbar`
  exposes one set of bounds for the whole strip; per-tool offsets must be
  derived manually if needed.
- **Typing is ASCII / US layout.** `RobotHelper.type` covers letters, digits,
  space and common symbols. Non-ASCII or non-US layouts are not handled.
- **HiDPI scaling.** Robot uses physical pixels while AWT bounds are
  logical. So far `searchAndRun` lands correctly; if a future click is off
  by a scale factor, suspect this.

## How to run

From the IDE, right-click → `Run 'main()'`:

- `InspectMainFrame.main()` — dump the IJ main-frame tree, useful when
  scripting a new interaction.
- `SearchBarTest.main()` — sanity check that `searchAndRun` opens the
  BDV-Playground tree view.

## Extending

- New high-level Fiji action → add a static method to `FijiCommands`. Prefer
  routing through `searchAndRun` over hunting screen coordinates.
- New low-level Robot primitive (drag, scroll, modifier combo) → add to
  `RobotHelper`.
- Targeting a non-IJ Swing window (BDV, Labkit, dialogs) → call
  `ComponentInspector.inspect(window)` to find the component, then
  `RobotHelper.moveTo(info.screenCenter())` + `click()`.