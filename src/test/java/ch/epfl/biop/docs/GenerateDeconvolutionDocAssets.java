package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;

import ch.unige.biochem.fiji.robot.CmdExecutor;
import ch.unige.biochem.fiji.robot.bdv.view.Bdv;
import ch.unige.biochem.fiji.robot.core.Assets;
import ch.unige.biochem.fiji.robot.core.CommandRef;
import ch.unige.biochem.fiji.robot.core.ScreenRecorder;
import ch.unige.biochem.fiji.robot.core.Screenshotter;
import ch.unige.biochem.fiji.robot.core.Step;
import ch.unige.biochem.fiji.robot.core.Timeline;
import ch.unige.biochem.fiji.robot.core.Timings;
import ch.unige.biochem.fiji.robot.core.Ui;
import ch.unige.biochem.fiji.robot.groovy.GroovyScript;
import ch.unige.biochem.fiji.robot.ij1.Fiji;
import ch.unige.biochem.fiji.robot.widgets.Tree;

import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;
import sc.fiji.bdvpg.service.SourceServices;

import javax.swing.JTree;
import java.awt.Frame;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.bdv.command.BdvLaunchers.treeLauncher;
import static ch.unige.biochem.fiji.robot.ij1.Ij1Launchers.searchLauncher;

/**
 * Generates a demo for deconvolution. Should be used for video documentation.
 *
 * <p>This demo has been ported onto the standalone <b>fiji-robot</b> library
 * ({@code ch.unige.biochem.fiji.robot}), the extracted/modularised version of
 * the in-repo {@code ch.epfl.biop.docs.videos} toolkit. The mapping:</p>
 * <ul>
 *   <li>{@code CommandExecutor.of(C).arg(...).viaTreePath().execute()} →
 *       {@code CmdExecutor.of(context, C).postSet(name, fromDialog(value, narration))
 *       .withLauncher(treeLauncher(...)).launch()}. The launcher choice <em>is</em>
 *       the execution mode now — there is no {@code FORCE_PROGRAMMATIC} global; to
 *       iterate headlessly swap the launcher for {@code programmaticLauncher()}.</li>
 *   <li>{@code .sayOnDialog(text)} → a plain {@code Step.say(text)} right before
 *       {@code launch()} (the per-field narrations carried by {@code fromDialog}
 *       still fire as each widget is driven).</li>
 *   <li>{@code .skipInScript()} → wrap the launch in {@link #skipScript} — the new
 *       {@link GroovyScript} records every {@code launch()} unconditionally, so
 *       suppressing a line means detaching the recorder for that one call.</li>
 *   <li>{@code Demo.intro/outro} → {@link Timeline#setIntro}/{@link Timeline#setOutro}
 *       directly, plus the {@code youtube-description.md} writer kept local here.</li>
 *   <li>{@code Layout} → the small local {@link Layout} below (not yet ported to
 *       the library), backed by the library's {@link Ui#resizeFrame}/{@link Ui#dragFrame}.</li>
 * </ul>
 *
 * <p>Known rough edges of the port: the library does not yet have
 * {@code GroovyScript.nameFile(...)}, so {@code File} inputs render in the
 * embedded script as anonymous {@code #@File fileN} parameters instead of the
 * readable {@code datasetFile} / {@code psfFile} / {@code exportFile} labels.</p>
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Open raw LLS7 HeLa image (both channels) in BDV.</li>
 *   <li>Run lazy Richardson-Lucy deconvolution (both channels, 200 nm PSF).</li>
 *   <li>Open deconvolved sources in a second BDV window, placed beside the raw window.</li>
 *   <li>Run ViewSynchronizeCommand — both windows now track each other live.</li>
 *   <li>Pause — navigate to a good region, wait for GPU tiles, then Continue to capture both.</li>
 * </ol>
 */
public class GenerateDeconvolutionDocAssets {

    static {
        LegacyInjector.preinit();
    }

    /** The SciJava context booted by {@link #bootFiji()} — feeds every {@link CmdExecutor#of}. */
    private static Context context;

    /** Intro metadata, retained so {@link #writeYoutubeDescription} can re-use it. */
    private static String introTitle;
    private static String introTagline;
    private static List<String> introHighlights = new ArrayList<>();

