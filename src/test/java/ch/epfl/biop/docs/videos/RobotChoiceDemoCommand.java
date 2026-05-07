package ch.epfl.biop.docs.videos;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

/**
 * Trivial SciJava command used to investigate the Swing components SciJava
 * renders for {@code String} {@code @Parameter}s with a {@code choices}
 * list — fixture for adding choice-widget support to {@link RobotCommands}.
 *
 * The same {@code String} field renders very differently depending on
 * {@code style}; this fixture exercises the four canonical variants so the
 * inspector dump can show the exact Swing class(es) for each:
 * <ul>
 *   <li>Default (no style) → {@link javax.swing.JComboBox}.</li>
 *   <li>{@link ChoiceWidget#LIST_BOX_STYLE listBox} → {@link javax.swing.JList}
 *       (typically inside a {@code JScrollPane}).</li>
 *   <li>{@link ChoiceWidget#RADIO_BUTTON_VERTICAL_STYLE radioButtonVertical}
 *       → group of {@link javax.swing.JRadioButton}s, one per choice.</li>
 *   <li>{@link ChoiceWidget#RADIO_BUTTON_HORIZONTAL_STYLE radioButtonHorizontal}
 *       → group of {@link javax.swing.JRadioButton}s, laid out horizontally.</li>
 * </ul>
 *
 * The {@code result} output echoes the picked values so a follow-up test
 * driver can assert correctness end-to-end.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Robot Choice Demo Command")
public class RobotChoiceDemoCommand implements Command {

    @Parameter(label = "Pixel type (default = combo)",
            choices = {"UInt8", "UInt16", "Float", "Double"})
    String pixelType = "UInt16";

    @Parameter(label = "Mode (list box)",
            choices = {"Fast", "Balanced", "Accurate"},
            style = ChoiceWidget.LIST_BOX_STYLE)
    String mode = "Balanced";

    @Parameter(label = "Direction (radio vertical)",
            choices = {"Up", "Down", "Left", "Right"},
            style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE)
    String direction = "Up";

    @Parameter(label = "Quality (radio horizontal)",
            choices = {"Low", "Medium", "High"},
            style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
    String quality = "Medium";

    @Parameter(type = ItemIO.OUTPUT)
    String result;

    @Override
    public void run() {
        result = "pixelType=" + pixelType
                + ", mode=" + mode
                + ", direction=" + direction
                + ", quality=" + quality;
        System.out.println("[RobotChoiceDemoCommand] " + result);
    }
}