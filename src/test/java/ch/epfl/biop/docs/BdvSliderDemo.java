package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.docs.videos.FijiCommands;
import ch.epfl.biop.docs.videos.Robot;
import ch.epfl.biop.docs.videos.RobotBdv;
import ch.epfl.biop.docs.videos.RobotHelper;
import ch.epfl.biop.docs.videos.Timings;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.Frame;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Smoke driver for {@link RobotBdv}. Reuses the {@link DeconvolutionVideoDemo}
 * prefix — open the multi-timepoint LLS7 dataset, show it in a new BDV — then
 * exercises the BDV-window primitives:
 * <ol>
 *   <li>{@link RobotBdv#setTimepoint(BdvHandle, int)} — drags the bottom
 *       timepoint slider through {@code max}, {@code 0}, {@code max / 2}.</li>
 *   <li>{@link RobotBdv#setCardPanelExpanded(BdvHandle, boolean)} — hovers
 *       the right-edge arrow and clicks to expand the card panel, pauses,
 *       then clicks again to collapse it.</li>
 * </ol>
 */
public class BdvSliderDemo {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();
        Robot.openBDVPlaygroundTree();

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        Robot.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", fileDataset.getName(),
                "unit", "MICROMETER",
                "files", new File[]{fileDataset},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);

        RobotHelper.pause(5000);

        // Snapshot existing BdvHandles so we can spot the one the show command
        // is about to create.
        Set<BdvHandle> before = new HashSet<>(
                SourceServices.getBdvDisplayService().getDisplays());

        Robot.runFromBDVPlaygroundTree("Sources>" + fileDataset.getName(),
                SingleBdvSourcesShowCommand.class,
                "adjust_view", true,
                "auto_contrast", true,
                "interpolate", false,
                "make_new_window", true);

        Frame bdvFrame = RobotHelper.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        RobotHelper.resizeFrame(bdvFrame, 800, 400);
        RobotHelper.dragFrame(bdvFrame,
                RobotHelper.DEFAULT_FRAME_X + 450,
                RobotHelper.DEFAULT_FRAME_Y + 150);

        BdvHandle bdv = newHandle(before);

        // Let the viewer settle before we start driving its slider.
        RobotHelper.pause(Timings.scaled(800));

        ViewerPanel vp = bdv.getViewerPanel();
        int max = vp.state().getNumTimepoints() - 1;

        RobotBdv.setTimepoint(bdv, max);
        RobotHelper.pause(Timings.scaled(600));
        RobotBdv.setTimepoint(bdv, 0);
        RobotHelper.pause(Timings.scaled(600));
        RobotBdv.setTimepoint(bdv, max / 2);

        RobotHelper.pause(Timings.scaled(600));
        RobotBdv.setCardPanelExpanded(bdv, true);
        RobotHelper.pause(Timings.scaled(800));
        RobotBdv.setCardPanelExpanded(bdv, false);
    }

    private static BdvHandle newHandle(Set<BdvHandle> before) {
        for (BdvHandle h : SourceServices.getBdvDisplayService().getDisplays()) {
            if (!before.contains(h)) return h;
        }
        throw new IllegalStateException("could not find newly-created BdvHandle");
    }
}