    public static void main(String[] args) throws Exception {

        DebugTools.setRootLevel("WARN");
        // Iterate headlessly by swapping the visible launchers below for
        // programmaticLauncher() — there is no global FORCE_PROGRAMMATIC switch
        // in fiji-robot. FAST_MODE still makes Ui frame placement / pauses instant.
        Ui.FAST_MODE = false;

        // Asset capture toggles. Flip these to false while iterating on the
        // demo; flip them back on for the recording pass. Output lands in
        // target/video-assets/DeconvolutionVideoDemo/ as NNN-step.{mp4,png}
        // plus a single timeline.json transcript.
        Assets.session("DeconvolutionVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        // Install the script recorder: every CmdExecutor.launch() inside an open
        // Step auto-records its cs.run(...) body into timeline.json's script block.
        GroovyScript script = new GroovyScript().install();

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        intro("Richardson-Lucy Deconvolution in Fiji",
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

        // NOTE: fiji-robot has no GroovyScript.nameFile(...) yet, so the File
        // inputs below surface in the embedded script as anonymous #@File fileN
        // params rather than the readable datasetFile / psfFile / exportFile
        // labels the old toolkit produced. See the class doc.

        // Single placement area for the two BDV windows in this demo. The raw
        // window fills the whole area while it's alone, then we re-partition
        // into a 2×1 stack — raw on top, deconvolved beneath — so each pane
        // is 800×400 with no overlap.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y, 800, 700);

        Frame fijiFrame = bootFiji();

        Step.begin("Open Tasks Window",
                "Before we shrink Fiji out of the way, we open the Tasks panel — "
                        + "a click in the bottom-right corner of the Fiji main window. "
                        + "We'll watch the OME-TIFF export progress here at the end of the video.");
        Step.say("Clicking the small Tasks indicator at the bottom-right of the Fiji status bar.");
        Insets fijiInsets = fijiFrame.getInsets();
        int tasksX = fijiFrame.getX() + fijiFrame.getWidth() - fijiInsets.right - 5;
        int tasksY = fijiFrame.getY() + fijiFrame.getHeight() - fijiInsets.bottom - 5;
        Ui.moveTo(tasksX, tasksY);
        Ui.click();
        Frame tasksFrame = Ui.waitForFrame("Tasks", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.placeFrame(tasksFrame, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        Ui.resizeFrame(tasksFrame, 450, 100);
        Step.end();

        showBdvTree(true, fijiFrame);

        Step.begin("Open Hela Demo Dataset",
                "We open the raw LLS7 HeLa dataset — "
                        + "three channels, multi-timepoint, opened through Bio-Formats, "+
                        "voxel size = 144 x 144 x 400nm");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        Step.say("Walking through the Bio-Formats importer dialog.");
        CmdExecutor.of(context, DatasetFromBioFormatsCreateCommand.class)
                .withLauncher(treeLauncher())
                .postSet("datasetname", fromDialog(datasetName,
                        "We name the dataset after the file itself — keeps the source tree readable."))
                .postSet("unit", fromDialog("MICROMETER",
                        "Coordinates in microns."))
                .postSet("files", fromDialog(new File[]{fileDataset},
                        "Browsing to the Hela example file."))
                .postSet("split_rgb_channels", fromDialog(false, "RGB is irrelevant for this dataset."))
                .postSet("auto_pyramidize", fromDialog(true,
                        "Auto-pyramidize is not relevant for this dataset."))
                .postSet("plane_origin_convention", fromDialog("TOP LEFT", "The origin convention does not matter since there's only one tile in this dataset."))
                .postSet("disable_memo", fromDialog(false, "We keep memoization - fast file opening."))
                .launch();
        Step.say("The CZI is opened lazily — nothing is read unless data is requested, for instance by a viewer. "
                + "That's the reason why we can handle this multi-gigabyte dataset fairly easily on a low-end computer.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Show Hela Dataset",
                "We can display the original raw dataset in a BigDataViewer window — "
                        + "we'll set its title to 'BDV - Raw Sources'.");
        Step.say("Right-clicking the CZI node — walking to: "
                + menuPath(SingleBdvSourcesShowCommand.class, 2));
        Step.say("We select the raw CZI sources for display.");
        Step.say("Walking through the show-sources dialog.");
        skipScript(script, () ->
            CmdExecutor.of(context, SingleBdvSourcesShowCommand.class)
                    .withLauncher(treeLauncher(datasetName))
                    .postSet("auto_contrast", fromDialog(true,
                            "Auto-contrast on so we start with sensible display ranges per channel."))
                    .postSet("adjust_view", fromDialog(true,
                            "Re-centering the view on the loaded sources."))
                    .postSet("make_new_window", fromDialog(true,
                            "Open a fresh BDV window — leave any existing viewers untouched."))
                    .postSet("interpolate", fromDialog(false, "Avoiding interpolation facilitates the visualisation of voxels."))
                    .launch());

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvRaw, 1, 1, 0, 0);

        Step.say("Setting the window title.");
        skipScript(script, () ->
            CmdExecutor.of(context, BdvTitleSetCommand.class)
                    .withLauncher(treeLauncher())
                    .postSet("title", fromDialog("BDV - Raw Sources",
                            "Naming this window so the upcoming sync command can address it by title."))
                    .launch());
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
                + menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        Step.say("We fill it as before.");
        CmdExecutor.of(context, DatasetFromBioFormatsCreateCommand.class)
                .withLauncher(treeLauncher())
                .postSet("datasetname", fromDialog(psfName))
                .postSet("unit", fromDialog("MICROMETER"))
                .postSet("files", fromDialog(new File[]{filePSF}, "Browsing to the 400 nm PSF which matches this dataset."))
                .postSet("split_rgb_channels", fromDialog(false, "No need to modify the other parameters"))
                .postSet("auto_pyramidize", fromDialog(true))
                .postSet("plane_origin_convention", fromDialog("TOP LEFT"))
                .postSet("disable_memo", fromDialog(false))
                .launch();
        Ui.rawPause(1500);
        Step.say("Both datasets now live in the Sources tree, ready to feed the deconvolution command.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Richardson-Lucy Deconvolution (Tiled & Lazy)",
                "Now we run lazy Richardson-Lucy: 20 iterations, 256-by-256-by-64 "
                        + "blocks with 16-pixel overlap, non-circulant, light regularization.");
        Step.say("Right-clicking the Sources root — this is the heavy step; everything else "
                + "has been setup for this. Walking to: "
                + menuPath(SourcesDeconvolveCommand.class, 2));
        Step.say("Walking through the deconvolve dialog — the parameters that shape the result.");
        CmdExecutor.of(context, SourcesDeconvolveCommand.class)
                .withLauncher(treeLauncher())
                .postSet("sources", fromDialog(datasetName,
                        "Picking the HeLa dataset as the input to deconvolve."))
                .postSet("psf", fromDialog(psfName,
                        "Picking the matching 400 nm PSF as the kernel."))
                .postSet("output_pixel_type", fromDialog("Keep Pixel Type Of Original Image",
                        "Keep original pixel type (16-bit) to save memory."))
                .postSet("suffix", fromDialog("_deconvolved",
                        "Suffix appended to each new source name so the two sets stay distinguishable."))
                .postSet("block_size_x", fromDialog(256,
                        "Processing by blocks of 256×256×64 pixels."))
                .postSet("block_size_y", fromDialog(256))
                .postSet("block_size_z", fromDialog(64))
                .postSet("overlap_size", fromDialog(16,
                        "16-pixel overlap between blocks to hide tile boundaries in the result."))
                .postSet("num_iterations", fromDialog(20,
                        "20 Richardson-Lucy iterations — a sweet spot for this PSF and noise level."))
                .postSet("non_circulant", fromDialog(true,
                        "Non-circulant mode avoids wrap-around artefacts at block edges."))
                .postSet("regularization_factor", fromDialog(0.001f,
                        "Light regularization — just enough to tame noise amplification."))
                .postSet("n_threads", fromDialog(4,
                        "Four streams in flight at once to keep the GPU pool saturated."))
                .launch();
        Step.say("The command returns immediately — the deconvolution itself is lazy, "
                + "computed block by block on the GPU pool only when requested (by a viewer for instance).");
        Step.end();

        Ui.rawPause(1500);

        Step.begin("Show Deconvolved Dataset",
                "The deconvolved sources land under Other Sources. We open them in a "
                        + "second BDV beneath the raw view, sized to share the same width.");
        bdvArea.place(bdvRaw, 2, 1, 0, 0);

        SourceService bss = context.service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        Step.say("Let's double click the \"Other Sources\" node to expand it and display the generated sources.");

        Tree.doubleClickPath(jtree, "Sources>Other Sources");
        Ui.rawPause(1500);

        Step.say("Right-clicking the \"Other Sources\" branch — walking to: "
                + menuPath(SingleBdvSourcesShowCommand.class, 2));
        Step.say("We pick the deconvolved sources that just landed under \"Other Sources\".");
        Step.say("Walking through the show-sources dialog again.");
        skipScript(script, () ->
            CmdExecutor.of(context, SingleBdvSourcesShowCommand.class)
                    .withLauncher(treeLauncher("Other Sources"))
                    .postSet("auto_contrast", fromDialog(false,
                            "Auto-contrast off this time — we'll carry the raw ranges over manually for a fair side-by-side."))
                    .postSet("adjust_view", fromDialog(true,
                            "Re-centering on the deconvolved sources."))
                    .postSet("make_new_window", fromDialog(true,
                            "New window so the raw view stays untouched next to it."))
                    .postSet("interpolate", fromDialog(false))
                    .launch());

        Frame bdvDeconvolved = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvDeconvolved, 2, 1, 1, 0);

        Step.say("Setting the window title.");
        skipScript(script, () ->
            CmdExecutor.of(context, BdvTitleSetCommand.class)
                    .withLauncher(treeLauncher())
                    .postSet("title", fromDialog("BDV - Deconvolved Sources",
                            "Naming this one so the sync command can pair the two windows by title."))
                    .launch());

        Ui.rawPause(2000);
        Step.end();

        Step.begin("Synchronize Raw And Deconvolved Sources Display",
                "Finally, we set both windows to track each other "
                        + "live — pan, zoom and timepoint stay in sync as we navigate.");
        Step.say("Right-clicking the Sources root — walking to: "
                + menuPath(ViewSynchronizeCommand.class, 2));
        Step.say("Walking through the synchronize dialog.");
        skipScript(script, () ->
            CmdExecutor.of(context, ViewSynchronizeCommand.class)
                    .withLauncher(treeLauncher())
                    .postSet("bdvhs", fromDialog(new String[]{"BDV - Raw Sources", "BDV - Deconvolved Sources"},
                            "Selecting both BDV windows we just named — the two viewers that will share state."))
                    .postSet("bvvhs", fromDialog(null))
                    .postSet("synchronizetime", fromDialog(true,
                            "Lock timepoints too — scrubbing one slider scrubs both."))
                    .launch());
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
                + menuPath(KheopsExportSourcesCommand.class, 2));
        Step.say("We pick the deconvolved sources — they live under \"Other Sources\".");
        Step.say("Walking through the OME-TIFF export dialog.");
        CmdExecutor.of(context, KheopsExportSourcesCommand.class)
                .withLauncher(treeLauncher("Other Sources"))
                .postSet("range_frames", fromDialog(tIni+":"+tEnd,
                        "Export only timepoints "+tIni+" to "+tEnd+" — fast enough to demo."))
                .postSet("file", fromDialog(exportFile,
                        "Saving straight to the desktop."))
                .postSet("unit", fromDialog("MICROMETER",
                        "Coordinates in micrometers."))
                .postSet("override_voxel_size", fromDialog(false, "No need to override the voxel sizes: they will be carried over since the deconvolved data is already well calibrated."+
                        " As a result the parameters below are ignored"))
                .postSet("n_resolution_levels", fromDialog(1, "No need to build multiple resolution levels since the dataset is small enough."))
                .postSet("tile_size_x", fromDialog(512, "Pretty standard block saving size: 512 x 512 pixels"))
                .postSet("tile_size_y", fromDialog(512))
                .postSet("n_threads", fromDialog(4,
                        "Four fetcher threads so IO doesn't bottleneck the GPU pool."))
                .postSet("compression", fromDialog("LZW", "Let's use LZW lossless compression"))
                .launch();
        Step.say("Each block is computed by the GPU on demand and streamed straight to disk — "
                + "the Tasks panel reports Okprogress.");

        // TREE-mode .launch() returns once OK is clicked; the export then
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
            Ui.rawPause(10000);
        }
        Step.say("Done — the OME-TIFF is ready on the desktop.");
        Ui.rawPause(15000);
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked, so it
        // lands in timeline.json's outro block and in youtube-description.md.
        outro("You've seen tiled, lazy Richardson-Lucy deconvolution end to end — "
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

    // ===== Local helpers bridging the gaps fiji-robot hasn't ported yet ======

    /**
     * Boots a fresh Fiji (with the BIOP update site, via {@link DemoHelper}),
     * captures its {@link Context} into {@link #context}, places the main frame
     * on the target screen and returns it. Replaces the old toolkit's
     * {@code GenerateDocAssetsCommon.boot()} + {@code videos.fiji.Fiji.startFiji()}:
     * fiji-robot's {@code ij1.Fiji} deliberately drives an already-running IJ
     * rather than booting one, so the boot stays a project concern here.
     */
    private static Frame bootFiji() {
        Step.begin("boot-fiji",
                "We start Fiji and place its main window on the secondary monitor.");
        Step.say("Booting a fresh Fiji instance with the BIOP update site enabled — "
                + "this brings in BDV Playground and the deconvolution command.");
        ImageJ ij = new ImageJ();
        context = ij.getContext();
        DemoHelper.startFiji(ij);
        Frame frame = Fiji.waitForIJFrame(Timings.FRAME_WAIT_TIMEOUT_MS);
        if (frame == null) {
            throw new IllegalStateException("ImageJ main frame not available after "
                    + Timings.FRAME_WAIT_TIMEOUT_MS + "ms");
        }
        Ui.placeFrame(frame);
        Ui.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);
        Step.end();
        return frame;
    }

    /**
     * Opens the BDV-Playground "BDV Sources" tree from the search bar, sizes and
     * places it, optionally minimizing the Fiji main frame. Port of the old
     * {@code GenerateDocAssetsCommon.showBdvTreeDisplay}.
     */
    private static void showBdvTree(boolean minimize, Frame fijiFrame) {
        Step.begin("open-bdv-tree",
                "From Fiji's search bar we open the BDV Playground Sources tree, "
                        + "where every loaded source will land.");
        Step.say("Using Fiji's search bar, we type 'show bdv play' to find BigDataViewer Playground tree display.");
        CmdExecutor.of(context, SourceServiceShowCommand.class)
                .withLauncher(searchLauncher("show bdv play"))
                .launch();
        Frame bdvSources = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvSources, 450, 600);
        if (minimize) {
            Ui.minimizeWithMouse(fijiFrame);
        }
        Ui.dragFrame(bdvSources, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y + 100);
        Step.end();
    }

