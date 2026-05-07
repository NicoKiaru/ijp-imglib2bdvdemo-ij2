package ch.epfl.biop.docs.videos;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

/**
 * Trivial SciJava command used to investigate the Swing components SciJava
 * renders for numeric {@code @Parameter}s — fixture for adding numeric-widget
 * support to {@link RobotCommands}.
 *
 * Each field exercises a different style so the inspector dump can show the
 * exact Swing class for each variant:
 * <ul>
 *   <li>Unbounded int  → typically a {@code JTextField} or formatted spinner.</li>
 *   <li>Bounded int    → SciJava default for bounded numbers is the spinner
 *       style ({@code JSpinner}). With explicit {@code style = SLIDER_STYLE}
 *       it becomes a {@code JSlider}; with {@code SCROLL_BAR_STYLE} a
 *       {@code JScrollBar}.</li>
 *   <li>Float / double → same widget choices as int.</li>
 * </ul>
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Robot Number Demo Command")
public class RobotNumberDemoCommand implements Command {

    @Parameter(label = "Iterations (unbounded int)")
    int iterations = 10;

    @Parameter(label = "Block size (bounded int)", min = "1", max = "1024")
    int blockSize = 256;

    @Parameter(label = "Threshold (slider)", min = "0", max = "100",
            style = NumberWidget.SLIDER_STYLE)
    int threshold = 50;

    @Parameter(label = "Threads (scroll bar)", min = "1", max = "32",
            style = NumberWidget.SCROLL_BAR_STYLE)
    int threads = 4;

    @Parameter(label = "Regularization (float)")
    float regularization = 0.002f;

    @Parameter(label = "Sigma (bounded double)", min = "0.0", max = "10.0")
    double sigma = 1.5;

    @Parameter(type = ItemIO.OUTPUT)
    String result;

    @Override
    public void run() {
        result = "iterations=" + iterations
                + ", blockSize=" + blockSize
                + ", threshold=" + threshold
                + ", threads=" + threads
                + ", regularization=" + regularization
                + ", sigma=" + sigma;
        System.out.println("[RobotNumberDemoCommand] " + result);
    }
}