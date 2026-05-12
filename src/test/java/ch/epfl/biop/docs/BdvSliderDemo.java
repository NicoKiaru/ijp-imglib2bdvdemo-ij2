package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.Frame;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Smoke driver for {@link Bdv}. Reuses the {@link DeconvolutionVideoDemo}
 * prefix — open the multi-timepoint LLS7 dataset, show it in a new BDV — then
 * exercises the BDV-window primitives:
 * <ol>
 *   <li>{@link Bdv#setTimepoint(BdvHandle, int)} — drags the bottom
 *       timepoint slider through {@code max}, {@code 0}, {@code max / 2}.</li>
 *   <li>{@link Bdv#setCardPanelExpanded(BdvHandle, boolean)} — hovers
 *       the right-edge arrow and clicks to expand the card panel.</li>
 *   <li>{@link Bdv#selectSourceInCard(BdvHandle, String)} +
 *       {@link Bdv#setDisplayRange(BdvHandle, double, double)} — clicks
 *       each source in the Sources card and edits its min/max display range
 *       via the {@code BoundedRangePanel} spinners.</li>
 *   <li>{@link Bdv#setCardPanelExpanded(BdvHandle, boolean)} again to
 *       collapse the card panel.</li>
 * </ol>
 */
public class BdvSliderDemo {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {
        Fiji.startFiji();
        BdvPlayground.openBDVPlaygroundTree();

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", fileDataset.getName(),
                "unit", "MICROMETER",
                "files", new File[]{fileDataset},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);

        Ui.pause(5000);

        // Snapshot existing BdvHandles so we can spot the one the show command
        // is about to create.
        Set<BdvHandle> before = new HashSet<>(
                SourceServices.getBdvDisplayService().getDisplays());

        BdvPlayground.runFromBDVPlaygroundTree("Sources>" + fileDataset.getName(),
                SingleBdvSourcesShowCommand.class,
                "adjust_view", true,
                "auto_contrast", true,
                "interpolate", false,
                "make_new_window", true);

        Frame bdvFrame = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvFrame, 800, 400);
        Ui.dragFrame(bdvFrame,
                Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150);

        BdvHandle bdv = newHandle(before);

        // Let the viewer settle before we start driving its slider.
        Ui.pause(Timings.scaled(800));

        ViewerPanel vp = bdv.getViewerPanel();
        int max = vp.state().getNumTimepoints() - 1;

        Bdv.setTimepoint(bdv, max);
        Ui.pause(Timings.scaled(600));
        Bdv.setTimepoint(bdv, 0);
        Ui.pause(Timings.scaled(600));
        Bdv.setTimepoint(bdv, max / 2);

        Ui.pause(Timings.scaled(600));
        Bdv.setCardPanelExpanded(bdv, true);
        Ui.pause(Timings.scaled(800));

        // Walk the sources and adjust each one's display range. Source names
        // come from the live ViewerState — no need to hardcode the
        // BioFormats-derived naming.
        List<SourceAndConverter<?>> sources = bdv.getViewerPanel().state().getSources();
        for (int i = 0; i < sources.size(); i++) {
            SourceAndConverter<?> s = sources.get(i);
            String name = s.getSpimSource().getName();
            Bdv.selectSourceInCard(bdv, name);
            Ui.pause(Timings.scaled(500));
            // Different range per source so the spinner edits read distinctly
            // on video.
            Bdv.setDisplayRange(bdv, 100.0 * (i + 1), 2000.0 * (i + 1));
            Ui.pause(Timings.scaled(600));
        }

        Ui.pause(Timings.scaled(800));
        Bdv.setCardPanelExpanded(bdv, false);
    }

    private static BdvHandle newHandle(Set<BdvHandle> before) {
        for (BdvHandle h : SourceServices.getBdvDisplayService().getDisplays()) {
            if (!before.contains(h)) return h;
        }
        throw new IllegalStateException("could not find newly-created BdvHandle");
    }
}