    /**
     * Run {@code launch} with the {@link GroovyScript} recorder detached, so the
     * launched command does not contribute a line to the embedded reproduction
     * script — the new-API equivalent of the old {@code .skipInScript()}. The
     * recorder's accumulated context (imports, hoisted params, prior bodies) is
     * preserved across the detach/re-attach, so only this one launch is dropped.
     */
    private static void skipScript(GroovyScript script, Runnable launch) {
        GroovyScript.uninstall();
        try {
            launch.run();
        } finally {
            script.install();
        }
    }

    /**
     * The SciJava-registered menu path of {@code commandClass} as a
     * {@code " > "}-delimited string starting at segment {@code iStart} — for
     * embedding the real menu route inside narration. Replaces the old
     * {@code CommandExecutor.menuPath(...)}.
     */
    private static String menuPath(Class<? extends Command> commandClass, int iStart) {
        CommandService cs = context.service(CommandService.class);
        MenuPath path = cs.getCommand(commandClass).getMenuPath();
        StringBuilder sb = new StringBuilder();
        for (int i = iStart; i < path.size(); i++) {
            if (i > iStart) sb.append(" > ");
            sb.append(path.get(i).getName());
        }
        return sb.toString();
    }

    /** Set the title-card metadata and push it into {@link Timeline}. */
    private static void intro(String title, String tagline, String... highlights) {
        introTitle = title;
        introTagline = tagline;
        introHighlights = highlights == null ? new ArrayList<>() : Arrays.asList(highlights);
        Timeline.setIntro(title, tagline, introHighlights);
    }

