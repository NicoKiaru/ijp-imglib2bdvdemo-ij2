package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.Layout;
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
public class GenerateDeconvolutionDocAssets {

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

        // Single placement area for the two BDV windows in this demo. The raw
        // window fills the whole area while it's alone, then we re-partition
        // into a 2×1 stack — raw on top, deconvolved beneath — so each pane
        // is 800×400 with no overlap.y
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150, 900, 600);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay();

        Step.begin("Open Hela Demo Dataset",
                "We open the raw LLS7 HeLa dataset — "
                        + "three channels, multi-timepoint, lazily loaded through Bio-Formats, "+
                        "voxel size = 144 x 144 x 400nm");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", fileDataset.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{fileDataset},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .sayOnDialog("Filling the importer dialog. The parameters are not very important for this demo.")
                .viaTreePath()
                .execute();
        Step.say("The CZI is opened lazily — nothing is read unless data is requested, for instance by a viewer."
                + "That's the reason why we can handle this multi-gigabyte fairly easily on a low-end computer.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Open Matching Point Spread Function",
                "We open the matching point-spread function (obtained via PSF distillation) — also through "
                        + "Bio-Formats.");
        Step.say("We walk through the same Bio-Formats command: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", filePSF.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{filePSF},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .sayOnDialog("Same dialog as before.")
                .viaTreePath()
                .execute();
        Step.say("Both datasets now live in the Sources tree, ready to feed the deconvolution command.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Show Hela Dataset",
                "We can display the original raw dataset in a BigDataViewer window — "
                        + "we'll set its title to 'BDV - Raw Sources'.");
        Step.say("Right-clicking the CZI node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", false,
                        "make_new_window", true)
                .sayOnDialog("Dialog: auto-contrast on, "
                        + "make_new_window=true so the raw view gets its own viewer.")
                .viaTreePath(fileDataset.getName())
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvRaw, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Raw Sources")
                .sayOnDialog("Renaming the window to \"BDV - Raw Sources\" so the upcoming sync "
                        + "command can address it by title.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Adjust Display Of Hela Dataset",
                "We jump to timepoint 30 and set a per-channel display range so all "
                        + "three channels are not displayed with saturation.");
        BdvHandle rawBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        Step.say("Stepping the time slider to frame 30 — that's the timepoint where we can see a mitosis event.");
        Bdv.setTimepoint(rawBdv, 30);
        Step.say("Opening the source card so we can dial in the display range per channel - mind the location of the blue arrow!");
        Bdv.setCardPanelExpanded(rawBdv, true);

        List<SourceAndConverter<?>> sources = rawBdv.getViewerPanel().state().getSources();
        Step.say("Channel 1: min to 90, max to 1500.");
        Bdv.selectSourceInCard(rawBdv, sources.get(0).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1500);
        Step.say("Channel 2 : min to 90, max to 512.");
        Bdv.selectSourceInCard(rawBdv, sources.get(1).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 512);
        Step.say("Channel 3: 90-1000.");
        Bdv.selectSourceInCard(rawBdv, sources.get(2).getSpimSource().getName());
        Bdv.setDisplayRange(rawBdv, 90, 1000);

        Step.say("Collapsing the source card so the viewer canvas takes back the full window.");
        Bdv.setCardPanelExpanded(rawBdv, false);
        Step.end();

        Step.begin("Richardson-Lucy Deconvolution (Tiled & Lazy)",
                "Now we run lazy Richardson-Lucy: 20 iterations, 256-by-256-by-64 "
                        + "blocks with 16-pixel overlap, non-circulant, light regularization.");
        Step.say("Right-clicking the Sources root — this is the heavy step; everything else "
                + "has been setup for this. Walking to: "
                + CommandExecutor.menuPath(SourcesDeconvolveCommand.class, 2));
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
                .sayOnDialog("Filling the deconvolve dialog: raw dataset as input, 400 nm PSF, "
                        + "reasonably-sized blocks with overlap, low iteration count for speed, light regularization.")
                .viaTreePath()
                .execute();
        Step.say("The command returns immediately — the deconvolution itself is lazy, "
                + "computed block by block on the GPU pool only when requested (by a viewer for instance).");
        Step.end();

        Step.begin("Show Deconvolved Dataset",
                "The deconvolved sources land under Other Sources. We open them in a "
                        + "second BDV beneath the raw view, sized to share the same width.");
        bdvArea.place(bdvRaw, 2, 1, 0, 0);

        Step.say("Right-clicking the new \"Other Sources\" branch (where the deconvolved channels "
                + "landed) — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", false,
                        "interpolate", false,
                        "make_new_window", true)
                .sayOnDialog("Dialog: auto_contrast off this time so we can carry the raw display "
                        + "ranges over manually for a fair visual comparison, make_new_window=true for a fresh viewer.")
                .viaTreePath("Other Sources")
                .execute();

        Frame bdvDeconvolved = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvDeconvolved, 2, 1, 1, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Deconvolved Sources")
                .sayOnDialog("Renaming it \"BDV - Deconvolved Sources\" so the sync command "
                        + "can find it by title.")
                .viaTreePath()
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("Synchronize Raw And Deconvolved Sources Display",
                "Finally, we set both windows to track each other "
                        + "live — pan, zoom and timepoint stay in sync as we navigate.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class, 2));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .args("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Deconvolved Sources"},
                        "bvvhs", null,
                        "synchronizetime", true)
                .sayOnDialog("Dialog: both BDV titles selected, synchronizetime=true — from this "
                        + "moment the two viewers share pan, zoom and timepoint.")
                .viaTreePath()
                .execute();
        Step.say("Holding the final frame for a minute while the deconvolved view streams in — "
                + "GPU tiles fill in block by block, and the side-by-side reveals noise reduction and intensity gain.");
        // The deconvolved view fills in over several seconds as the GPU
        // streams tiles — hold long enough that the final frame shows
        // both windows fully resolved before the clip ends.
        Ui.rawPause(90000);
        Step.end();

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots and script.md "
                + "are written. Click Continue to dismiss.");
    }
}