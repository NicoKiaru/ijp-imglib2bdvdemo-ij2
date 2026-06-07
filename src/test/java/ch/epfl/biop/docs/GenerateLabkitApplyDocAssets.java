package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.Demo;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import ch.epfl.biop.docs.videos.widgets.Tree;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;
import sc.fiji.bdvpg.service.SourceServices;

import javax.swing.JTree;
import java.awt.Frame;
import java.awt.Insets;
import java.io.File;
import java.util.List;

/**
 * Generates the second of two Labkit demos: applying a pre-trained classifier.
 * <p>
 * Reuses the classifier file written by {@link GenerateLabkitClassifierDocAssets}
 * (path is {@link LabkitDoc#CLASSIFIER_FILE}). The dataset import is run
 * programmatically — it's pure setup, not the point of the video, so the Robot
 * dialog walk is skipped and the visible time is just the new node appearing
 * in the BDV tree.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>boot Fiji, open the BDV Playground tree, open the Tasks window;</li>
 *   <li>open the raw LLS7 dataset programmatically (fast, no Robot);</li>
 *   <li>show it in a first BDV titled "BDV - Raw Sources";</li>
 *   <li>apply the saved classifier with {@link SourcesLabkitClassifyCommand};</li>
 *   <li>show the classified source in a second BDV, set display range 0..3;</li>
 *   <li>synchronize both viewers;</li>
 *   <li>export the classified sources to OME-TIFF, watching the Tasks panel.</li>
 * </ol>
 */
public class GenerateLabkitApplyDocAssets {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {

        CommandExecutor.FORCE_PROGRAMMATIC = false;
        Ui.FAST_MODE = false;

        Assets.session("LabkitApplyVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        Demo.intro("Applying a Labkit Classifier Lazily with BigDataViewer-Playground",
                "Re-apply a pre-trained Labkit classifier to a multi-channel "
                        + "dataset — tile by tile through BigDataViewer — then export the result to OME-TIFF.",
                "Saved classifier re-applies lazily — only tiles in view get classified",
                "Side-by-side synchronized viewers",
                "Final classified volume streamed straight to OME-TIFF");

        if (!LabkitDoc.CLASSIFIER_FILE.exists()) {
            throw new IllegalStateException(
                    "Classifier file not found at " + LabkitDoc.CLASSIFIER_FILE.getAbsolutePath()
                            + " — run GenerateLabkitClassifierDocAssets first (or set "
                            + "-Dlabkit.classifier.file=... to point at an existing one).");
        }
        System.out.println("Classifier path: " + LabkitDoc.CLASSIFIER_FILE.getAbsolutePath());

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(LabkitDoc.DATASET_URL);
        String datasetName = FilenameUtils.removeExtension(fileDataset.getName());

        File exportFile = new File("C:\\Users\\Nicolas\\Desktop",
                datasetName + "_classified.ome.tiff");
        if (exportFile.exists() && !exportFile.delete()) {
            throw new IllegalStateException(
                    "Could not delete pre-existing export file: " + exportFile);
        }

        // Hoisted #@File params for the auto-generated script.groovy — so a
        // viewer of the recording can re-run the headless equivalent from the
        // Fiji script editor without editing paths inline.
        GroovyScript.nameFile(fileDataset,                "datasetFile",    "Input dataset");
        GroovyScript.nameFile(LabkitDoc.CLASSIFIER_FILE,  "classifierFile", "Labkit classifier");
        GroovyScript.nameFile(exportFile,                 "exportFile",     "Output OME-TIFF");

        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y, 800, 700);

        GenerateDocAssetsCommon.boot();