    /**
     * Set the closing-card metadata and write {@code youtube-description.md}.
     * The command list is harvested from {@link CatchCommandForDocPostProcessor}
     * — the SciJava postprocessor that catches every command this demo ran —
     * and converted into fiji-robot {@link CommandRef} DTOs.
     */
    private static void outro(String closing, String... links) {
        List<CommandRef> commands = new ArrayList<>();
        for (CatchCommandForDocPostProcessor.CommandDoc cd
                : CatchCommandForDocPostProcessor.usedCommands.values()) {
            commands.add(new CommandRef(cd.name, cd.version, cd.codeUrl));
        }
        commands.sort(Comparator.comparing(c -> c.name == null ? "" : c.name));
        List<String> linkList = links == null ? new ArrayList<>() : Arrays.asList(links);
        Timeline.setOutro(closing, commands, linkList);
        writeYoutubeDescription(closing, commands, linkList);
    }

    /** YouTube-description file written next to the clips by {@link #outro}. */
    private static final String DESCRIPTION_FILENAME = "youtube-description.md";

    /**
     * Render the harvested metadata as a Markdown blob ready to paste into the
     * YouTube description box. Port of {@code videos.Demo.writeYoutubeDescription}.
     */
    private static void writeYoutubeDescription(String closing,
                                                List<CommandRef> commands,
                                                List<String> links) {
        StringBuilder sb = new StringBuilder();
        String heading = (introTitle != null && !introTitle.isEmpty()) ? introTitle : Assets.demoName();
        sb.append("# ").append(heading).append("\n\n");
        if (introTagline != null && !introTagline.isEmpty()) {
            sb.append(introTagline).append("\n\n");
        }
        if (introHighlights != null && !introHighlights.isEmpty()) {
            sb.append("## Why it matters\n\n");
            for (String h : introHighlights) sb.append("- ").append(h).append("\n");
            sb.append("\n");
        }
        if (closing != null && !closing.isEmpty()) {
            sb.append(closing).append("\n\n");
        }
        if (commands != null && !commands.isEmpty()) {
            sb.append("## Commands used in this video\n\n");
            for (CommandRef c : commands) {
                sb.append("- **").append(c.name).append("**");
                if (c.version != null && !c.version.isEmpty()) {
                    sb.append(" (v").append(c.version).append(")");
                }
                if (c.sourceUrl != null && !c.sourceUrl.isEmpty()) {
                    sb.append(" — ").append(c.sourceUrl);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (links != null && !links.isEmpty()) {
            sb.append("## Links\n\n");
            for (String l : links) sb.append("- ").append(l).append("\n");
            sb.append("\n");
        }
        File f = new File(Assets.dir(), DESCRIPTION_FILENAME);
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + f, e);
        }
    }

    // ===== Local Layout (not yet ported to fiji-robot) =======================

    /**
     * Grid-based placement for AWT {@link Frame}s relative to the target screen,
     * backed by fiji-robot's {@link Ui#resizeFrame}/{@link Ui#dragFrame}. A
     * verbatim port of the old {@code videos.core.Layout} — the library lists
     * {@code Layout} as a roadmap item but hasn't shipped it yet.
     */
    private static final class Layout {

        private final int dx, dy, width, height;

        private Layout(int dx, int dy, int width, int height) {
            this.dx = dx;
            this.dy = dy;
            this.width = width;
            this.height = height;
        }

        static Layout area(int dx, int dy, int width, int height) {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width / height must be > 0, got "
                        + width + " / " + height);
            }
            return new Layout(dx, dy, width, height);
        }

        void place(Frame frame, int rows, int cols, int row, int col) {
            place(frame, rows, cols, row, col, 1, 1);
        }

        void place(Frame frame, int rows, int cols, int row, int col,
                   int rowSpan, int colSpan) {
            if (rows <= 0 || cols <= 0) {
                throw new IllegalArgumentException("rows / cols must be > 0, got "
                        + rows + " / " + cols);
            }
            if (rowSpan <= 0 || colSpan <= 0) {
                throw new IllegalArgumentException("rowSpan / colSpan must be > 0, got "
                        + rowSpan + " / " + colSpan);
            }
            if (row < 0 || col < 0 || row + rowSpan > rows || col + colSpan > cols) {
                throw new IllegalArgumentException("Cell (" + row + "," + col
                        + ") span (" + rowSpan + "," + colSpan
                        + ") does not fit in grid " + rows + "x" + cols);
            }
            int cellW = width / cols;
            int cellH = height / rows;
            Ui.resizeFrame(frame, colSpan * cellW, rowSpan * cellH);
            Ui.dragFrame(frame, dx + col * cellW, dy + row * cellH);
        }
    }
}