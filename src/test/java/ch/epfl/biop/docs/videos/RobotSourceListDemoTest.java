package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.command.CommandModule;
import sc.fiji.bdvpg.scijava.service.SourceService;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.util.concurrent.Future;

/**
 * Drives {@link RobotSourceListDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify that the {@code SwingSourceListWidget} (multi-source JTree) is
 * driven correctly by the same {@code ">"}-delimited path API as the
 * single-source widget.
 *
 * <p>The widget's {@code getValue()} recursively collects every descendant
 * {@code SourceAndConverter} of any non-leaf node that's selected — so
 * single-clicking a parent node like {@code "Sources>BrainSection-Tiles>Channel>ch_0"}
 * is enough to deliver all sources beneath it. No multi-select / Ctrl+click
 * is needed for the common "select everything in this group" case.</p>
 *
 * <p>Compares the count returned by the command against the live tree's
 * descendant count for the chosen path so a regression in either the path
 * navigation or the harvester wiring fails loudly.</p>
 */
public class RobotSourceListDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, FijiCommands.context());
        System.out.println("[RobotSourceListDemoTest] loaded " + sources.length + " source(s)");

        SourceService bss = FijiCommands.context().service(SourceService.class);
        TreeModel model = bss.tree().getTreeModel();

        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0";
        System.out.println("[RobotSourceListDemoTest] selecting parent node via path: " + pathStr);

        int expected = countLeavesAt((DefaultMutableTreeNode) model.getRoot(), pathStr.split(">"));
        System.out.println("[RobotSourceListDemoTest] expected " + expected + " descendant source(s)");

        Future<CommandModule> future = RobotCommands.run(RobotSourceListDemoCommand.class,
                "sources", pathStr);

        CommandModule module = future.get();
        int actual = (Integer) module.getOutput("count");
        System.out.println("[RobotSourceListDemoTest] actual = " + actual
                + " — names: " + module.getOutput("result"));
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " sources, got " + actual);
        }
        System.out.println("[RobotSourceListDemoTest] OK");
    }

    /** Walk {@code parts} from {@code root}, then count leaves in the resulting subtree. */
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
