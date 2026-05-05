# Tutorial-Video Automation

Tools to drive Fiji's UI from Java using `java.awt.Robot`, so tutorial videos
can be recorded reproducibly (window in a fixed place, cursor visibly easing
onto each target, commands invoked the same way every run).

All code lives under `src/test/java/ch/epfl/biop/docs/videos/`.

## Files

| File | Role |
|---|---|
| `Timings` | Central timing constants + `GLOBAL_SPEED`. One knob to speed every video pace up or down. |
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
- **Locating other windows.** `RobotHelper.findFrame("BDV Sources")` /
  `waitForFrame(title, timeoutMs)` find a visible `Frame` by case-insensitive
  title substring — useful to grab a window opened as a side effect of a
  command, then inspect it.
- **Placing any frame on the target screen.**
  `RobotHelper.placeFrame(frame, dx, dy)` moves a frame to `(dx, dy)`
  *relative to the target screen's top-left*. The single-arg overload uses
  `DEFAULT_FRAME_X` / `DEFAULT_FRAME_Y`. Note: a bare `setLocation(x, y)`
  uses absolute virtual-desktop coordinates and so always lands on screen 0.
- **Minimizing a frame "by hand".** `RobotHelper.minimizeWithMouse(frame)`
  estimates the title-bar minimize button position from the frame's
  `Insets` (Windows 10/11 native title bar) and clicks it visibly — useful
  to look natural on video. Does not work for borderless / undecorated
  frames or non-native LAFs that paint their own title bar.
- **Dragging a frame "by hand".** `RobotHelper.dragFrame(frame, dx, dy)`
  grabs the title bar (away from the right-side buttons) and drags the
  frame to `(dx, dy)` relative to the target screen — same convention as
  `placeFrame`, but visible.
- **Resizing a frame "by hand".** `RobotHelper.resizeFrame(frame, w, h)`
  drags the bottom-right corner so the frame ends up at `w × h`. Requires
  the native resize border (won't work for borderless or fixed-size frames).
- **Single speed knob.** `Timings.GLOBAL_SPEED = 2.0` makes every visible
  motion / pause go twice as fast; `0.5` halves the speed. Mouse motion,
  inter-character typing rhythm, and any `RobotHelper.pause(ms)` are
  scaled. Mechanical timings (click hold, key hold) and polling intervals
  (`Timings.FRAME_POLL_INTERVAL_MS`) stay raw. Use `RobotHelper.rawPause`
  for waits that must NOT be affected by speed.
- **Distance-aware mouse motion.** The single-arg
  `RobotHelper.moveTo(x, y)` derives its duration from the actual cursor →
  target distance via `Timings.MOUSE_SPEED_PX_PER_MS`, then clamps to
  `[MOUSE_MOVE_MIN_MS, MOUSE_MOVE_MAX_MS]`, then applies `GLOBAL_SPEED`.
  So short hops feel snappy and long sweeps stay readable.
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