package ch.epfl.biop.docs.videos;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Currently supported widget types:
 * <ul>
 *   <li>{@link JCheckBox} (boolean parameters)</li>
 *   <li>{@link JTextField} (string parameters)</li>
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
            Component widget = findWidgetForLabel(dialog, label);
            if (widget == null) {
                throw new IllegalStateException("No widget found for parameter '"
                        + fieldName + "' (label: '" + label + "')");
            }
            System.out.println("[RobotCommands] " + fieldName + " (" + label + ") <- " + e.getValue()
                    + " via " + widget.getClass().getSimpleName());
            fillWidget(widget, e.getValue());
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
     * Locates the input widget bound to a SciJava parameter label inside the
     * harvester dialog. SwingInputHarvester emits a {@link JLabel} with the
     * parameter's label text, followed by sibling components that contain the
     * actual widget — so we find the JLabel, then walk forward through its
     * siblings until we reach a non-label container holding an input widget.
     */
    static Component findWidgetForLabel(Dialog dialog, String label) {
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
            Component widget = firstInputWidget(c);
            if (widget != null) return widget;
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

    private static Component firstInputWidget(Component c) {
        if (isInputWidget(c)) return c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                Component w = firstInputWidget(child);
                if (w != null) return w;
            }
        }
        return null;
    }

    private static boolean isInputWidget(Component c) {
        return c instanceof JTextField || c instanceof JCheckBox;
    }

    // ===== Widget driving (Robot) ================================================

    private static void fillWidget(Component widget, Object value) {
        if (widget instanceof JCheckBox) {
            fillCheckBox((JCheckBox) widget, asBoolean(value));
        } else if (widget instanceof JTextField) {
            fillTextField((JTextField) widget, String.valueOf(value));
        } else {
            throw new UnsupportedOperationException(
                    "Widget type not yet supported: " + widget.getClass().getName());
        }
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

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        throw new IllegalArgumentException("Expected Boolean, got " + value.getClass().getSimpleName());
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