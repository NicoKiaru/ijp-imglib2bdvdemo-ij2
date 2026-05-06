package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.JTree;
import java.awt.Frame;

/**
 * Drives the standalone BigDataViewer-Playground "BDV Sources" frame
 * ({@link sc.fiji.bdvpg.scijava.service.tree.SourceTree}) — finds it,
 * places it on the target screen, navigates the tree along a
 * {@code ">"}-delimited path, and right-clicks the selected node so its
 * {@link javax.swing.JPopupMenu} (built by
 * {@code SourcePopupMenu}) opens at the cursor.
 *
 * <p>The popup operates on the <em>currently selected</em> sources — the
 * frame's right-click handler does not itself change the selection — so
 * {@link RobotTree#rightClickPath} left-clicks first, pauses, then right-clicks
 * in place. Aiming at a non-leaf node (e.g.
 * {@code "Sources>BrainSection-Tiles>Channel>ch_0"}) is the parent-selects-all
 * pattern: the popup will operate on every descendant source.</p>
 *
 * <p>The popup is held visible briefly (so it's apparent on video / screen)
 * then dismissed with {@code Esc}. Add {@code RobotHelper.pause(...)} to taste
 * if you want it to linger longer.</p>
 */
public class RobotSourceTreeRightClickTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        // Loading sources auto-shows the "BDV Sources" frame
        // (SourceTree.update() calls show() the first time a source arrives).
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, FijiCommands.context());
        System.out.println("[RobotSourceTreeRightClickTest] loaded " + sources.length + " source(s)");

        Frame bdvSources = RobotHelper.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        if (bdvSources == null) {
            throw new IllegalStateException("BDV Sources frame did not appear within "
                    + Timings.FRAME_WAIT_TIMEOUT_MS + " ms");
        }

        // Place the frame at a known position so the row coords are reproducible
        // across runs / screens. Offset on the right side of the IJ frame.
        RobotHelper.placeFrame(bdvSources, 700, 100);
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        SourceService bss = FijiCommands.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        String pathStr = "Sources>BrainSection-Tiles>Channel>ch_0";
        System.out.println("[RobotSourceTreeRightClickTest] right-clicking node at: " + pathStr);

        RobotTree.rightClickPath(jtree, pathStr);

        // Hold the popup briefly so it's visible to the human / screen recorder,
        // then dismiss it.
        RobotHelper.pause(2500);
        RobotHelper.escape();
        System.out.println("[RobotSourceTreeRightClickTest] OK");
    }
}