        Step.begin("Open Tasks Window",
                "Before we shrink Fiji window, we open the Tasks panel — "
                        + "we'll watch the OME-TIFF export progress here at the end of the video.");
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
                "We open the raw LLS7 Kyoto dataset through Bio-Formats — "
                        + "in this video we run the import programmatically "
                        + "(no dialog walk). The new node simply appears under the Sources tree.");
        Step.say("Running the Bio-Formats importer programmatically — no Robot, no dialog. "
                + "The script.groovy transcript still gets the equivalent cs.run(...) line.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", fileDataset.getName(),
                        "We name the dataset after the file itself — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "The LLS7 HeLa file.")
                .arg("split_rgb_channels", false,
                        "RGB is irrelevant for this dataset.")
                .arg("auto_pyramidize", true,
                        "(Auto-pyramidize will be ignored because the dataset is small.)")
                .arg("disable_memo", false,
                        "We keep memoization — fast file opening on subsequent runs.")
                .arg("plane_origin_convention", "TOP LEFT",
                        "Origin convention does not matter — there is only one tile in this dataset.")
                .programmatic()
                .execute();
        Step.say("Both channels show up under the dataset node in the Sources tree, "
                + "ready to feed the classifier.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Show Hela Dataset",
                "We display the raw dataset in a BigDataViewer window — "
                        + "we'll set its title to 'BDV - Raw Sources'.");
        Step.say("Right-clicking the CZI node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast so the cells are visible immediately.")
                .arg("adjust_view", true,
                        "Re-centering the view on the loaded sources.")
                .arg("make_new_window", true,
                        "Open a fresh BDV window — leave any existing viewers untouched.")
                .arg("interpolate", false,
                        "No interpolation.")
                .sayOnDialog("Walking through the show-sources dialog.")
                .viaTreePath(fileDataset.getName(),
                        "We select the raw CZI sources for display.")
                .skipInScript()
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvRaw, 1, 2, 0, 0);

        Step.say("Right-clicking the CZI node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Sources",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Ui.rawPause(1000);
        Step.end();

        Step.begin("Apply The Classifier",
                "We apply the pre-trained classifier to the raw sources — lazy, so each tile "
                        + "is classified on demand as it comes into view.");
        Step.say("Right-clicking the dataset. Walking to: "
                + CommandExecutor.menuPath(SourcesLabkitClassifyCommand.class, 2));
        CommandExecutor.of(SourcesLabkitClassifyCommand.class)
                .arg("classifier_file", LabkitDoc.CLASSIFIER_FILE,
                        "The classifier file we trained in the companion video.")
                .arg("resolution_level", 0,
                        "Apply at the highest resolution level (0).")
                .arg("suffix", "_classified",
                        "Suffix appended to each new source name so the two sets stay distinguishable.")
                .arg("use_gpu", true,
                        "GPU acceleration (needs CLIJ update sites).")
                .sayOnDialog("Walking through the classify dialog — the parameters that shape the result.")
                .viaTreePath(fileDataset.getName(),
                        "We pick the raw sources as input to the classifier.")
                .execute();
        Step.say("The command returns immediately — classification happens lazily, one tile at "
                + "a time, only as the viewer requests pixels.");
        Step.end();

        Ui.rawPause(1500);

        Step.begin("Show Classified Sources",
                "The classified source lands under Other Sources. We open it in a second BDV "
                        + "next to the raw view, with display range 0..3 to show the three classes.");

        SourceService bss = Fiji.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        Step.say("Let's double click the \"Other Sources\" node to expand it and reveal the generated source.");
        Tree.doubleClickPath(jtree, "Other Sources");

        bdvArea.place(bdvRaw, 1, 2, 0, 0);
        Ui.rawPause(1500);

        Step.say("Right-clicking the new \"Other Sources\" branch (where the classified source "
                + "landed) — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", false,
                        "Auto-contrast off — we'll set the integer label range by hand.")
                .arg("adjust_view", true,
                        "Re-centering on the classified sources.")
                .arg("make_new_window", true,
                        "New window so the raw view stays untouched next to it.")
                .arg("interpolate", false,
                        "No interpolation — labels are integer indices; we want crisp class boundaries.")
                .sayOnDialog("Walking through the show-sources dialog again.")
                .viaTreePath("Other Sources",
                        "We pick the classified source that just landed under \"Other Sources\".")
                .skipInScript()
                .execute();

        Frame bdvClassified = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvClassified, 1, 2, 0, 1);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Classified Source",
                        "Naming this one so the sync command can pair the two windows by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();

        Step.say("Opening the source card so we can dial in the label range — mind the location of the blue arrow!");
        BdvHandle classifiedBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        Bdv.setCardPanelExpanded(classifiedBdv, true);
        Ui.rawPause(800);
        Step.say("Setting display range 0..3 on every classified source — the classifier emits "
                + "label indices 0/1/2 for background/cytoplasm/nucleus, so this maps each class to a distinct grey.");
        List<SourceAndConverter<?>> classifiedSources = classifiedBdv.getViewerPanel().state().getSources();
        for (SourceAndConverter<?> s : classifiedSources) {
            Bdv.selectSourceInCard(classifiedBdv, s.getSpimSource().getName());
            Bdv.setDisplayRange(classifiedBdv, 0, 3);
        }
        Step.say("Collapsing the source card so the viewer canvas takes back the full window.");
        Bdv.setCardPanelExpanded(classifiedBdv, false);
        Ui.rawPause(1000);
        Step.end();

        Step.begin("Synchronize Raw And Classified Views",
                "Finally, we set both windows to track each other "
                        + "live — pan, zoom and timepoint stay in sync as we navigate.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class, 2));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .arg("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Classified Source"},
                        "Selecting both BDV windows we just named — the two viewers that will share state.")
                .arg("bvvhs", null)
                .arg("synchronizetime", true,
                        "Lock timepoints too — scrubbing one slider scrubs both.")
                .sayOnDialog("Walking through the synchronize dialog.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.say("Holding the final frame while the classifier streams in tile by tile — "
                + "the side-by-side reveals where the model agrees with the raw signal and where it doesn't.");

        Frame syncWindow = Ui.waitForFrame("Close window to stop synchronization", 6000);
        if (syncWindow != null) {
            Ui.resizeFrame(syncWindow, 450, 100);
            Ui.dragFrame(syncWindow, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        }

        Ui.rawPause(20000);
        Step.end();

        Step.begin("Save Classified Sources To OME-TIFF",
                "We export the classified sources as a multi-resolution OME-TIFF — written "
                        + "straight to the desktop, ready to be reopened in any OME-TIFF reader.");
        if (syncWindow != null) {
            Ui.closeWithMouse(syncWindow);
            Ui.rawPause(500);
        }
        Step.say("Right-clicking the \"Other Sources\" branch — walking to: "
                + CommandExecutor.menuPath(KheopsExportSourcesCommand.class, 2));
        CommandExecutor.of(KheopsExportSourcesCommand.class)
                .arg("range_channels","", "We leave the first fields empty to collect all C,Z and T")
                .arg("range_slices","")
                .arg("range_frames","")
                .arg("file", exportFile,
                        "Saving straight to the desktop.")
                .arg("unit", "MICROMETER",
                        "Coordinates in micrometers.")
                .arg("override_voxel_size", false,
                        "No need to override the voxel sizes — they carry over from the raw sources.")
                .arg("n_resolution_levels", 1,
                        "Single resolution level — it's not a good idea to linearly interpolate labels.")
                .arg("tile_size_x", 512,
                        "Pretty standard block saving size: 512 x 512 pixels.")
                .arg("tile_size_y", 512)
                .arg("n_threads", 4,
                        "Four fetcher threads so IO doesn't bottleneck the classifier.")
                .arg("compression", "LZW",
                        "LZW lossless compression — integer label maps compress very well.")
                .sayOnDialog("Walking through the OME-TIFF export dialog.")
                .viaTreePath("Other Sources",
                        "We pick the classified sources — they live under \"Other Sources\".")
                .execute();
        Step.say("Each block is computed by the classifier on demand and streamed straight to disk — "
                + "the Tasks panel reports progress.");

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
        Step.say("Done — the classified OME-TIFF is ready on the desktop.");
        Ui.rawPause(15000);
        Step.end();

        Demo.outro("You've seen a pre-trained Labkit classifier re-applied end to end — "
                        + "from raw multi-channel data to a synchronized side-by-side and a "
                        + "multi-resolution OME-TIFF on disk, all streamed lazily tile by tile.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "Sample LLS7 dataset: https://zenodo.org/records/14203207",
                "Labkit: https://imagej.net/plugins/labkit/",
                "Companion video (training): GenerateLabkitClassifierDocAssets");

        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}