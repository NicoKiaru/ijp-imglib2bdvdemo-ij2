# Tutorial-Video Automation

Tools to drive Fiji's UI from Java using `java.awt.Robot`, so tutorial videos
can be recorded reproducibly (window in a fixed place, cursor visibly easing
onto each target, commands invoked the same way every run).

All code lives under `src/test/java/ch/epfl/biop/docs/videos/`, split into subpackages:
- `core/` — low-level Robot primitives and utilities, no Fiji/BDV awareness
- `widgets/` — generic Swing-widget drivers (input harvester, JTree, JPopupMenu)
- `fiji/` — Fiji-side high-level workflows
- `bdv/` — BigDataViewer window operations
- `tests/` — JUnit tests

## Files

| File | Role |
|---|---|
| `core/Timings` | Central timing constants + `GLOBAL_SPEED`. One knob to speed every video pace up or down. |
| `core/Ui` | Robot wrapper: target-screen handling, frame placement, sin-eased `moveTo`, click/key/`type(String)`, `paste(String)` (clipboard-based, layout-independent), `mouseWheel(notches)` + `scrollIntoView(Component)` (mouse-wheel a `JScrollPane` ancestor until a target widget sits at the viewport top), `pause`. Each mouse primitive (`click`, `doubleClick`, `rightClick`, `drag`, `mouseWheel`) also emits an entry to `core/Timeline` so the downstream video pipeline gets a machine-readable timeline of every visible gesture. |
| `core/Timeline` | Single per-session output file, `timeline.json` (v2) — replaces the old `events.json` + `script.md` pair. One `steps[]` entry per recorded clip, in capture order (every step, including pipeline-skipped ones like `boot-fiji`): `index`/`slug`/`clip` (exact mp4 filename), `description` (chapter title — the `Step.begin` narration), `comments[]` (narration sub-steps from `Step.say`, each `{tMs,endMs,text}`), `events[]` (`mouse.click` / `mouse.double_click` / `mouse.right_click` / `mouse.drag` with `endX`/`endY` / `mouse.wheel` with `notches`; screen-absolute `x`/`y`), and an ISO `startedAt`. **Every `tMs`/`endMs` is relative to the first captured frame of that step's own clip** — no session-global clock — so per-clip mapping never drifts. Top-level `recordingBounds` + `version`. `type` is namespaced (`mouse.…`) for future categories. Rewritten on every `Step.end()` and from a JVM shutdown hook. |
| `core/Inspector` | Recursive AWT/Swing tree walker. Returns a `LinkedHashMap<String, ComponentInfo>` with hierarchical path keys, local bounds and absolute screen positions. Includes `prettyPrint`. |
| `widgets/Harvester` | `CommandService`-style runner that drives a SciJava command's harvester dialog by reflecting `@Parameter` labels and filling each widget via Robot. `run(Class, ...)` (direct) and `runViaSearch(Class, query, ...)` (via the IJ search bar). Currently supports `boolean` (JCheckBox), `Number` (JSpinner / slider / scrollbar — drives the inner JFormattedTextField), `String` (JTextField), `String` targeting a `JComboBox` (choices, default style), `String` targeting a `JRadioButton` group (choices, radio styles), `File` / `File[]` (Browse + file-chooser flow), `String` targeting a `JTree` (e.g. BDV-Playground `SwingSourceWidget`), `String` targeting a `JTree` paired with a `JList` (BDV-Playground `SwingSourceSortedListWidget` — drag tree row → list), and `String[]` targeting a flat `JList` (BDV-Playground `SwingBdvHandleListWidget` / `SwingBvvHandleListWidget` — multi-select by display name; both widgets share the same Swing shape and dispatch branch). |
| `widgets/Tree` | Robot primitives for a `JTree` reachable on screen — `selectPath(JTree, String)` (navigate, expand ancestors via double-click, single-click final node) and `rightClickPath(JTree, String)` (same + right-click in place). Shared between the harvester JTree branch in `Harvester` and standalone tree frames like the "BDV Sources" panel. |
| `widgets/Popup` | Robot navigation of an open Swing `JPopupMenu` — `clickPath(String)` finds the active popup via `MenuSelectionManager.defaultManager()`, hovers through intermediate submenus (Swing auto-opens on mouse-enter), and clicks the final menu item. Path syntax matches `Tree`: `">"`-delimited, exact `JMenuItem.getText()`. |
| `fiji/Fiji` | Fiji-specific actions: `startFiji()` (boots Fiji + places the IJ frame, exposes the SciJava `ImageJ`/`Context`), `searchAndRun(String query)` (drives the legacy search bar). Holds the `LegacyInjector.preinit()` static block. |
| `fiji/BdvPlayground` | High-level BDV-Playground workflows on top of `Fiji` + `widgets/*`: `openTree()` runs `"show bdv play"` via the search bar and places the "BDV Sources" frame; `runFromBDVPlaygroundTree(path, CommandClass, args...)` right-clicks a source-tree node, walks the resulting popup to the command's menu path, then drives the harvester dialog with the named args. |
| `bdv/Bdv` | Robot primitives for a live BigDataViewer window. Mixes two location strategies: Swing-component lookup via `BdvHandle` when a component exists (timepoint slider, sources table, range spinners), pixel-aim at the display component's trigger region when only an overlay-painted control exists (card-panel arrow). Methods: `setTimepoint(BdvHandle, int)` — visibly drags `ViewerPanel`'s bottom timepoint `JSlider` (private `sliderTime`, added at `BorderLayout.SOUTH` only when the data has >1 timepoint), throws loudly on single-timepoint data or out-of-range value; `setCardPanelExpanded(BdvHandle, boolean)` — hovers the right-edge of `ViewerPanel.getDisplayComponent()` to materialize the one-touch arrow, pauses for the show animation, then clicks to forward through `SplitPaneOneTouchExpandTrigger` into `SplitPanel.setCollapsed`, idempotent on `splitPanel.isCollapsed()`; `selectSourceInCard(BdvHandle, String)` — clicks the matching row of the Sources card's `SourceTable` (by `NAME_COLUMN` exact match), `scrollRectToVisible` first if needed, loud throw with available names on mismatch; `setDisplayRange(BdvHandle, double, double)` — walks from the unique `SourceTable` up to the enclosing `BorderLayout` table-panel, grabs the SOUTH `ConverterSetupEditPanel`, collects the two `JSpinner`s of the (package-private) `BoundedRangePanel` and fills each via the harvester's `fillNumberField` (click → select-all → paste → Enter). Both source-card methods guard that the split panel is already expanded (loud throw — visible expansion is the caller's gesture) and programmatically ensure the Sources card is expanded. |
| `tests/WidgetsTest` | JUnit 4 test class — boots Fiji once in `@BeforeClass` (also loads `BRAIN_SECTION_3DTILES` for the source tests, opens two empty BDVs with stable titles "BDV alpha" / "BDV beta" via `SourceServices.getBdvDisplayService().getNewBdv()`, and opens two empty BVVs "BVV alpha" / "BVV beta" by running `BvvCreateCommand` through the `CommandService` — its `BvvHandlePostprocessor` registers each handle in the `ObjectService`), then one `@Test` per widget drives a nested `public static class XxxDemo implements Command` fixture via `Harvester.run` and asserts the echoed output. Covers `String`, `boolean`, `File`, `File[]`, all six numeric styles (grouped), all four choice styles (grouped), single `SourceAndConverter`, default `SourceAndConverter[]`, the sorted `SourceAndConverter[]` variant (`style="sorted"`, drag tree → list), `BdvHandle[]` and `BvvHandle[]` (flat-`JList` multi-select, same dispatch), and a 30-boolean `LongFormDemo` that forces the harvester into a `JScrollPane` and exercises `Ui.scrollIntoView` at top / mid / bottom positions. The harvester dialog is the system-under-test; nested fixtures keep each widget's command and assertions co-located. `@After` disposes any leftover `Dialog` so a mid-dialog failure doesn't pollute the next test. |

## What works today

- **Multi-screen positioning.** `Ui.DEFAULT_SCREEN` (default `1` =
  secondary), `DEFAULT_FRAME_X` / `DEFAULT_FRAME_Y` give a reproducible
  origin on the chosen monitor. Edit the defaults once in `Ui` —
  drivers should not override them.
- **Visible mouse motion.** `Ui.moveTo(x, y)` interpolates with a
  sin ease-in / ease-out so the cursor reads naturally on video.
- **Component inspection.** `Inspector.inspect(frame)` returns every
  component reachable from a `Frame`, with class name, label, local bounds
  and (when realized) absolute screen position.
- **Locating other windows.** `Ui.findFrame("BDV Sources")` /
  `waitForFrame(title, timeoutMs)` find a visible `Frame` by case-insensitive
  title substring — useful to grab a window opened as a side effect of a
  command, then inspect it.
- **Locating dialogs.** SciJava command dialogs are `JDialog`s, not `Frame`s,
  so `Frame.getFrames()` misses them. Use `Ui.findDialog(title)` /
  `waitForDialog(title, timeoutMs)` (which iterate `Window.getWindows()`),
  or `waitForActiveDialog(timeoutMs)` to grab the single visible dialog when
  the title isn't predictable. Inspect with `Inspector.inspect(Window)`.
- **Placing any frame on the target screen.**
  `Ui.placeFrame(frame, dx, dy)` moves a frame to `(dx, dy)`
  *relative to the target screen's top-left*. The single-arg overload uses
  `DEFAULT_FRAME_X` / `DEFAULT_FRAME_Y`. Note: a bare `setLocation(x, y)`
  uses absolute virtual-desktop coordinates and so always lands on screen 0.
- **Minimizing a frame "by hand".** `Ui.minimizeWithMouse(frame)`
  estimates the title-bar minimize button position from the frame's
  `Insets` (Windows 10/11 native title bar) and clicks it visibly — useful
  to look natural on video. Does not work for borderless / undecorated
  frames or non-native LAFs that paint their own title bar.
- **Dragging a frame "by hand".** `Ui.dragFrame(frame, dx, dy)`
  grabs the title bar (away from the right-side buttons) and drags the
  frame to `(dx, dy)` relative to the target screen — same convention as
  `placeFrame`, but visible.
- **Resizing a frame "by hand".** `Ui.resizeFrame(frame, w, h)`
  drags the bottom-right corner so the frame ends up at `w × h`. Requires
  the native resize border (won't work for borderless or fixed-size frames).
- **Single speed knob.** `Timings.GLOBAL_SPEED = 2.0` makes every visible
  motion / pause go twice as fast; `0.5` halves the speed. Mouse motion,
  inter-character typing rhythm, and any `Ui.pause(ms)` are
  scaled. Mechanical timings (click hold, key hold) and polling intervals
  (`Timings.FRAME_POLL_INTERVAL_MS`) stay raw. Use `Ui.rawPause`
  for waits that must NOT be affected by speed.
- **Distance-aware mouse motion.** The single-arg
  `Ui.moveTo(x, y)` derives its duration from the actual cursor →
  target distance via `Timings.MOUSE_SPEED_PX_PER_MS`, then clamps to
  `[MOUSE_MOVE_MIN_MS, MOUSE_MOVE_MAX_MS]`, then applies `GLOBAL_SPEED`.
  So short hops feel snappy and long sweeps stay readable.
- **Running any Fiji command.** `Fiji.searchAndRun("show bdv play")`
  focuses the legacy search bar, types, and presses Enter. This sidesteps
  the AWT-menu limitation (see below) and works for anything reachable via
  Fiji's command finder.
- **Filling a SciJava command dialog by parameter name.**
  `Harvester.run(MyCommand.class, "doIt", true, "name", "hello")` mirrors
  `cs.run(...)` ergonomics: invokes the command via the `CommandService`,
  waits for the harvester dialog, reflects each `@Parameter`'s `label()` on
  the class, locates the matching JLabel in the dialog and walks forward
  through its siblings to the input widget, drives it via Robot, then
  clicks **OK**. Returns the `Future<CommandModule>` so you can read
  outputs (`future.get().getOutput("name")`). Currently handles
  `boolean` (`JCheckBox`) and `String` (`JTextField`); other widget types
  throw `UnsupportedOperationException` for now.
- **Same, but launched naturally via the search bar.**
  `Harvester.runViaSearch(MyCommand.class, "robot dem", "doIt", true)`
  reproduces the user gesture of typing into the IJ search box and
  pressing Enter, then drives the resulting dialog the same way. Returns
  `void` — outputs aren't recoverable through the legacy search pipeline,
  so use the direct `run(...)` when you need them. The query should be a
  short, unambiguous prefix of the command's display name; the human
  supervisor checks visually that the right command was launched.
- **Scrolling tall harvester dialogs into view.** When a SciJava command
  has enough parameters that SwingInputHarvester wraps its panel in a
  `JScrollPane`, widgets below the initial viewport were previously
  clicked at off-screen coordinates (the cursor flew below the dialog
  and the click landed on whatever was underneath). `Harvester` now
  calls `Ui.scrollIntoView(inputContainer)` before each widget:
  it walks up to the enclosing `JScrollPane`, hovers the cursor over it
  (skipped if already inside), and visibly mouse-wheels notch-by-notch
  via `Robot.mouseWheel` toward a target viewport position of `widgetTop`
  — bringing the widget to the top (rather than just inside the viewport)
  maximises how many later widgets become reachable in one scroll.
  Three subtleties worth calling out: (1) the target is **clamped to
  `view.height - viewport.height`** (the scrollbar's max), so a widget
  near the very bottom of a tall form can't be reached at the top — it
  lands inside the viewport and the click still hits it; without the
  clamp the loop wheels against a saturated scrollbar, exits late, and
  leaves the widget below the dialog where `getLocationOnScreen()` would
  return an off-dialog y. (2) `Robot.waitForIdle()` is called after each
  notch so the post-event `viewport.getViewRect()` read isn't racing the
  EDT — without it, a short `WHEEL_TICK_PAUSE_MS` lets the
  `now.y == prev.y` "scrollbar at limit" guard mis-fire and exit early.
  (3) A `Timings.PAUSE_AFTER_SCROLL_MS` beat is added at the end of every
  actual scroll so the scroll and the following click read as two
  distinct steps on the recorded video rather than blurring into one
  motion. The down-direction one-notch overshoot back-off (`mouseWheel(-1)`
  if `now.y > targetY`) only fires when the target is `widgetTop`, never
  when it's `maxScroll`. Mouse-wheel only — no programmatic
  `setViewPosition` fallback. Tunables: `Timings.WHEEL_TICK_PAUSE_MS`
  (scaled, default 10), `Timings.PAUSE_AFTER_SCROLL_MS` (scaled, default
  500), `Timings.MAX_WHEEL_TICKS` (safety cap, default 80).
- **`File` parameters via the Browse-then-chooser flow.** Pass a
  `java.io.File` value and `Harvester` clicks the SciJava `Browse`
  button (matched case-insensitively, with a fallback to the only
  button in the input container), waits for the chooser to open, pastes
  the absolute folder path + Enter to navigate, then types the file
  name + Enter to select. The chooser doesn't need the cursor — its
  file-name field has focus by default. Pass a `String` instead of a
  `File` if you'd rather skip the chooser and have the path typed
  straight into the field next to Browse.
- **Layout-independent text injection.** `Ui.paste(String)`
  sets the system clipboard and sends `Ctrl+V`. Use this for paths and
  any other text that mixes characters `type(String)` can't reliably
  reproduce on non-US keyboard layouts (`\\`, `:`, `[`, etc. all
  go through the layout-dependent `Robot.keyPress`, which the Windows
  native peer rejects with "Invalid key code" when no single-key
  mapping exists for the current layout).
- **Visually correct checkbox clicks.** `Harvester` aims at
  `loc.x + CHECKBOX_CLICK_OFFSET_X` (default `9` px) instead of the
  geometric centre — the `JCheckBox` component spans the entire row but
  the visible square sits on the left, so a centred click toggles the box
  but reads strangely on video. Tweak `CHECKBOX_CLICK_OFFSET_X` for
  non-FlatLaf themes.
- **Idempotent checkbox state.** `Harvester` reads `JCheckBox.isSelected()`
  on the live component reference and only clicks when the desired state
  differs from the current one — so passing the existing value is a no-op
  rather than a toggle in the wrong direction.
- **Numeric `@Parameter`s.** Any boxed `Number` value (`Integer`, `Long`,
  `Float`, `Double`, …) drives the `JFormattedTextField` editor inside
  SciJava's `JSpinner`. Covers the plain spinner, the `slider` style and
  the `scroll bar` style alike: SciJava renders all three as a `JSpinner`
  (the slider / scrollbar variants sit next to a sibling spinner that
  shares the same `BoundedRangeModel`), so driving the spinner editor
  also moves the slider / scrollbar visualisation. The driver clicks the
  field, selects all, **pastes** `String.valueOf(value)` (typing fails on
  non-US keyboard layouts because `"."` goes through the layout-dependent
  `Robot.keyPress`), then presses **Enter** — `JFormattedTextField` only
  commits the edit on Enter or focus loss; without it the spinner reverts
  to the previous value when OK is clicked. Locale caveat: the receiving
  formatter follows the JVM default locale, so a `fr_FR` JVM may reject
  `"0.002"`; pre-format with `NumberFormat` at the call site if needed.
- **Choice `@Parameter`s as `JComboBox`.** Default SciJava rendering for a
  `String` parameter with a `choices` list. Pass the `String` value:
  `Harvester` clicks the combo (visible drop-down gesture), reaches
  the popup via `combo.getUI().getAccessibleChild(combo, 0)` (which
  returns the `BasicComboPopup` — popups aren't part of the parent
  dialog's component tree, same caveat as `Popup` for menus), waits
  for the popup's `JList` to be on screen, finds the row whose
  `String.valueOf(model.getElementAt(i))` equals the value, computes its
  screen position from `getCellBounds(row, row)` + `getLocationOnScreen()`
  (rows aren't Swing components and don't appear in
  `Inspector` output, same trick as `Tree`), and clicks.
  Loud throw with the list of available items on mismatch. Note: the
  `listBox` style currently also renders as a `JComboBox` in this
  scijava-ui-swing version, so the same driver covers it; if a future
  version emits a real `JList` we'll need a separate branch.
  No idempotent short-circuit — always shows the drop-down gesture, since
  opening the combo is the primary read for the viewer of a tutorial video.
- **Choice `@Parameter`s as `JRadioButton` group.** `radioButtonHorizontal`
  / `radioButtonVertical` styles render as a `JPanel` of `JRadioButton`s
  (one per choice; `ButtonGroup` is non-visual and doesn't show up in
  `Inspector`). Pass the `String` value: `Harvester` finds
  the radio whose `getText()` equals the value and clicks at
  `RADIO_CLICK_OFFSET_X` (default `9` px from the left edge — same
  reasoning as `CHECKBOX_CLICK_OFFSET_X`, the visible dot sits on the
  left of a row-spanning component). Idempotent: if the target radio is
  already selected, the click is skipped — clicking a selected radio is
  a visible no-op (radios in a `ButtonGroup` don't deselect on re-click)
  and reads as a redundant gesture on video. Loud throw on mismatch.
- **Selecting a node in a SciJava `JTree` widget.** Passing a
  `">"`-delimited path string (e.g. `"Sources>BrainSection-Tiles>tile_001"`)
  to a parameter rendered as a `JTree` (BDV-Playground `SwingSourceWidget`,
  `SwingSourceListWidget`, …) walks the tree model along the path,
  double-clicks each intermediate node to expand it (skipping nodes that
  are already expanded — otherwise a double-click would *collapse* them),
  and single-clicks the final node to select it. Path segments must match
  `DefaultMutableTreeNode.toString()` exactly, including the root —
  loud throw with sibling list on mismatch is preferred over silently
  selecting the wrong source. Dispatch is by container shape: when the
  parameter's input container holds a `JTree`, the tree branch is taken;
  otherwise the value falls through to the `JTextField` branch as before.
  Row pixel positions are computed at runtime from `tree.getRowBounds(row)`
  + `tree.getLocationOnScreen()` (tree rows aren't Swing components and
  don't appear in `Inspector` output), and re-fetched after each
  expansion since intervening rows may have been added or scrolled.
- **Right-clicking a node in the standalone "BDV Sources" frame.** The
  BDV-Playground `SourceTree` frame (auto-shown when sources land in the
  `SourceService`) hosts the same model as the harvester widget. Reach its
  live `JTree` via `bss.tree().getJTree()` and call
  `Tree.rightClickPath(jtree, "Sources>...")` to navigate, single-click
  to select, then right-click in place. The frame's mouse listener builds a
  `SourcePopupMenu` against the current selection and shows it at the cursor
  — so the popup operates on the node that was just selected (or all
  descendant sources, if the path ends at a non-leaf). The path API and
  expansion / select flow are the same as the widget branch
  (`Tree.selectPath`); the only addition is the trailing `rightClick()`
  in place.
- **Navigating an open `JPopupMenu`.** `Popup.clickPath("Viewer>BDV>BDV - Show Sources")`
  finds the currently-open popup via `MenuSelectionManager.defaultManager()`
  (popups aren't part of the parent window's component tree —
  `Inspector` doesn't see them), then walks the path: hover onto
  each intermediate `JMenu` (Swing auto-opens its submenu on mouse-enter),
  wait for the sub-popup to actually become visible, recurse, and click the
  final `JMenuItem`. Hover (not click) is used for intermediates because
  clicking a `JMenu` can <em>toggle</em> its submenu open/closed depending
  on selection state, while hover only ever opens. Segments must match
  `JMenuItem.getText()` exactly; loud throw with the list of available
  items at that level on mismatch. Pairs naturally with
  `Tree.rightClickPath` to drive the BDV Sources popup actions, but
  works for any open Swing popup (menu-bar dropdowns, context menus on
  other components, etc.).
- **Multi-source `SourceAndConverter[]` (default `SwingSourceListWidget`).**
  The same `">"`-delimited path API works unchanged: aim the path at a
  parent node (e.g. `"Sources>BrainSection-Tiles>Channel>ch_1"`) and
  `SwingSourceListWidget.getValue()` recursively collects every descendant
  source under that node — so a single click delivers all sources in a
  group without needing Ctrl+click bookkeeping. Aiming at a leaf still
  works and yields a single-element array. The path validator and the
  expansion / single-click flow are shared with the single-source case;
  no per-widget branching in `Harvester`. Multi-node selection (mixed
  unrelated subtrees) is not yet supported.
- **Sorted `SourceAndConverter[]` (`SwingSourceSortedListWidget`,
  `style="sorted"`).** Same `">"`-delimited path API, but the widget pairs
  a source `JTree` (left) with a destination `JList` (right) and the user
  is expected to drag from one to the other. `Harvester` disambiguates
  by container shape — when the input container holds both a `JTree` and
  a `JList`, it dispatches to the sorted-list driver, which navigates the
  tree (expanding ancestors via the same `Tree.navigateAndExpand` as
  the other tree widgets), computes the screen-centre of the matching row,
  and visibly drags it onto the centre of the destination list. Swing's
  built-in DnD pipeline does the rest: the tree has `setDragEnabled(true)`
  and a `SourceServiceTreeTransferHandler`, the list has a
  `JListTransferHandler`, and a `ListDataListener` pushes the new value
  into the SciJava model on each `intervalAdded`. Single path only — aiming
  at a leaf adds one source, aiming at a parent flattens to all descendant
  sources (handled by the source-tree TransferHandler, same as the plain
  list widget). The list isn't cleared first; if a known starting state
  matters, do the clear gesture explicitly via the widget's "Clear
  selection" button. Multi-path drops (and per-position ordering inside
  the list) aren't yet supported. The drag uses the public
  `Ui.drag(startX, startY, endX, endY)` primitive — same one
  `dragFrame` / `resizeFrame` use — so the smooth move crosses the
  drag-recognition threshold and the DnD pipeline takes over.
- **Multi-`BdvHandle[]` / `BvvHandle[]` (`SwingBdvHandleListWidget` /
  `SwingBvvHandleListWidget`).** Both widgets render as a single flat
  `JList` of every `BdvHandle` / `BvvHandle` registered in the
  `ObjectService`, in `MULTIPLE_INTERVAL_SELECTION` mode. Items display via
  `RenamableBdvHandle.toString()` / `RenamableBvvHandle.toString()`, which
  both return the underlying handle's window title (`BdvHandleHelper.getWindowTitle`
  / `BvvHandleHelper.getWindowTitle`). Because the two widgets share an
  identical Swing shape — flat `JList` inside a `JScrollPane`, no
  surrounding tree or combo — they share the same dispatch branch in
  `Harvester`: pass a `String[]` of window titles and the driver
  resolves each name against the live list model up front (loud throw with
  the available items on a typo, before any visible action — avoids a
  half-driven list on the recorded video), then visibly clicks the first
  matching row and Ctrl+clicks each subsequent row to toggle it into the
  selection. The widget's `ListSelectionListener` pushes
  `getSelectedValuesList()` into the SciJava model after every click, so no
  explicit commit step is needed. Output order is index-ascending (Swing's
  `getSelectedValuesList()` convention) — click order doesn't determine it.
  Dispatch is by value type (`String[]`), so this branch is unambiguous and
  doesn't collide with the JTree+JList sorted-source widget (which takes a
  `String` path). Row pixel positions come from
  `list.getCellBounds(row, row)` + `list.getLocationOnScreen()` (rows aren't
  Swing components and don't show up in `Inspector`). Single
  selection works too — pass a one-element `String[]`. The widget's own
  `JScrollPane` (250 × 80) is not scrolled into view yet — fine for the few
  windows a tutorial video usually shows; if many windows are registered,
  rows below the inner viewport would need a separate scroll pass against
  the widget's scroll pane (the harvester-level `scrollIntoView` only
  addresses the outer dialog scroll pane). Test-fixture note: BDVs are
  created via `SourceServices.getBdvDisplayService().getNewBdv()` (returns
  the handle directly); BVVs are created via `BvvCreateCommand` through the
  `CommandService` (its `BvvHandlePostprocessor` registers the resulting
  `BvvHandle` in the `ObjectService` as a side effect of the run, so the
  widget picks it up).

- **Driving a BDV window's timepoint slider.** `Bdv.setTimepoint(bdv, t)`
  reaches into `bdvHandle.getViewerPanel()`, finds the single `JSlider` child
  (bdv-core's private `sliderTime`, added at `BorderLayout.SOUTH` only for
  multi-timepoint data), computes current and target thumb x positions by
  linear interpolation between `slider.getMinimum()` / `getMaximum()` over the
  slider's screen bounds (with an 8 px thumb-half-width inset), and uses the
  public `Ui.drag` primitive to visibly drag from one to the other.
  No-op when already on the target value; loud throw on a single-timepoint
  dataset or an out-of-range value. Geometry note: the linear-interp model is
  off by a pixel or two at the ends of the track — fine here because the click
  only needs to land on the thumb, and Swing's drag pipeline reads the final
  thumb position from the released x. If a future LAF paints a wider or
  narrower thumb, the `THUMB_HALF_PX` constant absorbs it. The companion
  `BdvSliderDemo` driver exercises this end to end: open LLS7 → show in BDV
  via the source-tree right-click flow → drag the slider to `max`, `0`,
  `max / 2`.
- **Selecting a source in the BDV "Sources" card.**
  `Bdv.selectSourceInCard(bdv, name)` clicks the matching row in
  bdv-core's `SourceTable` (the only `JTable` reachable from the card panel —
  the "Groups" card has a `JTree`, not a table). Row lookup is exact match
  against `SourceTableModel.NAME_COLUMN` (column 0, `String`-typed,
  non-editable — the only column without a side effect on click). The click
  aims at the centre of the cell rectangle from
  `table.getCellRect(row, NAME_COLUMN, true) + table.getLocationOnScreen()`
  (row cells aren't Swing components — same trick as `Tree` and the
  `JComboBox` driver). `JTable.scrollRectToVisible(cell)` is called
  programmatically before the click to bring rows below the viewport into
  view; visible mouse-wheel scrolling of the sources card isn't supported
  yet, but tutorial datasets rarely have enough sources to need it. Loud
  throw with the available names on a typo — beats silently selecting the
  wrong source.
- **Setting a source's display range (min / max).**
  `Bdv.setDisplayRange(bdv, min, max)` drives the two `JSpinner`s of the
  `BoundedRangePanel` inside the Sources card's `ConverterSetupEditPanel` to
  edit the display range of the currently-selected source(s). Call
  `selectSourceInCard` first to choose what the edit applies to. Three
  geometry / API subtleties: (1) `BoundedRangePanel` is package-private in
  `bdv.ui.convertersetupeditor`, so the class isn't importable from outside
  bdv-core — instead we locate it structurally: walk from the unique
  `SourceTable` up three parents (`viewport → scrollPane → tablePanel`,
  matching how `BdvDefaultCards.setup` builds the card body), grab the
  `BorderLayout.SOUTH` child, then collect the `JSpinner`s in that subtree
  (exactly two — min then max in document order). (2) Each spinner is
  filled via a typing-flavored sibling of the harvester's paste-based
  driver — `Harvester.typeNumberField` (public, called from `bdv/Bdv`):
  walk the spinner's editor for its `JFormattedTextField`, click →
  select-all → `Ui.type(String.valueOf(value))` (char-by-char so the
  digits appear one at a time on the recorded video, rather than the value
  materializing instantly as with the paste-based `fillNumberField`) → Enter.
  Enter is load-bearing because a `JFormattedTextField` only commits on
  Enter or focus loss. Layout caveat: each char goes through
  `Robot.keyPress`, which is keyboard-layout-dependent — fine on US / Swiss
  / French layouts for digits and `"."` (the unshifted period key), but if
  a future layout rejects it, swap the call back to `fillNumberField`
  (paste, layout-independent) at the cost of invisible typing on video. (3) Spinner
  order doesn't matter for the final state: `BoundedRange.withMin` /
  `withMax` both expand-to-accommodate (setting `min > current max` drags
  `max` up, and vice versa), so a sequence of two spinner edits converges
  on `(min, max)` regardless of order. The driver sets min then max for
  readability. Both source-card methods (`selectSourceInCard` and
  `setDisplayRange`) guard that the split panel is already expanded — loud
  throw if not, since visible expansion is the caller's tutorial-video
  gesture (`setCardPanelExpanded`), not something to fold into a
  programmatic preroll. The Sources card itself is ensured expanded
  programmatically via `cardPanel.setCardExpanded(DEFAULT_SOURCES_CARD, true)`
  — default is already true, so this is a no-op on a fresh BDV; it covers
  the case where the user collapsed the card manually. `BdvSliderDemo`
  iterates `bdv.getViewerPanel().state().getSources()` and calls both
  methods per source, picking distinct range values so the spinner edits
  read distinctly on video.
- **Expanding / collapsing the BDV card panel.**
  `Bdv.setCardPanelExpanded(bdv, expanded)` reproduces the natural user
  gesture: hover the right edge of `viewerPanel.getDisplayComponent()` to
  materialize the one-touch overlay arrow, wait for the
  `SHOW_EXPAND` / `SHOW_COLLAPSE` animation to read on video, then click.
  Click dispatch goes through bdv-core's `SplitPaneOneTouchExpandTrigger`
  (a `MouseAdapter` on the display component) which forwards into
  `SplitPanel.setCollapsed(!isCollapsed())`. Two reasons pixel-aim — not
  component lookup — is the right approach here, in contrast to the
  timepoint slider: (1) the arrow is painted by
  `SplitPaneOneTouchExpandAnimator` as an `OverlayAnimator` on the viewer's
  display, *not* a Swing component, so there is nothing to point at via
  `Inspector`; (2) the trigger region is defined in pixel terms by
  the animator itself — `x > viewPortWidth - borderWidth` and
  `|viewPortHeight - 2y| < triggerHeight`, where `borderWidth ≈ arrowImg.w + 10`
  and `triggerHeight ≈ arrowImg.h + 10` (both scaled by UI scale) — so aiming
  at `(displayRight - CARD_ARROW_RIGHT_INSET_PX, displayMidY)` with an inset
  of 5 px lands well inside that region at UI scale 1. Idempotent on
  `SplitPanel.isCollapsed()`: no-op when already in the requested state, no
  cursor motion either. `BdvSliderDemo` exercises both expand and collapse
  end to end after the slider drags.

## Known limitations

- **AWT menus have no bounds.** The IJ1 main frame uses `java.awt.MenuBar` /
  `Menu` / `MenuItem`, which extend `MenuComponent`, not `Component` — so
  `Inspector` can list them by label but cannot give a screen
  position. Use `searchAndRun` or keyboard mnemonics instead of trying to
  click menu items.
- **Toolbar tools are painted, not separate components.** `ij.gui.Toolbar`
  exposes one set of bounds for the whole strip; per-tool offsets must be
  derived manually if needed.
- **`type(String)` is ASCII / US layout.** `Ui.type` covers letters,
  digits, space and a wider set of common symbols (now including
  shifted-digit punctuation, brackets, backslash, backquote and tilde),
  but it still goes through `Robot.keyPress` and so depends on the active
  Windows keyboard layout — `\\`, `:`, etc. throw "Invalid key code" on a
  Swiss/French/etc. layout. For any string that may contain those
  characters use `Ui.paste(String)` instead.
- **HiDPI scaling.** Robot uses physical pixels while AWT bounds are
  logical. So far `searchAndRun` lands correctly; if a future click is off
  by a scale factor, suspect this.

## How to run

- `tests/WidgetsTest` — JUnit 4. Run the whole class from the IDE (right-click →
  `Run 'WidgetsTest'`) or a single `@Test` method to exercise one widget.
  Fiji boots once per class.
- `ch.epfl.biop.docs.BdvSliderDemo.main()` — opens the LLS7 dataset, shows it
  in a new BDV window, drags the bottom timepoint slider, expands the card
  panel, walks each source in the Sources card to edit its display range,
  then collapses the card panel. Smoke driver for every `Bdv` primitive
  shipped so far. Lives outside `videos/` because it's a tutorial demo, not
  a unit test.

## Extending

- New high-level Fiji action → add a static method to `Fiji`. Prefer
  routing through `searchAndRun` over hunting screen coordinates.
- New low-level Robot primitive (drag, scroll, modifier combo) → add to
  `Ui`.
- New SciJava-widget support → extend `Harvester.fillWidget`. Same
  locator pattern applies: find JLabel by parameter label, walk to the
  next sibling input container, dispatch by value type (or by container
  shape for `String` values). For widgets without a single clean
  click-target — e.g. the existing `JComboBox` driver, which opens the
  popup and walks the popup `JList` — add a per-type helper rather than
  overloading `centerOnScreen`. Workflow:

  1. **Add a nested fixture.** In `WidgetsTest`, add a
     `public static class XxxDemo implements Command` annotated
     `@Plugin(type = Command.class)` with one `@Parameter` per style
     you want to support. Echo the values into a `String result` output
     so the test can assert end-to-end. SciJava skips the harvester
     dialog when a command has only a single primitive input — if you
     need the dialog to appear (e.g. for a single-`File` widget), add
     a filler `@Parameter` like `boolean extra` to force it.
  2. **Dump the harvester tree.** Throw-away inspection driver: in a
     `main()` (or a one-shot scratch class), boot Fiji, do
     `cs.run(XxxDemo.class, true)` *without* `.get()`, grab the dialog
     with `Ui.waitForActiveDialog(...)`, and print
     `Inspector.prettyPrint(Inspector.inspect(dialog))`.
     That reveals the exact Swing classes SciJava emits for each
     style — copy the relevant subtree path into the driver. Delete
     the inspection driver once the new widget works (`git log`
     preserves the example for next time).
  3. **Implement the driver branch** in `Harvester.fillWidget`,
     dispatching on value type (or container shape if multiple widgets
     share a value type, like `String` for `JTextField` vs `JComboBox`
     vs `JTree`).
  4. **Add an `@Test`** in `WidgetsTest` that runs the fixture via
     `Harvester.run(...)` and asserts the echoed `result`.
- Targeting a non-IJ Swing window (BDV, Labkit, dialogs) → call
  `Inspector.inspect(window)` to find the component, then
  `Ui.moveTo(info.screenCenter())` + `click()`.