package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitOpenCommand;
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
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.Frame;
import java.io.File;
import java.util.List;

/**
 * Generates a Labkit pixel-classification demo for video documentation.
 * <p>
 * Uses the LATTICE_HELA_SKEWED dataset (single timepoint, 2 channels). Both
 * channels are sent to Labkit so the classifier can leverage joint intensity
 * to separate background, cytoplasm and nucleus.
 *
 * <p>Workflow (each step a separate clip + script line + auto-screenshot):
 * <ol>
 *   <li>boot Fiji and open the BDV Playground tree;</li>
 *   <li>load the raw LLS7 dataset via Bio-Formats and show it in BDV;</li>
 *   <li>open Labkit on the two channels;</li>
 *   <li>(manual) create labels, draw scribbles per class, train classifier;</li>
 *   <li>(manual) save the trained classifier to {@link #CLASSIFIER_FILE} and
 *       close the Labkit window;</li>
 *   <li>apply the classifier with {@link SourcesLabkitClassifyCommand}, show
 *       the result in a second BDV beneath the raw view, and synchronize.</li>
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

        // Asset capture toggles. Flip these to false while iterating; flip
        // them back on for the recording pass. Output lands in
        // target/video-assets/LabkitVideoDemo/ as NNN-step.{mp4,png} plus a
        // single timeline.json transcript.
        Assets.session("LabkitVideoDemo");
        ScreenRecorder.ENABLED = false;//true;
        Screenshotter.ENABLED = false;//true;
        // Set Step.SKIP_MANUAL = true to race through the automated phases
        // (the wait-for-Continue dialogs return immediately — useful only
        // when the classifier file already exists and you want to test the
        // apply / show / sync tail).
        Step.SKIP_MANUAL = false;

        System.out.println("Classifier path: " + CLASSIFIER_FILE.getAbsolutePath());

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

        // Single placement area for every window that lands to the right of
        // the BDV Sources tree. Raw fills the full area while it's alone,
        // Labkit fills it while training, then we re-partition into a 1×2
        // side-by-side: raw on the left, classified on the right.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150, 900, 600);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay(false);

        Step.begin("load-raw-czi",
                "We load a Zeiss LLS7 HeLa Kyoto dataset through Bio-Formats — "
                        + "two channels, single timepoint, lazily backed by the .czi.");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", fileDataset.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{fileDataset},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .sayOnDialog("Filling the importer dialog: micrometre units, pyramid generation on, "
                        + "and the top-left plane-origin convention (not relevant for this demo).")
                .viaTreePath()
                .execute();
        Step.say("The CZI is opened lazily — both channels show up under the dataset node "
                + "in the Sources tree, ready to feed Labkit.");
        Ui.rawPause(3000); // Time to open the dataset - no get when not programmatic
        Step.end();

        Step.begin("show-raw-bdv",
                "Right-clicking the dataset shows it in a new BigDataViewer window — "
                        + "we'll call this one Raw Sources and place it next to the tree.");
        Step.say("Right-clicking the CZI node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", true)
                .sayOnDialog("Dialog: auto-contrast on so the cells are visible immediately, "
                        + "make_new_window=true for a fresh viewer.")
                .viaTreePath(fileDataset.getName())
                .execute();

        Frame bdvRaw = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        // Raw fills the whole BDV area while it's the only viewer.
        bdvArea.place(bdvRaw, 1, 2, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Raw Sources")
                .sayOnDialog("Renaming the window to \"BDV - Raw Sources\" so the upcoming sync "
                        + "command can address it by title.")
                .viaTreePath()
                .execute();
        Step.end();

        BdvHandle rawBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        List<SourceAndConverter<?>> sources = rawBdv.getViewerPanel().state().getSources();
        SourceAndConverter<?>[] sourceArray = sources.toArray(new SourceAndConverter<?>[0]);

        //Step.say("Launching SourcesLabkitOpenCommand — we don't await its Future because Labkit "
        //        + "blocks until its window closes, and we want to drive the next steps while it stays open.");
        // Labkit blocks until the user closes its window — fire it and let
        // the demo continue. We never await: at the end of the labkit phase
        // the window is minimized (not closed) so the tree is visible for
        // the apply-classifier step without losing the live Labkit session.
        // The returned Future is intentionally discarded.
        /*CommandService cs = Fiji.context().getService(CommandService.class);
        cs.run(SourcesLabkitOpenCommand.class, true,
                "sources", sourceArray,
                "resolution_level", 0);*/

        Step.begin("open-labkit",
                "We send both channels to Labkit. Labkit opens its own window where we'll "
                        + "draw scribbles for three classes: background, cytoplasm and nucleus.");
        Step.say("Pulling both sources and sending the pair to Labkit "
                + "lets the classifier use joint intensity (cytoplasm vs nucleus signal) to separate the classes.");
        Step.say("Right-clicking the sources to classify — walking to: "
                + CommandExecutor.menuPath(SourcesLabkitOpenCommand.class, 2));
        CommandExecutor.of(SourcesLabkitOpenCommand.class)
                .args("resolution_level", 0)
                .sayOnDialog("Dialog: the dataset has a single resolution level: 0 (= highest resolution)")
                .viaTreePath(fileDataset.getName())
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

        Step.begin("setup-labkit-labels",
                "We create three labels in Labkit's Labeling panel — background, cytoplasm and nucleus.",
                "In Labkit's Labeling panel (right side), CREATE THREE LABELS:\n"
                        + "  1. background\n"
                        + "  2. cytoplasm\n"
                        + "  3. nucleus\n\n"
                        + "Do not draw scribbles yet.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when the labels are set up.");
        Step.end();

        Step.begin("draw-background",
                "We scribble the background class on empty / dark regions outside the cells.",
                "Select the 'background' label and use the brush to scribble over\n"
                        + "empty / dark regions outside the cells. A few short strokes on\n"
                        + "two or three slices is enough.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when background scribbles are done.");
        Step.end();

        Step.begin("draw-cytoplasm",
                "We scribble the cytoplasm class — bright signal that is clearly outside the nuclei.",
                "Select the 'cytoplasm' label and scribble over cytoplasmic regions —\n"
                        + "bright but clearly outside the nucleus. Toggle channel visibility in\n"
                        + "the Image panel if it helps spot cytoplasm-specific signal.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue when cytoplasm scribbles are done.");
        Step.end();

        Step.begin("draw-nucleus-and-train",
                "We scribble the nucleus class, then train the classifier. The live overlay "
                        + "shows the segmentation as Labkit refines its model.",
                "Select the 'nucleus' label and scribble over nuclei.\n\n"
                        + "Then click 'Train Classifier' (Segmentation menu or toolbar) and\n"
                        + "wait for the live overlay. If the result is poor, add scribbles\n"
                        + "where it fails and retrain.\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue once the overlay reads correctly.");
        Step.end();

        Step.begin("save-and-minimize-labkit",
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

        Step.begin("apply-classifier",
                "We re-apply the saved classifier to the same sources — lazy, so each tile "
                        + "is classified on demand as it comes into view.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourcesLabkitClassifyCommand.class));
        CommandExecutor.of(SourcesLabkitClassifyCommand.class)
                .args("classifier_file", CLASSIFIER_FILE,
                        "resolution_level", 0,
                        "suffix", "_classified",
                        "use_gpu", true)
                .sayOnDialog("Dialog: the classifier file we just saved, GPU acceleration on for "
                        + "the dense feature pass.")
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.say("The command returns immediately — classification happens lazily, one tile at "
                + "a time, only as the viewer requests pixels.");
        Step.end();

        Step.begin("show-classified-bdv",
                "The classified source lands under Other Sources. We open it in a second BDV "
                        + "next to the raw view, with display range 0..3 to show the three classes.");
        // Shrink the raw viewer to the left column so the classified one
        // can take the right column.
        bdvArea.place(bdvRaw, 1, 2, 0, 0);
        Ui.rawPause(1500);

        Step.say("Right-clicking the new \"Other Sources\" branch (where the classified source "
                + "landed) — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", false,
                        "interpolate", false,
                        "make_new_window", true)
                .sayOnDialog("Dialog: auto-contrast off since we'll set an integer range for the "
                        + "labels by hand, make_new_window=true for a fresh viewer.")
                .viaTreePath("Other Sources")
                .execute();

        Frame bdvClassified = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvClassified, 1, 2, 0, 1);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Classified Sources")
                .sayOnDialog("Renaming it \"BDV - Classified Sources\" so the sync command can find it by title.")
                .viaTreePath()
                .execute();

        Step.say("Setting display range 0..3 on every classified source — the classifier emits "
                + "label indices 0/1/2 for background/cytoplasm/nucleus, so this maps each class to a distinct grey.");
        BdvHandle classifiedBdv = SourceServices.getBdvDisplayService().getActiveBdv();
        Bdv.setCardPanelExpanded(classifiedBdv, true);
        List<SourceAndConverter<?>> classifiedSources = classifiedBdv.getViewerPanel().state().getSources();
        for (SourceAndConverter<?> s : classifiedSources) {
            Bdv.selectSourceInCard(classifiedBdv, s.getSpimSource().getName());
            Bdv.setDisplayRange(classifiedBdv, 0, 3);
        }
        Bdv.setCardPanelExpanded(classifiedBdv, false);
        Step.end();

        Step.begin("sync-views",
                "Finally, ViewSynchronizeCommand keeps both windows tracking each other live — "
                        + "as we navigate the raw image, the classifier output streams in beside it.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .args("bdvhs", new String[]{"BDV - Raw Sources", "BDV - Classified Sources"},
                        "bvvhs", null,
                        "synchronizetime", true)
                .sayOnDialog("Dialog: both BDV titles selected, synchronizetime=true — pan, zoom "
                        + "and timepoint stay in sync, so the classifier output is read against the raw signal at the same view.")
                .viaTreePath()
                .execute();
        Step.say("Holding the final frame for ~20 s while the classifier streams in tile by tile — "
                + "the side-by-side reveals where the model agrees and where it doesn't.");
        // The classifier streams in over several seconds — hold so the
        // final frame shows both windows fully resolved.
        Ui.rawPause(20000);
        Step.end();

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots and timeline.json "
                + "are written. Click Continue to dismiss.");
    }
}
