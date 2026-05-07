package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.JTree;
import java.awt.Frame;

/**
 * Drives the standalone BigDataViewer-Playground "BDV Sources" frame
 * ({@link sc.fiji.bdvpg.scijava.service.tree.SourceTree}) end to end:
 * places the frame, right-clicks a node to open the
 * {@link javax.swing.JPopupMenu}, and walks a menu path through nested
 * submenus to invoke a SciJava command pre-filled with the selected sources.
 *
 * <p>Right-click semantics: the frame's mouse listener
 * ({@code SourceTree.java:161}) shows a popup based on whatever is currently
 * <em>selected</em> in the tree. {@link RobotTree#rightClickPath} therefore
 * left-clicks first to set the selection, then right-clicks in place.
 * Aiming at a non-leaf node (e.g. {@code "Sources>BrainSection-Tiles>Channel>ch_0"})
 * is the parent-selects-all pattern: every descendant source feeds into the
 * popup's command.</p>
 *
 * <p>Popup navigation: {@link RobotPopup#clickPath} hovers through each
 * submenu (Swing auto-opens on mouse-enter) and clicks the final menu item.
 * In this test that's {@code "Viewer>BDV>BDV - Show Sources"} — the
 * resulting SciJava command opens a BDV viewer on every selected source.</p>
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
        // across runs / screens. Offset on the right side of the IJ frame so the
        // cascading popup has room to grow rightward.
        // RobotHelper.placeFrame(bdvSources, 700, 100);
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        SourceService bss = FijiCommands.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        String treePath = "Sources>BrainSection-Tiles>Channel>ch_0";
        System.out.println("[RobotSourceTreeRightClickTest] right-clicking node at: " + treePath);
        RobotTree.rightClickPath(jtree, treePath);

        String menuPath = "Display>BDV>BDV - Show Sources";
        System.out.println("[RobotSourceTreeRightClickTest] navigating popup: " + menuPath);
        RobotPopup.clickPath(menuPath);

        // The command is now running — let it finish painting any UI it spawns
        // (a BDV viewer, in this case) before the test driver exits.
        RobotHelper.pause(2000);
        System.out.println("[RobotSourceTreeRightClickTest] OK");
    }
}
