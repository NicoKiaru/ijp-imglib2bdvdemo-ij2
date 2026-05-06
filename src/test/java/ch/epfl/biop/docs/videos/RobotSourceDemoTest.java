package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.command.CommandModule;
import sc.fiji.bdvpg.scijava.service.SourceService;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Drives {@link RobotSourceDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the {@code SwingSourceWidget} branch end-to-end.
 *
 * <p>Loads the {@code BRAIN_SECTION_3DTILES} dataset so the
 * {@code SourceService} tree is populated, prints the live tree (so the
 * {@code ">"}-delimited path syntax is easy to verify against ground truth),
 * picks the first leaf programmatically, and runs the command targeting that
 * leaf — the dialog should expand each ancestor visibly, single-click the leaf,
 * and produce an output equal to the leaf's source name.</p>
 */
public class RobotSourceDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, FijiCommands.context());
        System.out.println("[RobotSourceDemoTest] loaded " + sources.length + " source(s)");

        SourceService bss = FijiCommands.context().service(SourceService.class);
        TreeModel model = bss.tree().getTreeModel();

        System.out.println("===== current source tree =====");
        printTree((DefaultMutableTreeNode) model.getRoot(), 0);
        System.out.println("===== end =====");

        List<String> path = new ArrayList<>();
        firstLeafPath((DefaultMutableTreeNode) model.getRoot(), path);
        if (path.isEmpty()) throw new IllegalStateException("Source tree has no leaf");
        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0>All Sources";//String.join(">", path);
        System.out.println("[RobotSourceDemoTest] picking leaf via path: " + pathStr);

        Future<CommandModule> future = RobotCommands.run(RobotSourceDemoCommand.class,
                "source", pathStr);

        CommandModule module = future.get();
        System.out.println("[RobotSourceDemoTest] result = " + module.getOutput("result"));
    }

    private static void printTree(DefaultMutableTreeNode node, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(node.toString());
        if (node.isLeaf()) sb.append("  [leaf]");
        System.out.println(sb);
        for (int i = 0; i < node.getChildCount(); i++) {
            printTree((DefaultMutableTreeNode) node.getChildAt(i), depth + 1);
        }
    }

    private static void firstLeafPath(DefaultMutableTreeNode node, List<String> out) {
        out.add(node.toString());
        if (node.isLeaf()) return;
        firstLeafPath((DefaultMutableTreeNode) node.getChildAt(0), out);
    }
}
