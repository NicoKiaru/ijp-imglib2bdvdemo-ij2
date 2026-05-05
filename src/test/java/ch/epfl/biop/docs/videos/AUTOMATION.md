# Tutorial-Video Automation

Tools to drive Fiji's UI from Java using `java.awt.Robot`, so tutorial videos
can be recorded reproducibly (window in a fixed place, cursor visibly easing
onto each target, commands invoked the same way every run).

All code lives under `src/test/java/ch/epfl/biop/docs/videos/`.

## Files

| File | Role |
|---|---|
| `Timings` | Central timing constants + `GLOBAL_SPEED`. One knob to speed every video pace up or down. |
| `RobotHelper` | Robot wrapper: target-screen handling, frame placement, sin-eased `moveTo`, click/key/`type(String)`, `paste(String)` (clipboard-based, layout-independent), `pause`. |
| `ComponentInspector` | Recursive AWT/Swing tree walker. Returns a `LinkedHashMap<String, ComponentInfo>` with hierarchical path keys, local bounds and absolute screen positions. Includes `prettyPrint`. |
| `FijiCommands` | Fiji-specific actions: `startFiji()` (boots Fiji + places the IJ frame, exposes the SciJava `ImageJ`/`Context`), `searchAndRun(String query)` (drives the legacy search bar). Holds the `LegacyInjector.preinit()` static block. |
| `RobotCommands` | `CommandService`-style runner that drives a SciJava command's harvester dialog by reflecting `@Parameter` labels and filling each widget via Robot. `run(Class, ...)` (direct) and `runViaSearch(Class, query, ...)` (via the IJ search bar). Currently supports `boolean` (JCheckBox), `String` (JTextField) and `File` (Browse + file-chooser flow) parameters. |
| `RobotDemoCommand` | Trivial SciJava `Command` with one boolean, one string and one `File` `@Parameter`, plus a string output — fixture for `RobotCommands` tests. |
| `InspectMainFrame` | Driver: starts Fiji, dumps the IJ main frame's component tree to stdout. |
| `SearchBarTest` | Driver: starts Fiji, runs `"show bdv play"` via the search bar. |
| `RobotDemoTest` | Driver: creates a temp file, then runs `RobotDemoCommand` once via `RobotCommands.run` (reads outputs) and once via `RobotCommands.runViaSearch("robot dem", ...)`, exercising boolean + string + File parameters end to end. |

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
- **Locating dialogs.** SciJava command dialogs are `JDialog`s, not `Frame`s,
  so `Frame.getFrames()` misses them. Use `RobotHelper.findDialog(title)` /
  `waitForDialog(title, timeoutMs)` (which iterate `Window.getWindows()`),
  or `waitForActiveDialog(timeoutMs)` to grab the single visible dialog when
  the title isn't predictable. Inspect with `ComponentInspector.inspect(Window)`.
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
- **Filling a SciJava command dialog by parameter name.**
  `RobotCommands.run(MyCommand.class, "doIt", true, "name", "hello")` mirrors
  `cs.run(...)` ergonomics: invokes the command via the `CommandService`,
  waits for the harvester dialog, reflects each `@Parameter`'s `label()` on
  the class, locates the matching JLabel in the dialog and walks forward
  through its siblings to the input widget, drives it via Robot, then
  clicks **OK**. Returns the `Future<CommandModule>` so you can read
  outputs (`future.get().getOutput("name")`). Currently handles
  `boolean` (`JCheckBox`) and `String` (`JTextField`); other widget types
  throw `UnsupportedOperationException` for now.
- **Same, but launched naturally via the search bar.**
  `RobotCommands.runViaSearch(MyCommand.class, "robot dem", "doIt", true)`
  reproduces the user gesture of typing into the IJ search box and
  pressing Enter, then drives the resulting dialog the same way. Returns
  `void` — outputs aren't recoverable through the legacy search pipeline,
  so use the direct `run(...)` when you need them. The query should be a
  short, unambiguous prefix of the command's display name; the human
  supervisor checks visually that the right command was launched.
