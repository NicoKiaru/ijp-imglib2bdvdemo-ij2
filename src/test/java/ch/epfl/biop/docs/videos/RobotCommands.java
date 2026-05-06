package ch.epfl.biop.docs.videos;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTree;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Drives a SciJava command's input-harvester dialog via {@link RobotHelper},
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
 *   <li>{@link String} → {@link JTextField}. Also used for {@code File}
 *       parameters when the value is passed as a {@code String}: types the
 *       path straight into the path field next to "Browse…".</li>
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
 * </ul>
 */
public class RobotCommands {

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
        CommandService cs = FijiCommands.context().service(CommandService.class);
        Future<CommandModule> future = cs.run(cmdClass, true);
        driveDialog(cmdClass, values);
        return future;
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
        FijiCommands.searchAndRun(searchQuery);
        driveDialog(cmdClass, values);
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

    /** Wait for the active dialog, fill each named parameter, click OK. */
    private static <C extends Command> void driveDialog(Class<C> cmdClass, Map<String, Object> values) {
        Map<String, String> fieldToLabel = labelsFor(cmdClass);

        Dialog dialog = RobotHelper.waitForActiveDialog(Timings.FRAME_WAIT_TIMEOUT_MS);
        if (dialog == null) {
            throw new IllegalStateException("No dialog appeared for " + cmdClass.getSimpleName());
        }
        // Let the dialog finish realizing before we touch screen positions.
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        for (Map.Entry<String, Object> e : values.entrySet()) {
            String fieldName = e.getKey();
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
            System.out.println("[RobotCommands] " + fieldName + " (" + label + ") <- " + e.getValue());
            fillWidget(inputContainer, e.getValue());
        }

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
            // The container holds either a JTree (BDV-Playground SourceWidget /
            // SourceListWidget) or a plain JTextField — pick whichever is
            // present. Trees take precedence: a SciJava input panel that
            // contains a tree never also exposes a free-text field.
            List<JTree> trees = findAll(inputContainer, JTree.class);
            if (!trees.isEmpty()) {
                fillTree(trees.get(0), String.valueOf(value));
            } else {
                JTextField tf = firstOf(inputContainer, JTextField.class, "JTextField");
                fillTextField(tf, String.valueOf(value));
            }
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
        RobotHelper.moveTo(x, y);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
        RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /** Pixels from the JCheckBox's left edge to the centre of the box icon (FlatLaf default ≈ 9). */
    public static int CHECKBOX_CLICK_OFFSET_X = 9;

    private static void fillTextField(JTextField tf, String text) {
        Point center = centerOnScreen(tf);
        RobotHelper.moveTo(center);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
        RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
        selectAllAndDelete();
        RobotHelper.type(text);
        RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
    }

    /** Ctrl+A then Delete — clears whatever the field currently contains. */
    private static void selectAllAndDelete() {
        java.awt.Robot r = RobotHelper.robot();
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
        RobotHelper.moveTo(center);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
        RobotHelper.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

        if (!dirPath.isEmpty()) {
            RobotHelper.paste(dirPath);
            RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
            RobotHelper.enter();
            RobotHelper.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
            // Enter navigates into the folder but leaves the path text in
            // the file-name field — wipe it before typing the file name.
            selectAllAndDelete();
        }
        // Filename is short and ASCII — type it for the visible cadence
        // (paths get pasted because of the layout-dependent symbols).
        RobotHelper.type(name);
        RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
        RobotHelper.enter();
        RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Click "Clear list" once. Safe to call on an already-empty list — Swing
     * routes the click to the disabled-button no-op and Robot still moves
     * the cursor visibly, which reads as a deliberate reset gesture.
     */
    private static void clickClearList(AbstractButton clear) {
        Point center = centerOnScreen(clear);
        RobotHelper.moveTo(center);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
        RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
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
            RobotHelper.moveTo(center);
            RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
            RobotHelper.click();
            RobotHelper.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

            if (!dirPath.isEmpty()) {
                RobotHelper.paste(dirPath);
                RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
                RobotHelper.enter();
                RobotHelper.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
                selectAllAndDelete();
            }
            RobotHelper.type(name);
            RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
            RobotHelper.enter();
            RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
        }
    }

    // ===== JTree (SourceWidget / SourceListWidget) ==============================

    /**
     * Drive a JTree-bearing widget. Thin wrapper over {@link RobotTree#selectPath};
     * the actual navigate / expand / select logic lives in {@link RobotTree}
     * so it can also drive the standalone "BDV Sources" frame.
     */
    private static void fillTree(JTree tree, String path) {
        RobotTree.selectPath(tree, path);
    }

    // ===== OK button =============================================================

    private static void clickOk(Dialog dialog) {
        AbstractButton ok = findButton(dialog, "OK");
        if (ok == null) throw new IllegalStateException("No OK button found in dialog");
        Point center = centerOnScreen(ok);
        RobotHelper.moveTo(center);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
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