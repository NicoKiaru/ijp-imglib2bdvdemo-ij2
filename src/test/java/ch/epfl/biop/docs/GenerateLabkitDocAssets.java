package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitOpenCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
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
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;
import sc.fiji.bdvpg.service.SourceServices;

import javax.swing.*;
import java.awt.Frame;
import java.io.File;
import java.util.List;

/**
 * Generates a Labkit pixel-classification demo for video documentation.
 * <p>
 * Uses the Hela-Kyoto-1-Timepoint-LLS7 dataset (single timepoint, two
 * channels). Both channels are sent to Labkit so the classifier can leverage
 * joint intensity to separate background, cytoplasm and nucleus.
 *
 * <p>Workflow (each step a separate clip + script line + auto-screenshot):
 * <ol>
 *   <li>boot Fiji and open the BDV Playground tree;</li>
 *   <li>load the raw LLS7 dataset via Bio-Formats and show it in BDV;</li>
 *   <li>open Labkit on the two channels;</li>
 *   <li>(manual) create labels, draw scribbles per class, train classifier;</li>
 *   <li>(manual) save the trained classifier to {@link #CLASSIFIER_FILE} and
 *       leave the Labkit window open (we minimize it programmatically);</li>
 *   <li>apply the classifier with {@link SourcesLabkitClassifyCommand}, show
 *       the result in a second BDV next to the raw view, and synchronize.</li>
 * </ol>
 *
 * <p>Manual phases use {@link Step#waitForUser(String)} — a small floating
 * dialog (placed off the recording screen if multi-monitor) that holds the
 * demo while the recorder draws / clicks in Labkit, with the recording still
 * rolling so the manual actions appear on video.</p>
 *
 * <p>Classifier path defaults to {@code <user.dir>/labkit-cyto-nuc.classifier};
 * override with {@code -Dlabkit.classifier.file=...}.</p>
 */
public class GenerateLabkitDocAssets {

    static {
        LegacyInjector.preinit();
    }

    static final File CLASSIFIER_FILE = new File(
            System.getProperty("labkit.classifier.file",
                    new File(System.getProperty("user.dir"), "labkit-cyto-nuc.classifier").getAbsolutePath())
    );

