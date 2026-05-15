package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.scijava.converter.StringToBdvHandleArray;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Generates a demo for deconvolution. Should be used for video documentation.
 * <p>
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

        // Flip both to true while iterating on this demo — every CommandExecutor
        // runs programmatically (no Robot, no dialog) and Ui frame placement /
        // pauses become instant. Flip back to false to record.
        CommandExecutor.FORCE_PROGRAMMATIC = false;
        Ui.FAST_MODE = false;

        // Asset capture toggles. Flip these to false while iterating on the
        // demo; flip them back on for the recording pass. Output lands in
        // target/video-assets/DeconvolutionVideoDemo/ as NNN-step.{mp4,png}
        // plus a single script.md transcript.
        Assets.session("DeconvolutionVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        // Prepare = cache necessary files
        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        File filePSF = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-400nm.tif");

        Step.begin("boot-fiji",
                "We start Fiji and place its main window on the secondary monitor.");
        Fiji.startFiji();
        Step.end();

        Step.begin("open-bdv-tree",
                "From Fiji's search bar we open the BDV Playground Sources tree, "
                        + "where every loaded source will land.");
        CommandExecutor.of(SourceServiceShowCommand.class)
                .viaSearch("show bdv play")
                .execute();
        Frame bdvSources = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvSources, 450, 600);
        Ui.dragFrame(bdvSources, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y + 150);
        Step.end();

        Step.begin("load-raw-czi",
                "We load the raw LLS7 HeLa dataset through Bio-Formats — "
                        + "three channels, multi-timepoint, lazily backed by the .czi.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", fileDataset.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{fileDataset},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("load-psf",
                "Then the matching 400 nm point-spread function — also through "
                        + "Bio-Formats so units and conventions match the dataset.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", filePSF.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{filePSF},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("show-raw-bdv",
                "Right-clicking the dataset shows it in a new BigDataViewer window — "
                        + "we'll call this one Raw Sources and place it next to the tree.");
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", false,
                        "make_new_window", true)
                .viaTreePath(fileDataset.getName())
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        Ui.resizeFrame(bdvRaw, 800, 600);
        Ui.dragFrame(bdvRaw, Ui.DEFAULT_FRAME_X + 450, Ui.DEFAULT_FRAME_Y + 150);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Raw Sources")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("tune-raw-display",
                "We jump to timepoint 30 and set a per-channel display range so all "
                        + "three channels read distinctly before we deconvolve.");
        BdvHandle rawBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        Bdv.setTimepoint(rawBdv, 30);
        Bdv.setCardPanelExpanded(rawBdv, true);

        List<SourceAndConverter<?>> sources = rawBdv.getViewerPanel().state().getSources();
        Bdv.selectSourceInCard(rawBdv, sources.get(0).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1500);
        Bdv.selectSourceInCard(rawBdv, sources.get(1).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 512);
        Bdv.selectSourceInCard(rawBdv, sources.get(2).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1000);

        Bdv.setCardPanelExpanded(rawBdv, false);
        Step.end();

        Step.begin("deconvolve",
                "Now we run lazy Richardson-Lucy: 20 iterations, 256-by-256-by-64 "
                        + "blocks with 16-pixel overlap, non-circulant, light regularization.");
        CommandExecutor.of(SourcesDeconvolveCommand.class)
                .args("sources", fileDataset.getName(),
                        "psf", filePSF.getName(),
                        "output_pixel_type", "Keep Pixel Type Of Original Image",
                        "suffix", "_deconvolved",
                        "block_size_x", 256,
                        "block_size_y", 256,
                        "block_size_z", 64,
                        "overlap_size", 16,
                        "num_iterations", 20,
                        "non_circulant", true,
                        "regularization_factor", 0.002f,
                        "n_threads", 4)
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("show-deconvolved-bdv",
                "The deconvolved sources land under Other Sources. We open them in a "
                        + "second BDV beneath the raw view, sized to share the same width.");
        Ui.resizeFrame(bdvRaw, 800, 400);

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", false,
                        "interpolate", false,
                        "make_new_window", true)
                .viaTreePath("Other Sources")
                .execute();

        Frame bdvDeconvolved = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        Ui.resizeFrame(bdvDeconvolved, 800, 400);
        Ui.dragFrame(bdvDeconvolved, Ui.DEFAULT_FRAME_X + 450, Ui.DEFAULT_FRAME_Y + 450);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Deconvolved Sources")
                .viaTreePath()
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("sync-views",
                "Finally, ViewSynchronizeCommand keeps both windows tracking each other "
                        + "live — pan, zoom and timepoint stay in sync as we navigate.");
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .args("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Deconvolved Sources"},
                        "bvvhs", null,
                        "synchronizetime", true)
                .viaTreePath()
                .execute();
        // The deconvolved view fills in over several seconds as the GPU
        // streams tiles — hold long enough that the final frame shows
        // both windows fully resolved before the clip ends.
        Ui.rawPause(45000);
        Step.end();
    }
}