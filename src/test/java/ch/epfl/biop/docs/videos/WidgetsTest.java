package ch.epfl.biop.docs.videos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;
import sc.fiji.bdvpg.command.display.bvv.BvvCreateCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bvv.BvvHandleHelper;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.Dialog;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * End-to-end tests for every widget {@link RobotCommands} can drive. Boots
 * Fiji (and loads the {@code BRAIN_SECTION_3DTILES} dataset for the source
 * tests) once in {@link #setUpClass()}; each {@code @Test} drives one
 * harvester dialog and asserts the echoed output.
 *
 * <p>Each widget has a companion {@code public static} nested {@code Demo}
 * command — fixture and driver stay co-located. Widgets that wrap several
 * SciJava styles ({@link NumberDemo} for the six numeric styles,
 * {@link ChoiceDemo} for the four choice styles) are kept grouped so a
 * style-specific regression still fails the corresponding {@code @Test}
 * without needing one method per style.</p>
 *
 * <p>Out of scope: driving the standalone "BDV Sources" frame's right-click
 * popup (different shape — no harvester widget). That test stays separate.</p>
 */
public class WidgetsTest {

    /** BDV window titles used by {@link #testBdvHandleList()}. */
    private static final String BDV_ALPHA_TITLE = "BDV alpha";
    private static final String BDV_BETA_TITLE = "BDV beta";

    /** BVV window titles used by {@link #testBvvHandleList()}. */
    private static final String BVV_ALPHA_TITLE = "BVV alpha";
    private static final String BVV_BETA_TITLE = "BVV beta";

    @BeforeClass
    public static void setUpClass() throws Exception {
        FijiCommands.startFiji();
        // Eager load: the source tests below need the SourceService populated.
        DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, FijiCommands.context());
        // Two empty BDVs registered in the ObjectService so the BdvHandleList
        // test has something to multi-select. Stable, distinct titles so the
        // assertion isn't sensitive to default-naming changes.
        BdvHandle bdvAlpha = SourceServices.getBdvDisplayService().getNewBdv();
        BdvHandleHelper.setWindowTitle(bdvAlpha, BDV_ALPHA_TITLE);
        BdvHandle bdvBeta = SourceServices.getBdvDisplayService().getNewBdv();
        BdvHandleHelper.setWindowTitle(bdvBeta, BDV_BETA_TITLE);
        // Two empty BVVs for the BvvHandleList test. BvvCreateCommand creates
        // the window and BvvHandlePostprocessor registers it in the
        // ObjectService as a side effect; passing windowtitle skips the
        // harvester.
        CommandService cs = FijiCommands.context().service(CommandService.class);
        cs.run(BvvCreateCommand.class, true, "windowtitle", BVV_ALPHA_TITLE).get();
        cs.run(BvvCreateCommand.class, true, "windowtitle", BVV_BETA_TITLE).get();
    }

    /**
     * Belt-and-braces cleanup so a failed test mid-dialog doesn't leave a modal
     * harvester open and pollute the next {@code @Test}. BDV viewer windows are
     * {@link java.awt.Frame}s, not {@link Dialog}s, so they survive.
     */
    @After
    public void closeLeftoverDialogs() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog && w.isShowing()) {
                w.dispose();
            }
        }
    }

    // ─── String → JTextField ────────────────────────────────────────────────

    @Test
    public void testString() throws Exception {
        String name = "hello robot";
        Future<CommandModule> future = RobotCommands.run(StringDemo.class, "name", name);
        assertEquals("name=" + name, future.get().getOutput("result"));
    }

    @Plugin(type = Command.class)
    public static class StringDemo implements Command {
        @Parameter(label = "Name") String name = "";
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() { result = "name=" + name; }
    }

    // ─── Boolean → JCheckBox ────────────────────────────────────────────────

    @Test
    public void testBoolean() throws Exception {
        Future<CommandModule> future = RobotCommands.run(BooleanDemo.class, "doIt", true);
        assertEquals("doIt=true", future.get().getOutput("result"));
    }

    @Plugin(type = Command.class)
    public static class BooleanDemo implements Command {
        @Parameter(label = "Do it?") boolean doIt = false;
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() { result = "doIt=" + doIt; }
    }

    // ─── File → Browse + chooser ────────────────────────────────────────────

    @Test
    public void testFile() throws Exception {
        File tmp = makeTmp("widgets-file-", ".txt", "hi");
        Future<CommandModule> future = RobotCommands.run(FileDemo.class, "file", tmp);
        assertEquals(tmp, future.get().getInput("file"));
    }

    @Plugin(type = Command.class)
    public static class FileDemo implements Command {
        @Parameter boolean extra;
        @Parameter(label = "A file") File file;
        @Override public void run() {}
    }

    // ─── File[] → Add files chooser flow ────────────────────────────────────

    @Test
    public void testFileList() throws Exception {
        File[] tmps = new File[] {
                makeTmp("widgets-list-a-", ".txt", "a"),
                makeTmp("widgets-list-b-", ".txt", "b"),
                makeTmp("widgets-list-c-", ".txt", "c"),
        };
        Future<CommandModule> future = RobotCommands.run(FileListDemo.class, "files", tmps);
        File[] out = (File[]) future.get().getInput("files");
        assertEquals(tmps.length, out.length);
    }

    @Plugin(type = Command.class)
    public static class FileListDemo implements Command {
        @Parameter boolean extra;
        @Parameter(label = "Many files") File[] files;
        @Override public void run() {}
    }

    // ─── Number → JSpinner / slider / scrollbar (all share the spinner editor) ─

    @Test
    public void testNumber() throws Exception {
        int iterations = 25;
        int blockSize = 128;
        int threshold = 73;
        int threads = 8;
        float regularization = 0.005f;
        double sigma = 2.5;

        Future<CommandModule> future = RobotCommands.run(NumberDemo.class,
                "iterations", iterations,
                "blockSize", blockSize,
                "threshold", threshold,
                "threads", threads,
                "regularization", regularization,
                "sigma", sigma);

        String expected = "iterations=" + iterations
                + ", blockSize=" + blockSize
                + ", threshold=" + threshold
                + ", threads=" + threads
                + ", regularization=" + regularization
                + ", sigma=" + sigma;
        assertEquals(expected, future.get().getOutput("result"));
    }

    @Plugin(type = Command.class)
    public static class NumberDemo implements Command {
        @Parameter(label = "Iterations (unbounded int)") int iterations = 10;
        @Parameter(label = "Block size (bounded int)", min = "1", max = "1024") int blockSize = 256;
        @Parameter(label = "Threshold (slider)", min = "0", max = "100",
                style = NumberWidget.SLIDER_STYLE) int threshold = 50;
        @Parameter(label = "Threads (scroll bar)", min = "1", max = "32",
                style = NumberWidget.SCROLL_BAR_STYLE) int threads = 4;
        @Parameter(label = "Regularization (float)") float regularization = 0.002f;
        @Parameter(label = "Sigma (bounded double)", min = "0.0", max = "10.0") double sigma = 1.5;
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() {
            result = "iterations=" + iterations
                    + ", blockSize=" + blockSize
                    + ", threshold=" + threshold
                    + ", threads=" + threads
                    + ", regularization=" + regularization
                    + ", sigma=" + sigma;
        }
    }

    // ─── Choice → JComboBox / JRadioButton group ───────────────────────────

    @Test
    public void testChoice() throws Exception {
        String pixelType = "Float";
        String mode = "Accurate";
        String direction = "Down";
        String quality = "High";

        Future<CommandModule> future = RobotCommands.run(ChoiceDemo.class,
                "pixelType", pixelType,
                "mode", mode,
                "direction", direction,
                "quality", quality);

        String expected = "pixelType=" + pixelType
                + ", mode=" + mode
                + ", direction=" + direction
                + ", quality=" + quality;
        assertEquals(expected, future.get().getOutput("result"));
    }

    @Plugin(type = Command.class)
    public static class ChoiceDemo implements Command {
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
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() {
            result = "pixelType=" + pixelType
                    + ", mode=" + mode
                    + ", direction=" + direction
                    + ", quality=" + quality;
        }
    }

    // ─── Source → SwingSourceWidget (single-source JTree) ──────────────────

    @Test
    public void testSource() throws Exception {
        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0>All Sources";
        Future<CommandModule> future = RobotCommands.run(SourceDemo.class, "source", pathStr);
        Object result = future.get().getOutput("result");
        assertNotNull(result);
        if ("null".equals(result)) {
            throw new AssertionError("Source widget did not select a source: result=null");
        }
    }

    @Plugin(type = Command.class)
    public static class SourceDemo implements Command {
        @Parameter(label = "Source") SourceAndConverter<?> source;
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() {
            result = source == null ? "null" : source.getSpimSource().getName();
        }
    }

    // ─── SourceList → SwingSourceListWidget (parent selects all descendants) ─

    @Test
    public void testSourceList() throws Exception {
        SourceService bss = FijiCommands.context().service(SourceService.class);
        TreeModel model = bss.tree().getTreeModel();

        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0";
        int expected = countLeavesAt((DefaultMutableTreeNode) model.getRoot(), pathStr.split(">"));

        Future<CommandModule> future = RobotCommands.run(SourceListDemo.class, "sources", pathStr);
        int actual = (Integer) future.get().getOutput("count");
        assertEquals(expected, actual);
    }

    @Plugin(type = Command.class)
    public static class SourceListDemo implements Command {
        @Parameter(label = "Sources") SourceAndConverter<?>[] sources;
        @Parameter(type = ItemIO.OUTPUT) int count;
        @Override public void run() { count = sources == null ? 0 : sources.length; }
    }

    // ─── SourceSortedList → SwingSourceSortedListWidget (drag tree → list) ──

    @Test
    public void testSourceSortedList() throws Exception {
        SourceService bss = FijiCommands.context().service(SourceService.class);
        TreeModel model = bss.tree().getTreeModel();

        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0";
        int expected = countLeavesAt((DefaultMutableTreeNode) model.getRoot(), pathStr.split(">"));

        Future<CommandModule> future = RobotCommands.run(SourceSortedListDemo.class, "sources", pathStr);
        int actual = (Integer) future.get().getOutput("count");
        assertEquals(expected, actual);
    }

    @Plugin(type = Command.class)
    public static class SourceSortedListDemo implements Command {
        @Parameter(label = "Sorted sources", style = "sorted") SourceAndConverter<?>[] sources;
        @Parameter(type = ItemIO.OUTPUT) int count;
        @Override public void run() { count = sources == null ? 0 : sources.length; }
    }

    // ─── BdvHandle[] → SwingBdvHandleListWidget (flat JList, multi-select) ──

    /**
     * Selects both BDV windows registered in {@link #setUpClass()} via the
     * multi-select {@link javax.swing.JList} widget. Asserts that the output
     * array contains both, identified by window title — which is what the
     * widget displays (via {@code RenamableBdvHandle.toString()} →
     * {@link BdvHandleHelper#getWindowTitle}).
     */
    @Test
    public void testBdvHandleList() throws Exception {
        String[] names = new String[] { BDV_ALPHA_TITLE, BDV_BETA_TITLE };
        Future<CommandModule> future = RobotCommands.run(BdvHandleListDemo.class, "bdvs", names);
        CommandModule module = future.get();
        assertEquals(2, (int) (Integer) module.getOutput("count"));
        String titles = (String) module.getOutput("titles");
        // getSelectedValuesList() returns selected items in ascending index
        // order, which here matches ObjectService registration order: alpha
        // first, beta second.
        assertEquals(BDV_ALPHA_TITLE + "," + BDV_BETA_TITLE, titles);
    }

    @Plugin(type = Command.class)
    public static class BdvHandleListDemo implements Command {
        @Parameter(label = "BDVs") BdvHandle[] bdvs;
        @Parameter(type = ItemIO.OUTPUT) int count;
        @Parameter(type = ItemIO.OUTPUT) String titles;
        @Override public void run() {
            count = bdvs == null ? 0 : bdvs.length;
            if (bdvs == null) { titles = ""; return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bdvs.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(BdvHandleHelper.getWindowTitle(bdvs[i]));
            }
            titles = sb.toString();
        }
    }

    // ─── BvvHandle[] → SwingBvvHandleListWidget (same shape as BdvHandle[]) ─

    /**
     * Same flat-{@link javax.swing.JList} multi-select as
     * {@link #testBdvHandleList()}, against the BVV (BigVolumeViewer) twin
     * widget. Shares the {@code String[] → JList} dispatch branch in
     * {@link RobotCommands} — no widget-specific code, just a different
     * value type on the fixture.
     */
    @Test
    public void testBvvHandleList() throws Exception {
        String[] names = new String[] { BVV_ALPHA_TITLE+"_00", BVV_BETA_TITLE+"_00" }; // stupid thingy
        Future<CommandModule> future = RobotCommands.run(BvvHandleListDemo.class, "bvvs", names);
        CommandModule module = future.get();
        assertEquals(2, (int) (Integer) module.getOutput("count"));
        String titles = (String) module.getOutput("titles");
        assertEquals(BVV_ALPHA_TITLE+"_00" + "," + BVV_BETA_TITLE+"_00", titles);
    }

    @Plugin(type = Command.class)
    public static class BvvHandleListDemo implements Command {
        @Parameter(label = "BVVs") BvvHandle[] bvvs;
        @Parameter(type = ItemIO.OUTPUT) int count;
        @Parameter(type = ItemIO.OUTPUT) String titles;
        @Override public void run() {
            count = bvvs == null ? 0 : bvvs.length;
            if (bvvs == null) { titles = ""; return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bvvs.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(BvvHandleHelper.getWindowTitle(bvvs[i]));
            }
            titles = sb.toString();
        }
    }

    // ─── Long form → harvester wrapped in JScrollPane, scroll required ─────

    /**
     * Smoke-tests {@link RobotHelper#scrollIntoView}: a command with enough
     * boolean parameters that {@code SwingInputHarvester} wraps its panel in
     * a {@link javax.swing.JScrollPane}. Setting widgets at the top, the
     * middle and the bottom exercises three regimes — already visible
     * (no-op), needs a partial scroll, needs a further scroll. If the
     * scroll path regresses, the click for {@code b15} / {@code b30} lands
     * off-screen below the dialog, the box stays at its default
     * {@code false}, and the assertion fails.
     *
     * <p>30 parameters reliably overflows on common CI / dev monitors
     * (~25 px / row in FlatLaf → ~750 px, well past the harvester's
     * preferred-size cap). On an unusually tall display the dialog may
     * fit without scrolling — the test still passes (every click lands)
     * but does not exercise the scroll path. Bump the parameter count
     * if that becomes a problem.</p>
     */
    @Test
    public void testScrollIntoView() throws Exception {
        Future<CommandModule> future = RobotCommands.run(LongFormDemo.class,
                "b01", true,
                "b15", true,
                "b30", true);
        assertEquals("b01=true b15=true b30=true", future.get().getOutput("result"));
    }

    @Plugin(type = Command.class)
    public static class LongFormDemo implements Command {
        @Parameter(label = "Boolean 01", persist = false) boolean b01;
        @Parameter(label = "Boolean 02") boolean b02;
        @Parameter(label = "Boolean 03") boolean b03;
        @Parameter(label = "Boolean 04") boolean b04;
        @Parameter(label = "Boolean 05") boolean b05;
        @Parameter(label = "Boolean 06") boolean b06;
        @Parameter(label = "Boolean 07") boolean b07;
        @Parameter(label = "Boolean 08") boolean b08;
        @Parameter(label = "Boolean 09") boolean b09;
        @Parameter(label = "Boolean 10") boolean b10;
        @Parameter(label = "Boolean 11") boolean b11;
        @Parameter(label = "Boolean 12") boolean b12;
        @Parameter(label = "Boolean 13") boolean b13;
        @Parameter(label = "Boolean 14") boolean b14;
        @Parameter(label = "Boolean 15", persist = false) boolean b15;
        @Parameter(label = "Boolean 16") boolean b16;
        @Parameter(label = "Boolean 17") boolean b17;
        @Parameter(label = "Boolean 18") boolean b18;
        @Parameter(label = "Boolean 19") boolean b19;
        @Parameter(label = "Boolean 20") boolean b20;
        @Parameter(label = "Boolean 21") boolean b21;
        @Parameter(label = "Boolean 22") boolean b22;
        @Parameter(label = "Boolean 23") boolean b23;
        @Parameter(label = "Boolean 24") boolean b24;
        @Parameter(label = "Boolean 25") boolean b25;
        @Parameter(label = "Boolean 26") boolean b26;
        @Parameter(label = "Boolean 27") boolean b27;
        @Parameter(label = "Boolean 28") boolean b28;
        @Parameter(label = "Boolean 29") boolean b29;
        @Parameter(label = "Boolean 30", persist = false) boolean b30;
        @Parameter(type = ItemIO.OUTPUT) String result;
        @Override public void run() {
            result = "b01=" + b01 + " b15=" + b15 + " b30=" + b30;
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static File makeTmp(String prefix, String suffix, String content) throws Exception {
        File f = File.createTempFile(prefix, suffix);
        f.deleteOnExit();
        Files.write(f.toPath(), content.getBytes());
        return f;
    }

    private static int countLeavesAt(DefaultMutableTreeNode root, String[] parts) {
        if (!parts[0].equals(root.toString())) {
            throw new IllegalArgumentException("Tree root mismatch: expected '"
                    + parts[0] + "', got '" + root.toString() + "'");
        }
        DefaultMutableTreeNode node = root;
        for (int i = 1; i < parts.length; i++) {
            DefaultMutableTreeNode child = null;
            for (int c = 0; c < node.getChildCount(); c++) {
                DefaultMutableTreeNode candidate = (DefaultMutableTreeNode) node.getChildAt(c);
                if (parts[i].equals(candidate.toString())) {
                    child = candidate;
                    break;
                }
            }
            if (child == null) {
                throw new IllegalArgumentException("Tree path mismatch at segment '"
                        + parts[i] + "' under '" + node + "'");
            }
            node = child;
        }
        return countLeaves(node);
    }

    private static int countLeaves(DefaultMutableTreeNode node) {
        if (node.isLeaf()) return 1;
        int n = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            n += countLeaves((DefaultMutableTreeNode) node.getChildAt(i));
        }
        return n;
    }
}