    public static void main(String[] args) throws Exception {

        // Flip both to true while iterating on this demo — every CommandExecutor
        // runs programmatically (no Robot, no dialog) and Ui frame placement /
        // pauses become instant. Flip back to false to record.
        CommandExecutor.FORCE_PROGRAMMATIC = false;
        Ui.FAST_MODE = false;

        // Asset capture toggles. Flip these to false while iterating on the
        // demo; flip them back on for the recording pass. Output lands in
        // target/video-assets/LabkitVideoDemo/ as NNN-step.{mp4,png} plus a
        // single timeline.json transcript.
        Assets.session("LabkitVideoDemo");
        ScreenRecorder.ENABLED = true;//true;
        Screenshotter.ENABLED = true;//true;
        // Set Step.SKIP_MANUAL = true to race through the automated phases
        // (the wait-for-Continue dialogs return immediately — useful only
        // when the classifier file already exists and you want to test the
        // apply / show / sync tail).
        Step.SKIP_MANUAL = false;

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        Demo.intro("Pixel Classification with Labkit in Fiji",
                "Train a Labkit classifier on a multi-channel LLS7 light-sheet dataset "
                        + "and re-apply it lazily, tile by tile, through BigDataViewer.",
                "Trains on multi-channel light-sheet data — joint intensity per class",
                "Saved classifier re-applies lazily — only tiles in view get classified",
                "Side-by-side BDV viewers, synchronized in pan / zoom / timepoint");

        System.out.println("Classifier path: " + CLASSIFIER_FILE.getAbsolutePath());

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        // Single placement area for every window that lands to the right of
        // the BDV Sources tree. Raw fills the full area while it's alone,
        // Labkit fills it while training, then we re-partition into a 1×2
        // side-by-side: raw on the left, classified on the right.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y, 800, 700);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay(true);

        Step.begin("Open Hela Demo Dataset",
                "We open a Zeiss LLS7 HeLa Kyoto dataset through Bio-Formats — "
                        + "two channels, single timepoint, lazily backed by the .czi.");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", fileDataset.getName(),
                        "We name the dataset after the file itself — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "Browsing to the LLS7 HeLa file.")
                .arg("split_rgb_channels", false,
                        "RGB is irrelevant for this dataset.")
                .arg("auto_pyramidize", true,
                        "(Auto-pyramidize will be ignored because the dataset is small.)")
                .arg("disable_memo", false,
                        "We keep memoization — fast file opening on subsequent runs.")
                .arg("plane_origin_convention", "TOP LEFT",
                        "Origin convention does not matter — there is only one tile in this dataset.")
                .sayOnDialog("Walking through the Bio-Formats importer dialog.")
                .viaTreePath()
                .execute();

        Ui.rawPause(3000); // Time for the dataset to be available - no get() when not programmatic
        Step.say("The CZI is opened lazily — both channels show up under the dataset node "
                + "in the Sources tree, ready to feed Labkit.");
        Ui.rawPause(1000);
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
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvRaw, 1, 2, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Sources",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .execute();
        Ui.rawPause(1000);
        Step.end();

        Step.begin("Open Labkit For Pixel Classification",
                "We send both channels to Labkit. Labkit opens its own window where we'll "
                        + "draw scribbles for three classes: background, cytoplasm and nucleus.");
        Step.say("Sending both channels to Labkit lets the classifier use joint intensity "
                + "(cytoplasm vs nucleus signal) to separate the classes.");
        Step.say("Right-clicking the sources to classify — walking to: "
                + CommandExecutor.menuPath(SourcesLabkitOpenCommand.class, 2));
        CommandExecutor.of(SourcesLabkitOpenCommand.class)
                .arg("resolution_level", 0,
                        "Highest resolution (level 0) — this dataset only has one level.")
                .sayOnDialog("Walking through the Labkit open dialog.")
                .viaTreePath(fileDataset.getName(),
                        "We pick the raw CZI sources to classify.")
                .execute();

        // Find the Labkit window once it appears and place it in the BDV
        // area, covering the BDV Raw view. The BDV Sources tree on the left
        // stays visible.
        Frame labkitFrame = Ui.waitForFrame("Labkit -", Timings.FRAME_WAIT_TIMEOUT_MS);
        if (labkitFrame == null) {
            throw new IllegalStateException("Labkit window (title starting 'Labkit -') did not appear");
        }
        Ui.pause(1500);
        bdvArea.place(labkitFrame, 1, 1, 0, 0);
        Step.end();

        Step.begin("Create Labels In Labkit",
                "We create three labels in Labkit's Labeling panel — background, cytoplasm and nucleus.",
                "In Labkit's Labeling panel (right side), CREATE THREE LABELS:\n"
                        + "  1. background\n"
                        + "  2. cytoplasm\n"
                        + "  3. nucleus\n\n"
                        + "Do not draw scribbles yet.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when the labels are set up.");
        Step.end();

        Step.begin("Draw Background Scribbles",
                "We scribble the background class on empty / dark regions outside the cells.",
                "Select the 'background' label and use the brush to scribble over\n"
                        + "empty / dark regions outside the cells. A few short strokes on\n"
                        + "two or three slices is enough.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when background scribbles are done.");
        Step.end();

        Step.begin("Draw Cytoplasm Scribbles",
                "We scribble the cytoplasm class — bright signal that is clearly outside the nuclei.",
                "Select the 'cytoplasm' label and scribble over cytoplasmic regions —\n"
                        + "bright but clearly outside the nucleus. Toggle channel visibility in\n"
                        + "the Image panel if it helps spot cytoplasm-specific signal.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when cytoplasm scribbles are done.");
        Step.end();

        Step.begin("Draw Nucleus Scribbles",
                "We scribble the nucleus class — bright signal inside the cells.",
                "Select the 'nucleus' label and scribble over nuclei.\n\n"
                        + "Do not train yet — we'll do that in the next step.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when nucleus scribbles are done.");
        Step.end();

        Step.begin("Train The Classifier",
                "We train the classifier on the three classes. The live overlay shows the "
                        + "segmentation as Labkit refines its model.",
                "Click 'Train Classifier' (Segmentation menu or toolbar) and wait for the\n"
                        + "live overlay. If the result is poor, add scribbles where it fails and retrain.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue once the overlay reads correctly.");
        Step.end();

        Step.begin("Save The Classifier",
                "We save the trained classifier so it can be re-applied later, then minimize "
                        + "Labkit so the BDV tree is visible again for the next steps.",
                "In Labkit: Segmentation > Save Classifier as...\n\n"
                        + "Save the classifier EXACTLY at this path:\n\n"
                        + "  " + CLASSIFIER_FILE.getAbsolutePath() + "\n\n"
                        + "Do NOT close Labkit — we'll minimize it automatically.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue once the classifier file has been written.");
        // Recording is still active — minimize Labkit visibly so the viewer
        // sees the BDV tree become accessible again.
        Ui.minimizeWithMouse(labkitFrame);
        Ui.pause(1000);
        if (!CLASSIFIER_FILE.exists()) {
            throw new IllegalStateException(
                    "Classifier file not found at " + CLASSIFIER_FILE.getAbsolutePath()
                            + " — did you save it to the correct path in the previous step?");
        }
        Step.end();

        Step.begin("Apply The Classifier",
                "We re-apply the saved classifier to the same sources — lazy, so each tile "
                        + "is classified on demand as it comes into view.");
        Step.say("Right-clicking the dataset — this is the heavy step; everything else "
                + "has been setup for this. Walking to: "
                + CommandExecutor.menuPath(SourcesLabkitClassifyCommand.class, 2));
        CommandExecutor.of(SourcesLabkitClassifyCommand.class)
                .arg("classifier_file", CLASSIFIER_FILE,
                        "Re-using the classifier file we just saved.")
                .arg("resolution_level", 0,
                        "Apply at the highest resolution level (0).")
                .arg("suffix", "_classified",
                        "Suffix appended to each new source name so the two sets stay distinguishable.")
                .arg("use_gpu", true,
                        "GPU acceleration on for the dense feature pass.")
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

        Step.say("Let's double click the \"Other Sources\" node to expand it and display the generated source.");

        Tree.doubleClickPath(jtree,"Other Sources");

        // Shrink the raw viewer to the left column so the classified one
        // can take the right column.
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
                .execute();

        Frame bdvClassified = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvClassified, 1, 2, 0, 1);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Classified Sources",
                        "Naming this one so the sync command can pair the two windows by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
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
                .arg("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Classified Sources"},
                        "Selecting both BDV windows we just named — the two viewers that will share state.")
                .arg("bvvhs", null)
                .arg("synchronizetime", true,
                        "Lock timepoints too — scrubbing one slider scrubs both.")
                .sayOnDialog("Walking through the synchronize dialog.")
                .viaTreePath()
                .execute();
        Step.say("Holding the final frame while the classifier streams in tile by tile — "
                + "the side-by-side reveals where the model agrees with the raw signal and where it doesn't.");

        Frame syncWindow = Ui.waitForFrame("Close window to stop synchronization", 6000);
        if (syncWindow != null) {
            Ui.resizeFrame(syncWindow, 450, 100);
            Ui.dragFrame(syncWindow, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        }

        // The classifier streams in over several seconds — hold long enough
        // that the final frame shows both windows fully resolved before the
        // clip ends.
        Ui.rawPause(45000);
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked, so it
        // lands in timeline.json's outro block and in youtube-description.md.
        Demo.outro("You've seen Labkit pixel classification end to end — "
                        + "from raw multi-channel data to a synchronized side-by-side "
                        + "of the trained classifier output, all streamed lazily tile by tile.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "Sample LLS7 dataset: https://zenodo.org/records/14203207",
                "Labkit: https://imagej.net/plugins/labkit/");

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}