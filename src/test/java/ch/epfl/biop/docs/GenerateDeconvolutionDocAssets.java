package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import ch.epfl.biop.docs.videos.Demo;
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
import ch.epfl.biop.docs.videos.widgets.Tree;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;
import sc.fiji.bdvpg.service.SourceServices;

import javax.swing.*;
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
        // plus a single timeline.json transcript.
        Assets.session("DeconvolutionVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        Demo.intro("Richardson-Lucy Deconvolution in Fiji",
                "Deconvolve, view and save a multi-gigabyte LLS7 light-sheet dataset in Fiji — "
                        + "without ever loading it into memory.",
                "Streams data block-by-block — multi-gigabyte datasets on a modest computer",
                "Scales across every available GPU",
                "Lazy by design — a block is computed only when requested");

        // Prepare = cache necessary files
        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        File filePSF = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-400nm.tif");
        String datasetName = FilenameUtils.removeExtension(fileDataset.getName());
        String psfName = FilenameUtils.removeExtension(filePSF.getName());

        // OME-TIFF export target — desktop, frame range encoded in the name so
        // a viewer of the resulting file knows what's inside. Wiped at start so
        // the export step always runs against a clean slate (otherwise
        // OMETiffWriter throws when the target exists).
        int tIni = 28;
        int tEnd = 30;
        File exportFile = new File("C:\\Users\\Nicolas\\Desktop",
                datasetName + "_deconvolved_t"+tIni+"-"+tEnd+".ome.tiff");
        if (exportFile.exists() && !exportFile.delete()) {
            throw new IllegalStateException(
                    "Could not delete pre-existing export file: " + exportFile);
        }

        // Single placement area for the two BDV windows in this demo. The raw
        // window fills the whole area while it's alone, then we re-partition
        // into a 2×1 stack — raw on top, deconvolved beneath — so each pane
        // is 800×400 with no overlap.y
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y, 800, 700);

        GenerateDocAssetsCommon.boot();

        Step.begin("Open Tasks Window",
                "Before we shrink Fiji out of the way, we open the Tasks panel — "
                        + "a click in the bottom-right corner of the Fiji main window. "
                        + "We'll watch the OME-TIFF export progress here at the end of the video.");
        Step.say("Clicking the small Tasks indicator at the bottom-right of the Fiji status bar.");
        Frame fijiFrame = Fiji.getFijiFrame();
        Insets fijiInsets = fijiFrame.getInsets();
        int tasksX = fijiFrame.getX() + fijiFrame.getWidth() - fijiInsets.right - 5;
        int tasksY = fijiFrame.getY() + fijiFrame.getHeight() - fijiInsets.bottom - 5;
        Ui.moveTo(tasksX, tasksY);
        Ui.click();
        Frame tasksFrame = Ui.waitForFrame("Tasks", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.placeFrame(tasksFrame, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        Ui.resizeFrame(tasksFrame, 450, 100);
        Step.end();

        GenerateDocAssetsCommon.showBdvTreeDisplay(true);

        Step.begin("Open Hela Demo Dataset",
                "We open the raw LLS7 HeLa dataset — "
                        + "three channels, multi-timepoint, opened through Bio-Formats, "+
                        "voxel size = 144 x 144 x 400nm");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", datasetName,
                        "We name the dataset after the file itself — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "Browsing to the Hela example file.")
                .arg("split_rgb_channels", false, "RGB is irrelevant for this dataset.")
                .arg("auto_pyramidize", true,
                        "Auto-pyramidize is not relevant for this dataset.")
                .arg("plane_origin_convention", "TOP LEFT", "The origin convention does not matter since there's only one tile in this dataset.")
                .arg("disable_memo", false, "We keep memoization - fast file opening.")
                .sayOnDialog("Walking through the Bio-Formats importer dialog.")
                .viaTreePath()
                .execute();
        Step.say("The CZI is opened lazily — nothing is read unless data is requested, for instance by a viewer. "
                + "That's the reason why we can handle this multi-gigabyte dataset fairly easily on a low-end computer.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Show Hela Dataset",
                "We can display the original raw dataset in a BigDataViewer window — "
                        + "we'll set its title to 'BDV - Raw Sources'.");
        Step.say("Right-clicking the CZI node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast on so we start with sensible display ranges per channel.")
                .arg("adjust_view", true,
                        "Re-centering the view on the loaded sources.")
                .arg("make_new_window", true,
                        "Open a fresh BDV window — leave any existing viewers untouched.")
                .arg("interpolate", false, "Avoiding interpolation facilitates the visualisation of voxels.")
                .sayOnDialog("Walking through the show-sources dialog.")
                .viaTreePath(datasetName,
                        "We select the raw CZI sources for display.")
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvRaw, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Sources",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Adjust Display Of Hela Dataset",
                "We jump to timepoint 30 and set a per-channel display range so all "
                        + "three channels are not displayed with saturation.");
        BdvHandle rawBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        Step.say("Stepping the time slider to frame 30 — where we can see a nice mitosis event.");
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

        Step.begin("Open Matching Point Spread Function",
                "We open the matching point-spread function (obtained via PSF distillation) — also through "
                        + "Bio-Formats.");
        Step.say("We walk through the same Bio-Formats command: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", psfName)
                .arg("unit", "MICROMETER")
                .arg("files", new File[]{filePSF},"Browsing to the 400 nm PSF which matches this dataset.")
                .arg("split_rgb_channels", false, "Do need to modify the other parameters")
                .arg("auto_pyramidize", true)
                .arg("plane_origin_convention", "TOP LEFT")
                .arg("disable_memo", false)
                .sayOnDialog("We fill it as before.")
                .viaTreePath()
                .execute();
        Ui.rawPause(1500);
        Step.say("Both datasets now live in the Sources tree, ready to feed the deconvolution command.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Richardson-Lucy Deconvolution (Tiled & Lazy)",
                "Now we run lazy Richardson-Lucy: 20 iterations, 256-by-256-by-64 "
                        + "blocks with 16-pixel overlap, non-circulant, light regularization.");
        Step.say("Right-clicking the Sources root — this is the heavy step; everything else "
                + "has been setup for this. Walking to: "
                + CommandExecutor.menuPath(SourcesDeconvolveCommand.class, 2));
        CommandExecutor.of(SourcesDeconvolveCommand.class)
                .arg("sources", datasetName,
                        "Picking the HeLa dataset as the input to deconvolve.")
                .arg("psf", psfName,
                        "Picking the matching 400 nm PSF as the kernel.")
                .arg("output_pixel_type", "Keep Pixel Type Of Original Image",
                        "Keep original pixel type (16-bit) to save memory.")
                .arg("suffix", "_deconvolved",
                        "Suffix appended to each new source name so the two sets stay distinguishable.")
                .arg("block_size_x", 256,
                        "Processing by blocks of 256×256×64 pixels.")
                .arg("block_size_y", 256)
                .arg("block_size_z", 64)
                .arg("overlap_size", 16,
                        "16-pixel overlap between blocks to hide tile boundaries in the result.")
                .arg("num_iterations", 20,
                        "20 Richardson-Lucy iterations — a sweet spot for this PSF and noise level.")
                .arg("non_circulant", true,
                        "Non-circulant mode avoids wrap-around artefacts at block edges.")
                .arg("regularization_factor", 0.001f,
                        "Light regularization — just enough to tame noise amplification.")
                .arg("n_threads", 4,
                        "Four streams in flight at once to keep the GPU pool saturated.")
                .sayOnDialog("Walking through the deconvolve dialog — the parameters that shape the result.")
                .viaTreePath()
                .execute();
        Step.say("The command returns immediately — the deconvolution itself is lazy, "
                + "computed block by block on the GPU pool only when requested (by a viewer for instance).");
        Step.end();

        Ui.rawPause(1500);

        Step.begin("Show Deconvolved Dataset",
                "The deconvolved sources land under Other Sources. We open them in a "
                        + "second BDV beneath the raw view, sized to share the same width.");
        bdvArea.place(bdvRaw, 2, 1, 0, 0);

        SourceService bss = Fiji.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        Step.say("Let's double click the \"Other Sources\" node to expand it and display the generated sources.");

        Tree.doubleClickPath(jtree,"Other Sources");
        Ui.rawPause(1500);

        Step.say("Right-clicking the \"Other Sources\" branch — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", false,
                        "Auto-contrast off this time — we'll carry the raw ranges over manually for a fair side-by-side.")
                .arg("adjust_view", true,
                        "Re-centering on the deconvolved sources.")
                .arg("make_new_window", true,
                        "New window so the raw view stays untouched next to it.")
                .arg("interpolate", false)
                .sayOnDialog("Walking through the show-sources dialog again.")
                .viaTreePath("Other Sources",
                        "We pick the deconvolved sources that just landed under \"Other Sources\".")
                .execute();

        Frame bdvDeconvolved = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvDeconvolved, 2, 1, 1, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Deconvolved Sources",
                        "Naming this one so the sync command can pair the two windows by title.")
                .sayOnDialog("Setting the window title.")
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
                .arg("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Deconvolved Sources"},
                        "Selecting both BDV windows we just named — the two viewers that will share state.")
                .arg("bvvhs", null)
                .arg("synchronizetime", true,
                        "Lock timepoints too — scrubbing one slider scrubs both.")
                .sayOnDialog("Walking through the synchronize dialog.")
                .viaTreePath()
                .execute();
        Step.say("Holding the final frame while the deconvolved view streams in — "
                + "GPU tiles fill in block by block, and the side-by-side reveals noise reduction and intensity gain.");

        Ui.FAST_MODE = false;
        Frame syncWindow = Ui.waitForFrame("Close window to stop synchronization", 6000);
        Ui.resizeFrame(syncWindow, 450, 100);
        Ui.dragFrame(syncWindow, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);

        // The deconvolved view fills in over several seconds as the GPU
        // streams tiles — hold long enough that the final frame shows
        // both windows fully resolved before the clip ends.
        Ui.rawPause(90000);
        Step.end();

        Step.begin("Save Deconvolved Sources To OME-TIFF",
                "We export timepoints "+tIni+" to "+tEnd+" of the deconvolved sources as a "
                        + "multi-resolution OME-TIFF — written straight to the desktop, "
                        + "ready to be reopened in any OME-TIFF reader.");
        // Close the sync window so the Tasks panel placed underneath becomes
        // visible — we want the export progress in frame while it runs.
        Ui.closeWithMouse(syncWindow);
        Ui.rawPause(500);
        Step.say("Right-clicking the \"Other Sources\" branch — walking to: "
                + CommandExecutor.menuPath(KheopsExportSourcesCommand.class, 2));
        CommandExecutor.of(KheopsExportSourcesCommand.class)
                .arg("range_frames", tIni+":"+tEnd,
                        "Export only timepoints "+tIni+" to "+tEnd+" — fast enough to demo.")
                .arg("file", exportFile,
                        "Saving straight to the desktop.")
                .arg("unit", "MICROMETER",
                        "Coordinates in micrometers.")
                .arg("override_voxel_size", false, "No need to override the voxel sizes: they will be carried over since the deconvolved data is already well calibrated."+
                        " As a result the parameters below are ignored")
                .arg("n_resolution_levels", 1, "No need to build multiple resolution levels since the dataset is small enough.")
                .arg("tile_size_x", 512, "Pretty standard block saving size: 512 x 512 pixels")
                .arg("tile_size_y", 512)

                //.arg("vox_size_xy_um", 0.144)
                //.arg("vox_size_z_um", 0.4)
                .arg("n_threads", 4,
                        "Four fetcher threads so IO doesn't bottleneck the GPU pool.")
                .sayOnDialog("Walking through the OME-TIFF export dialog.")
                .viaTreePath("Other Sources",
                        "We pick the deconvolved sources — they live under \"Other Sources\".")
                .execute();
        Step.say("Each block is computed by the GPU on demand and streamed straight to disk — "
                + "the Tasks panel reports per-channel progress.");

        // TREE-mode .execute() returns once OK is clicked; the export then
        // runs on the SciJava worker thread. Poll the output file until its
        // size has been stable for a few seconds (or hit the safety timeout)
        // so the Tasks-panel progress bar runs to completion on video before
        // we end the clip.
        long stableSince = -1;
        long lastSize = -1;
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            long size = exportFile.exists() ? exportFile.length() : 0L;
            if (size > 0 && size == lastSize) {
                if (stableSince < 0) stableSince = System.currentTimeMillis();
                if (System.currentTimeMillis() - stableSince > 5000) break;
            } else {
                stableSince = -1;
            }
            lastSize = size;
            Ui.rawPause(1000);
        }
        Step.say("Done — the OME-TIFF is ready on the desktop.");
        Ui.rawPause(5000);
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked, so it
        // lands in timeline.json's outro block and in youtube-description.md.
        Demo.outro("You've seen tiled, lazy Richardson-Lucy deconvolution end to end — "
                        + "from a raw multi-gigabyte CZI to a synchronized side-by-side comparison and OME-TIFF export, "
                        + "all without loading the dataset into memory.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "UNIGE - Biochemistry: https://www.unige.ch/sciences/biochimie/",
                "Sample LLS7 dataset: https://zenodo.org/records/19047136",
                "PSF dataset: https://zenodo.org/records/14505724");

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}