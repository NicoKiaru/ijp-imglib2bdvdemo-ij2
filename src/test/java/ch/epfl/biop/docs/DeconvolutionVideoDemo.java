package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Generates a demo for deconvolution. Should be used for video documentation.
 *
 * Workflow:
 *   1. Open raw LLS7 HeLa image (both channels) in BDV.
 *   2. Run lazy Richardson-Lucy deconvolution (both channels, 200 nm PSF).
 *   3. Open deconvolved sources in a second BDV window, placed beside the raw window.
 *   4. Run ViewSynchronizeCommand — both windows now track each other live.
 *   5. Pause — navigate to a good region, wait for GPU tiles, then Continue to capture both.
 */
public class DeconvolutionVideoDemo {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {
        Fiji.startFiji();
        BdvPlayground.openBDVPlaygroundTree();

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", fileDataset.getName(),
                "unit", "MICROMETER",
                "files", new File[]{fileDataset},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);


        File filePSF = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-400nm.tif");
        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", filePSF.getName(),
                "unit", "MICROMETER",
                "files", new File[]{filePSF},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);

        BdvPlayground.runFromBDVPlaygroundTree("Sources>"+fileDataset.getName(), SingleBdvSourcesShowCommand.class,
                "adjust_view", true,
                "auto_contrast", true,
                "interpolate", false,
                "make_new_window", true);

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvRaw, 800, 600);
        Ui.dragFrame(bdvRaw, Ui.DEFAULT_FRAME_X + 450, Ui.DEFAULT_FRAME_Y + 150);

        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources", BdvTitleSetCommand.class,
                "title", "BDV - Raw Sources");

        BdvHandle rawBdv = SourceServices.getBdvDisplayService().getActiveBdv();

        Bdv.setTimepoint(rawBdv, 30);

        Bdv.setCardPanelExpanded(rawBdv, true);
        Ui.pause(2000);

        List<SourceAndConverter<?>> sources = rawBdv.getViewerPanel().state().getSources();
        Bdv.selectSourceInCard(rawBdv, sources.get(0).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1500);
        Bdv.selectSourceInCard(rawBdv, sources.get(1).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 512);
        Bdv.selectSourceInCard(rawBdv, sources.get(2).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1000);

        Bdv.setCardPanelExpanded(rawBdv, false);

        BdvPlayground.runFromBDVPlaygroundTree("Sources>"+fileDataset.getName(), SourcesDeconvolveCommand.class,
                "sources", "Sources>"+fileDataset.getName(),
                "psf", "Sources>"+filePSF.getName(),
                "output_pixel_type", "Keep Pixel Type Of Original Image",
                "suffix", "_deconvolved",
                "block_size_x", 256,
                "block_size_y", 256,
                "block_size_z", 64,
                "overlap_size", 16,
                "num_iterations", 20,
                "non_circulant", true,
                "regularization_factor", 0.002f,
                "n_threads", 4);

        Ui.resizeFrame(bdvRaw, 800, 400);

        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources", SingleBdvSourcesShowCommand.class,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", false,
                "make_new_window", true);


        Frame bdvDeconvolved = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvDeconvolved, 800, 400);
        Ui.dragFrame(bdvDeconvolved, Ui.DEFAULT_FRAME_X + 450, Ui.DEFAULT_FRAME_Y + 450);

        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources", BdvTitleSetCommand.class,
                "title", "BDV - Deconvolved Sources");

        BdvPlayground.runFromBDVPlaygroundTree("Sources>Other Sources", ViewSynchronizeCommand.class,
                "bdvhs", new String[]{"BDV - Raw Sources", "BDV - Deconvolved Sources"},
                    "synchronizetime", true);
    }
}