- **`File` parameters via the Browse-then-chooser flow.** Pass a
  `java.io.File` value and `RobotCommands` clicks the SciJava `Browse`
  button (matched case-insensitively, with a fallback to the only
  button in the input container), waits for the chooser to open, pastes
  the absolute folder path + Enter to navigate, then types the file
  name + Enter to select. The chooser doesn't need the cursor — its
  file-name field has focus by default. Pass a `String` instead of a
  `File` if you'd rather skip the chooser and have the path typed
  straight into the field next to Browse.
- **Layout-independent text injection.** `RobotHelper.paste(String)`
  sets the system clipboard and sends `Ctrl+V`. Use this for paths and
  any other text that mixes characters `type(String)` can't reliably
  reproduce on non-US keyboard layouts (`\\`, `:`, `[`, etc. all
  go through the layout-dependent `Robot.keyPress`, which the Windows
  native peer rejects with "Invalid key code" when no single-key
  mapping exists for the current layout).
- **Visually correct checkbox clicks.** `RobotCommands` aims at
  `loc.x + CHECKBOX_CLICK_OFFSET_X` (default `9` px) instead of the
  geometric centre — the `JCheckBox` component spans the entire row but
  the visible square sits on the left, so a centred click toggles the box
  but reads strangely on video. Tweak `CHECKBOX_CLICK_OFFSET_X` for
  non-FlatLaf themes.
- **Idempotent checkbox state.** `RobotCommands` reads `JCheckBox.isSelected()`
  on the live component reference and only clicks when the desired state
  differs from the current one — so passing the existing value is a no-op
  rather than a toggle in the wrong direction.

## Known limitations

- **AWT menus have no bounds.** The IJ1 main frame uses `java.awt.MenuBar` /
  `Menu` / `MenuItem`, which extend `MenuComponent`, not `Component` — so
  `ComponentInspector` can list them by label but cannot give a screen
  position. Use `searchAndRun` or keyboard mnemonics instead of trying to
  click menu items.
- **Toolbar tools are painted, not separate components.** `ij.gui.Toolbar`
  exposes one set of bounds for the whole strip; per-tool offsets must be
  derived manually if needed.
- **`type(String)` is ASCII / US layout.** `RobotHelper.type` covers letters,
  digits, space and a wider set of common symbols (now including
  shifted-digit punctuation, brackets, backslash, backquote and tilde),
  but it still goes through `Robot.keyPress` and so depends on the active
  Windows keyboard layout — `\\`, `:`, etc. throw "Invalid key code" on a
  Swiss/French/etc. layout. For any string that may contain those
  characters use `RobotHelper.paste(String)` instead.
- **HiDPI scaling.** Robot uses physical pixels while AWT bounds are
  logical. So far `searchAndRun` lands correctly; if a future click is off
  by a scale factor, suspect this.

## How to run

From the IDE, right-click → `Run 'main()'`:

- `InspectMainFrame.main()` — dump the IJ main-frame tree, useful when
  scripting a new interaction.
- `SearchBarTest.main()` — sanity check that `searchAndRun` opens the
  BDV-Playground tree view.
- `RobotDemoTest.main()` — exercises `RobotCommands` end-to-end on the
  `RobotDemoCommand` fixture, both via direct `CommandService` invocation
  (with output assertion) and via the search bar.

## Extending

- New high-level Fiji action → add a static method to `FijiCommands`. Prefer
  routing through `searchAndRun` over hunting screen coordinates.
- New low-level Robot primitive (drag, scroll, modifier combo) → add to
  `RobotHelper`.
- New SciJava-widget support (combo box, slider, file chooser, …) → extend
  `RobotCommands.fillWidget` and `isInputWidget`. Same locator pattern
  applies: find JLabel by parameter label, then the next sibling input
  widget. For widgets without a clean Robot-driveable centre (e.g. combo
  boxes, where you'd need to open the popup and click an item), add a
  per-type helper rather than overloading `centerOnScreen`.
- Targeting a non-IJ Swing window (BDV, Labkit, dialogs) → call
  `ComponentInspector.inspect(window)` to find the component, then
  `RobotHelper.moveTo(info.screenCenter())` + `click()`.