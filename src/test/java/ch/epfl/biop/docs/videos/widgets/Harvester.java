package ch.epfl.biop.docs.videos.widgets;

import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Inspector;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Drives a SciJava command's input-harvester dialog via {@link Ui},
 * mimicking {@link CommandService#run(Class, boolean, Object...)} ergonomics.
 *
 * Pass alternating {@code "fieldName", value} pairs the same way you would
 * to {@code cs.run(...)}; this class reflects each {@code @Parameter}'s
 * {@link Parameter#label() label} (falling back to the field name) to locate
 * the corresponding widget in the dialog, then visibly fills it.
 *
 * Currently supported parameter types:
 * <ul>
 *   <li>{@link Boolean} → {@link JCheckBox}.</li>
 *   <li>{@link Number} (any boxed numeric type — {@code Integer},
 *       {@code Long}, {@code Float}, {@code Double}, …) → the
 *       {@link JFormattedTextField} editor inside the SciJava
 *       {@code JSpinner}. Works for plain spinners as well as the
 *       slider / scrollbar variants, which all expose the same spinner
 *       editor; the slider / scrollbar visualisation updates from the
 *       spinner model.</li>
 *   <li>{@link String} → {@link JTextField}. Also used for {@code File}
 *       parameters when the value is passed as a {@code String}: types the
 *       path straight into the path field next to "Browse…".</li>
 *   <li>{@link String} <em>targeting a {@link JComboBox}</em> (default
 *       SciJava rendering for a {@code String} parameter with a
 *       {@code choices} list) → clicks the combo to open its popup, then
 *       clicks the matching item in the popup's {@link JList}. Match is by
 *       {@code String.valueOf(item)} equals the value; loud throw with the
 *       list of available items on mismatch.</li>
 *   <li>{@link String} <em>targeting a group of {@link JRadioButton}s</em>
 *       (SciJava {@code radioButtonHorizontal} / {@code radioButtonVertical}
 *       styles) → clicks the radio whose {@link AbstractButton#getText() text}
 *       equals the value. Idempotent: skips the click if that radio is
 *       already selected.</li>
 *   <li>{@link File} → clicks the {@code Browse} button, then drives the
 *       file chooser by typing the folder path + Enter, then the file name
 *       + Enter. Looks like a real user picking a file.</li>
 *   <li>{@code File[]} → SciJava's mutable file-list widget. Clicks
 *       <em>Add files…</em> once per file, drives the chooser the same way
 *       as the single-file case. Each file appears in the list as it's added.</li>
 *   <li>{@link String} <em>targeting a {@link JTree}</em> (e.g. the
 *       BigDataViewer-Playground {@code SwingSourceWidget} for a single
 *       {@code SourceAndConverter}, or {@code SwingSourceListWidget} for
 *       a {@code SourceAndConverter[]}) → walks the tree model along a
 *       {@code ">"}-delimited path, double-clicks each intermediate node
 *       to expand it, then single-clicks the final node to select it.
 *       Aiming at a leaf selects that single source; aiming at a parent
 *       node selects every descendant source (handled by the widget's
 *       own {@code getValue()} recursion). Path segments must match
 *       {@code DefaultMutableTreeNode.toString()} exactly, including
 *       the root.</li>
 *   <li>{@link String} <em>targeting a {@link JTree} paired with a
 *       {@link JList}</em> (BigDataViewer-Playground
 *       {@code SwingSourceSortedListWidget}, the {@code style="sorted"}
 *       variant of the {@code SourceAndConverter[]} widget) → navigates
 *       the source tree the same way, then drags the matching row onto
 *       the destination list. Single path only; the list isn't cleared
 *       first.</li>
 *   <li>{@code String[]} <em>targeting a flat {@link JList}</em>
 *       (BigDataViewer-Playground {@code SwingBdvHandleListWidget} /
 *       {@code SwingBvvHandleListWidget}, which list every registered
 *       {@code BdvHandle} / {@code BvvHandle} for multi-selection — same
 *       Swing shape, same dispatch) → click + Ctrl+click each named row in
 *       turn. Names are matched against
 *       {@code String.valueOf(model.getElementAt(i))} on the live list
 *       model.</li>
 * </ul>
 */
public class Harvester {

    /**
     * Launches {@code cmdClass} via the {@link CommandService}, waits for the
     * harvester dialog, fills the named parameters via Robot, clicks OK, and
     * returns the {@link Future} from the underlying {@code cs.run} — so you
     * can {@code .get().getOutput("name")} on the result.
     *
     * @param cmdClass the command class to run
     * @param namedInputs alternating field-name / value pairs, e.g.
     *                    {@code "doIt", true, "name", "hello"}
     */
    public static <C extends Command> Future<CommandModule> run(Class<C> cmdClass, Object... namedInputs) {
        Map<String, Object> values = parseValues(namedInputs);
        CommandService cs = Fiji.context().service(CommandService.class);
        Future<CommandModule> future = cs.run(cmdClass, true);
        driveDialog(cmdClass, values, Collections.emptyMap());
        return future;
    }

    public static <C extends Command> void runOpenDialog(Class<C> cmdClass, Object... namedInputs) {
        runOpenDialog(cmdClass, Collections.emptyMap(), namedInputs);
    }

    /**
     * Same as {@link #runOpenDialog(Class, Object...)} but with a per-parameter
     * narration map: just before each widget is driven, if {@code narrations}
     * contains an entry keyed by that parameter's field name, {@link Step#say}
     * fires with the narration text. The {@code tMs} of the resulting
     * {@code comments[]} entry in {@code timeline.json} therefore lines up
     * with the moment the corresponding field is filled — so the downstream
     * video pipeline can correlate the narration to the specific gesture.
     *
     * <p>Narration fires <em>before</em> {@link Ui#scrollIntoView}: when the
     * harvester panel is wrapped in a {@link javax.swing.JScrollPane}, the
     * cursor's scroll-then-click travel toward the widget plays out under
     * the subtitle, which reads as "we're heading to the next thing
     * because…".</p>
     *
     * <p>Parameters without a narration entry are filled silently. Empty
     * map = same behaviour as {@link #runOpenDialog(Class, Object...)}.</p>
     */
    public static <C extends Command> void runOpenDialog(Class<C> cmdClass,
                                                          Map<String, String> narrations,
                                                          Object... namedInputs) {
        Map<String, Object> values = parseValues(namedInputs);
        driveDialog(cmdClass, values, narrations);
    }

    /**
     * Same as {@link #run(Class, Object...)} but launches via the legacy IJ
     * search bar — i.e. reproduces the natural user gesture of typing into
     * Fiji's search box and pressing Enter to find and run the command.
     *
     * Trade-off: because the command is invoked through the legacy search
     * pipeline rather than directly through the {@link CommandService}, no
     * {@link Future} / {@link CommandModule} is returned and outputs are not
     * recoverable from this call. Use the direct {@link #run(Class, Object...)}
     * when you need to read outputs.
     *
     * @param cmdClass the command class — used to resolve {@code @Parameter}
     *                 labels for the dialog
     * @param searchQuery the text to type into the search bar (typically a
     *                    short prefix of the command's display name, e.g.
     *                    {@code "robot dem"})
     * @param namedInputs alternating field-name / value pairs
     */
    public static <C extends Command> void runViaSearch(Class<C> cmdClass, String searchQuery,
                                                        Object... namedInputs) {
        Map<String, Object> values = parseValues(namedInputs);
        Fiji.searchAndRun(searchQuery);
        driveDialog(cmdClass, values, Collections.emptyMap());
    }

    private static Map<String, Object> parseValues(Object[] namedInputs) {
        if (namedInputs.length % 2 != 0) {
            throw new IllegalArgumentException("namedInputs must be alternating name/value pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < namedInputs.length; i += 2) {
            values.put((String) namedInputs[i], namedInputs[i + 1]);
        }
        return values;
    }

    /**
     * True when a parameter value should be skipped entirely in Robot mode —
     * the widget is left at its harvester default. Covers two cases:
     * {@code null} (caller passed an explicit "no value" placeholder), and
     * any empty array <em>except</em> {@code File[]} (an empty {@code File[]}
     * is a deliberate "click Clear list" gesture in the
     * {@code MutableFileListWidget} dispatch).
     */
    private static boolean isRobotNoOpValue(Object value) {
        if (value == null) return true;
        if (value instanceof File[]) return false;
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    /** Wait for the active dialog, fill each named parameter, click OK. */
    private static <C extends Command> void driveDialog(Class<C> cmdClass, Map<String, Object> values,
                                                         Map<String, String> narrations) {
        Map<String, String> fieldToLabel = labelsFor(cmdClass);

        Dialog dialog = Ui.waitForActiveDialog(Timings.FRAME_WAIT_FOR_DIALOG_MS);
        if (dialog == null) {
            // No other parameters - we're done
            return;
        }

        Ui.dragFrame(dialog, dialog.getX(),40);
        Ui.rawPause(1000);

        // Let the dialog finish realizing before we touch screen positions.
        Ui.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        for (Map.Entry<String, Object> e : values.entrySet()) {
            String fieldName = e.getKey();
            Object value = e.getValue();
            // Robot-mode skip: callers often need to pass null / empty arrays
            // in programmatic mode purely to satisfy "all inputs supplied"
            // (so cs.run doesn't pop a harvester dialog for a missing input).
            // Driving the dialog for such values would just leave the widget
            // at its harvester default anyway — and there's no JTextField to
            // fall back to for typed multi-select widgets like
            // SwingBdvHandleListWidget / SwingBvvHandleListWidget — so skip
            // the parameter entirely. File[] is the one exception: an empty
            // File[] is a deliberate "click Clear list" gesture.
            if (isRobotNoOpValue(value)) {
                System.out.println("[Harvester] " + fieldName + " skipped (null or empty array)");
                continue;
            }
            String label = fieldToLabel.get(fieldName);
            if (label == null) {
                throw new IllegalArgumentException("No @Parameter found for field '"
                        + fieldName + "' on " + cmdClass.getSimpleName());
            }
            Container inputContainer = findInputContainerForLabel(dialog, label);
            if (inputContainer == null) {
                throw new IllegalStateException("No input container found for parameter '"
                        + fieldName + "' (label: '" + label + "')");
            }
            System.out.println("[Harvester] " + fieldName + " (" + label + ") <- " + value);
            // Per-parameter narration, fired before the scroll-then-click
            // travel begins — the resulting comments[] tMs in timeline.json
            // marks the moment this field's filling started, so the video
            // pipeline can correlate the subtitle to the specific gesture.
            String narration = narrations.get(fieldName);
            if (narration != null) Step.say(narration);
            // No-op when the harvester panel isn't wrapped in a JScrollPane.
            // When it is, brings the widget to the top of the viewport so it
            // (and as many subsequent widgets as possible) are reachable.
            Ui.scrollIntoView(inputContainer);
            fillWidget(inputContainer, value);
            // Breathing room before the next field's narration + cursor
            // travel — each per-field edit reads as its own beat on video
            // rather than blurring into the next one. Stacks on top of
            // fillWidget's per-click PAUSE_AFTER_CLICK_MS.
            Ui.pause(Timings.PAUSE_AFTER_FIELD_MS);
        }
        Ui.pause(1000);
        clickOk(dialog);
    }

    // ===== Reflection: field name -> dialog label ================================

    private static Map<String, String> labelsFor(Class<?> cls) {
        Map<String, String> map = new HashMap<>();
        for (Field f : cls.getDeclaredFields()) {
            Parameter p = f.getAnnotation(Parameter.class);
            if (p == null) continue;
            String label = p.label();
            if (label == null || label.isEmpty()) label = f.getName();
            map.put(f.getName(), label);
        }
        return map;
    }

    // ===== Widget location =======================================================

    /**
     * Locates the input <em>container</em> bound to a SciJava parameter label
     * inside the harvester dialog. SwingInputHarvester emits a {@link JLabel}
     * with the parameter's label text, followed by a sibling {@link Container}
     * that holds the actual widget(s) — sometimes a single {@link JCheckBox}
     * or {@link JTextField}, sometimes a {@code File} widget combining a path
     * field and a "Browse" button. We return that sibling container so the
     * dispatcher can pick the right component to drive based on the value's
     * Java type.
     */
    static Container findInputContainerForLabel(Dialog dialog, String label) {
        JLabel jl = findLabel(dialog, label);
        if (jl == null) return null;
        Container parent = jl.getParent();
        if (parent == null) return null;
        Component[] siblings = parent.getComponents();
        int idx = -1;
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i] == jl) { idx = i; break; }
        }
        if (idx < 0) return null;
        for (int i = idx + 1; i < siblings.length; i++) {
            Component c = siblings[i];
            if (c instanceof JLabel) break; // hit the next parameter — stop searching
            if (c instanceof Container) return (Container) c;
        }
        return null;
    }

    private static JLabel findLabel(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                if (text.equals(l.getText())) return l;
            }
            if (c instanceof Container) {
                JLabel found = findLabel((Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Recursively collect all components of {@code type} under {@code root}. */
    private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    private static <T extends Component> void collect(Container c, Class<T> type, List<T> out) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) out.add(type.cast(child));
            if (child instanceof Container) collect((Container) child, type, out);
        }
    }

    // ===== Widget driving (Robot) ================================================

    /**
     * Pick a driver based on the value's Java type. The same input container
     * may legitimately hold several widgets (e.g. {@code File} → JTextField +
     * Browse button); the value type tells us which one the caller wants to
     * drive.
     */
    private static void fillWidget(Container inputContainer, Object value) {
        if (value instanceof Boolean) {
            JCheckBox cb = firstOf(inputContainer, JCheckBox.class, "JCheckBox");
            fillCheckBox(cb, (Boolean) value);
        } else if (value instanceof Number) {
            // SciJava renders every numeric @Parameter as a JSpinner whose
            // editor is a JFormattedTextField — true for the plain spinner
            // and for the slider / scrollbar styles too (they sit next to
            // a sibling spinner that shares the same model). Drive the
            // formatted text field; the slider / scrollbar updates itself.
            JFormattedTextField field = firstOf(inputContainer,
                    JFormattedTextField.class, "JFormattedTextField (numeric spinner editor)");
            fillNumberField(field, (Number) value);
        } else if (value instanceof String[]) {
            // SciJava widgets backed by a flat JList (e.g. BDV-Playground
            // SwingBdvHandleListWidget / SwingBvvHandleListWidget, which
            // list every registered BdvHandle / BvvHandle for
            // multi-selection — same Swing shape, same dispatch). Match
            // each name against the live list model's element toString();
            // click + Ctrl+click for additive multi-select.
            JList<?> list = firstOf(inputContainer, JList.class, "JList");
            fillListByNames(list, (String[]) value);
        } else if (value instanceof File[]) {
            AbstractButton addFiles = findAddFilesButton(inputContainer);
            if (addFiles == null) {
                throw new IllegalStateException("No 'Add files…' button found for File[] parameter");
            }
            // Clear any pre-existing entries first so the resulting list
            // contains exactly the files we asked for. Skipped silently if
            // the widget has no Clear button.
            AbstractButton clear = findClearListButton(inputContainer);
            if (clear != null) clickClearList(clear);
            fillFileList(addFiles, (File[]) value);
        } else if (value instanceof File) {
            AbstractButton browse = findBrowseButton(inputContainer);
            if (browse == null) {
                throw new IllegalStateException("No Browse button found for File parameter");
            }
            fillFile(browse, (File) value);
        } else {
            // Dispatch by container shape. Each shape is mutually exclusive
            // in practice — a SciJava input panel for a String @Parameter
            // contains exactly one of: a JComboBox (choices, default style),
            // a group of JRadioButtons (choices, radio styles), a JTree
            // (BDV-Playground source widgets), or a plain JTextField.
            List<JComboBox> combos = findAll(inputContainer, JComboBox.class);
            if (!combos.isEmpty()) {
                fillComboBox(combos.get(0), String.valueOf(value));
                return;
            }
            List<JRadioButton> radios = findAll(inputContainer, JRadioButton.class);
            if (!radios.isEmpty()) {
                fillRadioGroup(radios, String.valueOf(value));
                return;
            }
            List<JTree> trees = findAll(inputContainer, JTree.class);
            if (!trees.isEmpty()) {
                // SwingSourceSortedListWidget pairs a source JTree with a
                // destination JList in the same input container — disambiguate
                // from the plain tree widgets (which have no JList) by the
                // presence of a JList sibling.
                List<JList> lists = findAll(inputContainer, JList.class);
                if (!lists.isEmpty()) {
                    fillSourceSortedList(trees.get(0), lists.get(0), String.valueOf(value));
                    return;
                }
                fillTree(trees.get(0), String.valueOf(value));
                return;
            }
            JTextField tf = firstOf(inputContainer, JTextField.class, "JTextField");
            fillTextField(tf, String.valueOf(value));
        }
    }

    private static <T extends Component> T firstOf(Container c, Class<T> type, String human) {
        List<T> all = findAll(c, type);
        if (all.isEmpty()) {
            throw new IllegalStateException("No " + human + " found in input container");
        }
        return all.get(0);
    }

    /**
     * Find the file-chooser-trigger button. Matches any {@link AbstractButton}
     * whose text contains "browse" (case-insensitive); falls back to the
     * first button found if no explicit match — the SciJava {@code File}
     * widget historically only ever has one button in the input container,
     * so the fallback is safe.
     */
    private static AbstractButton findBrowseButton(Container root) {
        List<AbstractButton> buttons = findAll(root, AbstractButton.class);
        for (AbstractButton b : buttons) {
            String t = b.getText();
            if (t != null && t.toLowerCase().contains("browse")) return b;
        }
        return buttons.isEmpty() ? null : buttons.get(0);
    }

    /**
     * Find the "Add files…" button in a SciJava {@code MutableFileListWidget}.
     * Matches "add file" (singular contained, case-insensitive) — distinct
     * from the sibling "Add folder content…" button. Returns {@code null}
     * if no button matches; the {@code File[]} dispatch reports an error in
     * that case rather than silently picking a wrong button.
     */
    private static AbstractButton findAddFilesButton(Container root) {
        List<AbstractButton> buttons = findAll(root, AbstractButton.class);
        for (AbstractButton b : buttons) {
            String t = b.getText();
            if (t != null && t.toLowerCase().contains("add file")) return b;
        }
        return null;
    }

    /**
     * Find the "Clear list" button in a SciJava {@code MutableFileListWidget}.
     * Returns {@code null} if no matching button exists — the {@code File[]}
     * dispatch then proceeds without clearing, which is harmless on a list
     * that's already empty.
     */
    private static AbstractButton findClearListButton(Container root) {
        List<AbstractButton> buttons = findAll(root, AbstractButton.class);
        for (AbstractButton b : buttons) {
            String t = b.getText();
            if (t != null && t.toLowerCase().contains("clear")) return b;
        }
        return null;
    }

    private static void fillCheckBox(JCheckBox cb, boolean target) {
        if (cb.isSelected() == target) return; // already in the desired state
        // The JCheckBox component spans the whole row but the visible square
        // sits on the left — clicking the geometric centre still toggles it,
        // but lands far from where the user expects to see the click.
        if (!cb.isShowing()) {
            throw new IllegalStateException("JCheckBox is not on screen");
        }
        Point loc = cb.getLocationOnScreen();
        int x = loc.x + CHECKBOX_CLICK_OFFSET_X;
        int y = loc.y + cb.getHeight() / 2;
        Ui.moveTo(x, y);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /** Pixels from the JCheckBox's left edge to the centre of the box icon (FlatLaf default ≈ 9). */
    public static int CHECKBOX_CLICK_OFFSET_X = 9;

    /** Pixels from the JRadioButton's left edge to the centre of the dot icon (FlatLaf default ≈ 9). */
    public static int RADIO_CLICK_OFFSET_X = 9;

    /**
     * Drive a SciJava {@link JComboBox} choice widget: click the combo to
     * open its popup, locate the popup's backing {@link JList} via the
     * combo's UI delegate, find the row whose
     * {@code String.valueOf(model.getElementAt(row))} equals {@code value},
     * compute the row's screen position from
     * {@link JList#getCellBounds getCellBounds} + {@link JList#getLocationOnScreen
     * getLocationOnScreen}, and click. The popup closes on click and the
     * combo's selection updates.
     *
     * The popup itself isn't part of the parent dialog's component tree
     * (similar to the menu / context popups handled in {@link Popup}),
     * so we reach it through {@code combo.getUI().getAccessibleChild(combo, 0)}
     * which returns the {@link BasicComboPopup}. Row positions are read
     * from the live {@link JList} once it is on screen — they aren't Swing
     * components and don't appear in {@link Inspector} output.
     *
     * Always shows the visible gesture (open popup → click item) — no
     * idempotent short-circuit when the value already matches the current
     * selection, since the dropdown opening is the primary read for the
     * viewer of a tutorial video.
     */
    private static void fillComboBox(JComboBox<?> combo, String value) {
        Point center = centerOnScreen(combo);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);

        Object accessible = combo.getUI().getAccessibleChild(combo, 0);
        if (!(accessible instanceof BasicComboPopup)) {
            throw new IllegalStateException("Expected BasicComboPopup from combo UI, got: "
                    + (accessible == null ? "null" : accessible.getClass().getName()));
        }
        JList<?> list = ((BasicComboPopup) accessible).getList();

        long deadline = System.currentTimeMillis() + Timings.FRAME_WAIT_TIMEOUT_MS;
        while (!list.isShowing() && System.currentTimeMillis() < deadline) {
            Ui.rawPause(Timings.FRAME_POLL_INTERVAL_MS);
        }
        if (!list.isShowing()) {
            throw new IllegalStateException("Combo popup did not open in time");
        }

        ListModel<?> model = list.getModel();
        int found = -1;
        for (int i = 0; i < model.getSize(); i++) {
            if (value.equals(String.valueOf(model.getElementAt(i)))) { found = i; break; }
        }
        if (found < 0) {
            StringBuilder available = new StringBuilder();
            for (int i = 0; i < model.getSize(); i++) {
                if (i > 0) available.append(", ");
                available.append('"').append(model.getElementAt(i)).append('"');
            }
            throw new IllegalArgumentException("No item '" + value + "' in combo. Available: ["
                    + available + "]");
        }

        Rectangle bounds = list.getCellBounds(found, found);
        Point listLoc = list.getLocationOnScreen();
        int x = listLoc.x + bounds.x + bounds.width / 2;
        int y = listLoc.y + bounds.y + bounds.height / 2;
        Ui.moveTo(x, y);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Drive a group of {@link JRadioButton}s rendered for a SciJava
     * {@code radioButtonHorizontal} / {@code radioButtonVertical} choice
     * style. Finds the radio whose {@code getText()} equals {@code value}
     * and clicks it (using {@link #RADIO_CLICK_OFFSET_X} so the click lands
     * on the visible dot, same trick as the checkbox flow).
     *
     * Idempotent: if the target radio is already selected, the click is
     * skipped — clicking a selected radio is a visible no-op (radios are
     * part of a {@code ButtonGroup} and don't deselect on re-click) and
     * reads as a redundant gesture on video.
     */
    private static void fillRadioGroup(List<JRadioButton> radios, String value) {
        JRadioButton target = null;
        for (JRadioButton rb : radios) {
            if (value.equals(rb.getText())) { target = rb; break; }
        }
        if (target == null) {
            StringBuilder available = new StringBuilder();
            for (int i = 0; i < radios.size(); i++) {
                if (i > 0) available.append(", ");
                available.append('"').append(radios.get(i).getText()).append('"');
            }
            throw new IllegalArgumentException("No radio button '" + value + "'. Available: ["
                    + available + "]");
        }
        if (target.isSelected()) return;
        if (!target.isShowing()) {
            throw new IllegalStateException("JRadioButton '" + value + "' is not on screen");
        }
        Point loc = target.getLocationOnScreen();
        int x = loc.x + RADIO_CLICK_OFFSET_X;
        int y = loc.y + target.getHeight() / 2;
        Ui.moveTo(x, y);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Drive a SciJava widget whose input container holds a flat
     * {@link JList} — i.e. the BDV-Playground {@code SwingBdvHandleListWidget}
     * (lists every registered {@code BdvHandle} for multi-selection). Each
     * entry in {@code names} is matched against
     * {@code String.valueOf(model.getElementAt(i))} on the live list model
     * (for the BdvHandle widget that's the BDV window title, since
     * {@code RenamableBdvHandle.toString()} returns {@code getWindowTitle}).
     *
     * <p>Visible gesture: plain click on the first row (replaces any prior
     * selection), then Ctrl+click each subsequent row (additive selection
     * under {@link javax.swing.ListSelectionModel#MULTIPLE_INTERVAL_SELECTION}).
     * The widget's {@code ListSelectionListener} pushes
     * {@code getSelectedValuesList()} into the SciJava model after every
     * click — index-ordered, so click order doesn't determine output order.</p>
     *
     * <p>Indices are resolved up front so a typo throws before any visible
     * action — avoids a half-driven list on the recorded video. Available
     * items are listed in the error.</p>
     */
    private static void fillListByNames(JList<?> list, String[] names) {
        if (!list.isShowing()) {
            throw new IllegalStateException("JList is not on screen");
        }
        ListModel<?> model = list.getModel();
        int[] indices = new int[names.length];
        for (int n = 0; n < names.length; n++) {
            int found = -1;
            for (int i = 0; i < model.getSize(); i++) {
                if (names[n].equals(String.valueOf(model.getElementAt(i)))) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                StringBuilder available = new StringBuilder();
                for (int i = 0; i < model.getSize(); i++) {
                    if (i > 0) available.append(", ");
                    available.append('"').append(model.getElementAt(i)).append('"');
                }
                throw new IllegalArgumentException("No item '" + names[n] + "' in JList. Available: ["
                        + available + "]");
            }
            indices[n] = found;
        }

        java.awt.Robot r = Ui.robot();
        Point listLoc = list.getLocationOnScreen();
        for (int k = 0; k < indices.length; k++) {
            Rectangle bounds = list.getCellBounds(indices[k], indices[k]);
            int x = listLoc.x + bounds.x + bounds.width / 2;
            int y = listLoc.y + bounds.y + bounds.height / 2;
            Ui.moveTo(x, y);
            Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
            if (k == 0) {
                Ui.click();
            } else {
                // Ctrl+click → toggle into the existing selection. Plain click
                // would replace it and we'd end up with only the last row.
                r.keyPress(KeyEvent.VK_CONTROL);
                r.delay(Timings.KEY_HOLD_MS);
                Ui.click();
                r.keyRelease(KeyEvent.VK_CONTROL);
            }
            Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        }
    }

    /**
     * Drive a SciJava numeric-spinner's {@link JFormattedTextField}: click,
     * select all, paste {@code String.valueOf(value)}, then Enter.
     *
     * Two non-obvious choices:
     * <ul>
     *   <li><strong>Paste</strong> rather than {@link Ui#type type} —
     *       numeric strings include {@code "."}, which goes through
     *       {@link java.awt.Robot#keyPress Robot.keyPress} and is rejected on
     *       non-US keyboard layouts (Swiss / French / etc.). Same reason the
     *       file flow pastes paths.</li>
     *   <li><strong>Enter at the end</strong> — a {@code JFormattedTextField}
     *       only commits its edit on Enter or focus loss. Without this final
     *       Enter, the spinner reverts to its previous value when OK is
     *       clicked and the command runs with the wrong input.</li>
     * </ul>
     *
     * Locale caveat: {@code String.valueOf(0.002f)} always uses {@code "."}
     * as decimal separator, but the formatter on the receiving field follows
     * the JVM default locale. If a JVM running on a {@code fr_FR} locale
     * rejects the value, pre-format with {@link java.text.NumberFormat} at
     * the call site.
     */
    static void fillNumberField(JFormattedTextField field, Number value) {
        Point center = centerOnScreen(field);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        selectAllAndDelete();
        Ui.paste(String.valueOf(value));
        Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
        Ui.enter();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Visible sibling of {@link #fillNumberField}: types the value
     * character-by-character via {@link Ui#type} instead of pasting
     * it in one shot. Used by the BDV-window automation
     * ({@link Bdv#setDisplayRange}) where the digits appearing one at a
     * time read better on a recorded tutorial than a value materializing
     * instantly.
     *
     * <p>Trade-off vs {@link #fillNumberField}: each character goes through
     * {@link java.awt.Robot#keyPress}, which is keyboard-layout-dependent.
     * For the decimal point in {@code String.valueOf(value)} this is fine on
     * US / Swiss / French layouts (all map {@code "."} to the unshifted period
     * key), but if a future layout rejects it the paste-based
     * {@link #fillNumberField} remains available as a fallback. Same locale
     * caveat about the receiving formatter applies (pre-format with
     * {@link java.text.NumberFormat} at the call site for {@code fr_FR}-style
     * comma-decimal JVMs).</p>
     *
     * <p>Enter at the end is load-bearing for the same reason as
     * {@link #fillNumberField} — a {@code JFormattedTextField} only commits
     * the edit on Enter or focus loss.</p>
     */
    public static void typeNumberField(JFormattedTextField field, Number value) {
        Point center = centerOnScreen(field);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        selectAllAndDelete();
        Ui.type(String.valueOf(value));
        Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
        Ui.enter();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    private static void fillTextField(JTextField tf, String text) {
        Point center = centerOnScreen(tf);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        selectAllAndDelete();
        Ui.type(text);
        Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
    }

    /** Ctrl+A then Delete — clears whatever the field currently contains. */
    private static void selectAllAndDelete() {
        java.awt.Robot r = Ui.robot();
        r.keyPress(KeyEvent.VK_CONTROL);
        r.keyPress(KeyEvent.VK_A);
        r.delay(Timings.KEY_HOLD_MS);
        r.keyRelease(KeyEvent.VK_A);
        r.keyRelease(KeyEvent.VK_CONTROL);
        r.delay(Timings.KEY_HOLD_MS);
        r.keyPress(KeyEvent.VK_DELETE);
        r.delay(Timings.KEY_HOLD_MS);
        r.keyRelease(KeyEvent.VK_DELETE);
    }

    /**
     * Click Browse, then drive the resulting file chooser by pasting the
     * folder path + Enter (navigates into the folder), then pasting the
     * file name + Enter (selects the file and closes the chooser). The
     * mouse is not moved inside the chooser — focus is on the file-name
     * field by default and the paste reaches it directly. The harvester's
     * path JTextField is updated by SciJava once the chooser closes.
     *
     * Pasting (rather than per-character typing) avoids
     * {@code Robot.keyPress}'s keyboard-layout dependency, which otherwise
     * rejects characters like {@code \\} or {@code :} on non-US layouts.
     */
    private static void fillFile(AbstractButton browse, File file) {
        File abs = file.getAbsoluteFile();
        File dir = abs.getParentFile();
        String dirPath = dir == null ? "" : dir.getAbsolutePath();
        String name = abs.getName();

        Point center = centerOnScreen(browse);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

        if (!dirPath.isEmpty()) {
            Ui.paste(dirPath);
            Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
            Ui.enter();
            Ui.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
            // Enter navigates into the folder but leaves the path text in
            // the file-name field — wipe it before typing the file name.
            selectAllAndDelete();
        }
        // Filename is short and ASCII — type it for the visible cadence
        // (paths get pasted because of the layout-dependent symbols).
        Ui.type(name);
        Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
        Ui.enter();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Click "Clear list" once. Safe to call on an already-empty list — Swing
     * routes the click to the disabled-button no-op and Robot still moves
     * the cursor visibly, which reads as a deliberate reset gesture.
     */
    private static void clickClearList(AbstractButton clear) {
        Point center = centerOnScreen(clear);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * For each file: click "Add files…", drive the resulting chooser the
     * same way as a single-file pick. One chooser cycle per file — slower
     * than multi-select-via-quoted-names, but works whether or not the
     * files share a parent directory, and reads naturally on video as the
     * list grows entry by entry.
     */
    private static void fillFileList(AbstractButton addFiles, File[] files) {
        for (File file : files) {
            File abs = file.getAbsoluteFile();
            File dir = abs.getParentFile();
            String dirPath = dir == null ? "" : dir.getAbsolutePath();
            String name = abs.getName();

            Point center = centerOnScreen(addFiles);
            Ui.moveTo(center);
            Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
            Ui.click();
            Ui.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

            if (!dirPath.isEmpty()) {
                Ui.paste(dirPath);
                Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
                Ui.enter();
                Ui.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
                selectAllAndDelete();
            }
            Ui.type(name);
            Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
            Ui.enter();
            Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        }
    }

    // ===== JTree (SourceWidget / SourceListWidget) ==============================

    /**
     * Drive a JTree-bearing widget. Thin wrapper over {@link Tree#selectPath};
     * the actual navigate / expand / select logic lives in {@link Tree}
     * so it can also drive the standalone "BDV Sources" frame.
     */
    private static void fillTree(JTree tree, String path) {
        Tree.selectPath(tree, path);
    }

    /**
     * Drive the {@code SwingSourceSortedListWidget}: navigate the source
     * {@link JTree} to {@code path} (expanding ancestors as needed), then
     * drag the matching row onto the destination {@link JList}. The widget's
     * JTree has {@code setDragEnabled(true)} and a
     * {@code SourceServiceTreeTransferHandler}, so a normal Swing DnD gesture
     * (press → smooth move → release) triggers the transfer; the JList's
     * {@code JListTransferHandler} accepts the drop and a
     * {@code ListDataListener} pushes the new value into the SciJava model.
     *
     * <p>The drop target is the centre of the destination JList. Aiming at a
     * leaf path adds one source; aiming at a parent path adds every descendant
     * source under it (the source-tree {@code TransferHandler} flattens to all
     * descendant {@code SourceAndConverter}s).</p>
     *
     * <p>Single path only — multi-path / per-path-position drops aren't
     * supported. The list isn't cleared first; callers that need a known
     * starting state should do that explicitly (the widget exposes a "Clear
     * selection" button).</p>
     */
    private static void fillSourceSortedList(JTree tree, JList<?> list, String path) {
        javax.swing.tree.TreePath target = Tree.navigateAndExpand(tree, path);
        Point src = Tree.rowScreenCenter(tree, target);
        if (!list.isShowing()) {
            throw new IllegalStateException("Sorted-list drop target JList is not on screen");
        }
        Point listLoc = list.getLocationOnScreen();
        int dstX = listLoc.x + list.getWidth() / 2;
        int dstY = listLoc.y + list.getHeight() / 2;
        Ui.drag(src.x, src.y, dstX, dstY);
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    // ===== OK button =============================================================

    private static void clickOk(Dialog dialog) {
        AbstractButton ok = findButton(dialog, "OK");
        if (ok == null) throw new IllegalStateException("No OK button found in dialog");
        Point center = centerOnScreen(ok);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        // Tutorial PNG: dialog fully filled, cursor on OK, just before the click.
        Step.snapMoment("dialog");
        Ui.click();
        // Beat to let the dialog dismiss and the command's immediate UI
        // side effects (viewer opening, sources appearing, …) materialize
        // before the demo script's next Step.say lands — otherwise the
        // next narration's tMs predates the visible result it describes.
        Ui.pause(Timings.PAUSE_AFTER_DIALOG_OK_MS);
    }

    private static AbstractButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) c;
                if (text.equals(b.getText())) return b;
            }
            if (c instanceof Container) {
                AbstractButton found = findButton((Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Point centerOnScreen(Component c) {
        if (!c.isShowing()) {
            throw new IllegalStateException(c.getClass().getSimpleName() + " is not on screen");
        }
        Point loc = c.getLocationOnScreen();
        return new Point(loc.x + c.getWidth() / 2, loc.y + c.getHeight() / 2);
    }
}