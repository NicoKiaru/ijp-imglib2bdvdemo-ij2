package ch.epfl.biop.docs;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitOpenCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.Demo;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;

import java.awt.Frame;
import java.io.File;

/**
 * Generates the first of two Labkit demos: the manual training half.
 * <p>
 * Workflow:
 * <ol>
 *   <li>boot Fiji and open the BDV Playground tree;</li>
 *   <li>load the raw LLS7 dataset via Bio-Formats and show it in BDV;</li>
 *   <li>open Labkit on the two channels;</li>
 *   <li>(manual) create three labels, draw scribbles per class, train classifier;</li>
 *   <li>(manual) save the trained classifier to {@link LabkitDoc#CLASSIFIER_FILE}.</li>
 * </ol>
 *
 * <p>The classifier file written here is the input of
 * {@link GenerateLabkitApplyDocAssets}, the apply-half companion video.</p>
 *
 * <p>{@link GroovyScript} output is disabled — every action that matters in this
 * demo is manual (drawing scribbles, training), so a reproducible script would be
 * meaningless. The apply half ships the script.</p>
 */
public class GenerateLabkitClassifierDocAssets {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {

        CommandExecutor.FORCE_PROGRAMMATIC = false;
        Ui.FAST_MODE = false;

        Assets.session("LabkitClassifierVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;
        Step.SKIP_MANUAL = false;

        // No script — every meaningful action in this demo is manual.
        GroovyScript.ENABLED = false;

        Demo.intro("Training a Labkit Classifier in Fiji",
                "Train a Labkit pixel classifier on a multi-channel LLS7 light-sheet dataset, "
                        + "then save it to disk for lazy re-application in the companion video.",
                "Trains on multi-channel light-sheet data — joint intensity per class",
                "Three classes: background, cytoplasm, nucleus",
                "Saved classifier feeds the apply-half video that reuses it tile by tile");

        System.out.println("Classifier path: " + LabkitDoc.CLASSIFIER_FILE.getAbsolutePath());

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(LabkitDoc.DATASET_URL);

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

        Ui.rawPause(3000);
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
        bdvArea.place(bdvRaw, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Sources",
                        "Naming this window so the apply-half video can find it later.")
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
                "We save the trained classifier so it can be re-applied later — the apply-half "
                        + "video picks it up from this exact path.",
                "In Labkit: Segmentation > Save Classifier as...\n\n"
                        + "Save the classifier EXACTLY at this path:\n\n"
                        + "  " + LabkitDoc.CLASSIFIER_FILE.getAbsolutePath() + "\n\n"
                        + "Click OK to start recording.");
        Step.waitForUser("Click Continue once the classifier file has been written.");
        if (!LabkitDoc.CLASSIFIER_FILE.exists()) {
            throw new IllegalStateException(
                    "Classifier file not found at " + LabkitDoc.CLASSIFIER_FILE.getAbsolutePath()
                            + " — did you save it to the correct path in the previous step?");
        }
        Step.end();

        Demo.outro("You've trained a Labkit classifier end to end on multi-channel light-sheet data. "
                        + "The saved classifier file is now ready to be re-applied — see the companion "
                        + "video for the lazy, tile-by-tile apply pass.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "Sample LLS7 dataset: https://zenodo.org/records/14203207",
                "Labkit: https://imagej.net/plugins/labkit/");

        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}