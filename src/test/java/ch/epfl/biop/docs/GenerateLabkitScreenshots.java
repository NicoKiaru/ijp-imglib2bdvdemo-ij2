package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitOpenCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.command.CommandModule;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.viewer.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.viewer.bdv.supplier.SerializableBdvOptions;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.concurrent.Future;

/**
 * Generates screenshots for the workflows/labkit.md documentation page.
 *
 * Uses the LATTICE_HELA_SKEWED dataset (single timepoint, 2 channels).
 * Both channels are sent to Labkit so the classifier can leverage the
 * joint intensity information to separate background, cytoplasm and nucleus.
 *
 * Workflow:
 *   1. Open raw LLS7 HeLa image (both channels) in BDV — capture raw view.
 *   2. Open Labkit on the sources — capture the Labkit window at the start of training.
 *   3. User draws background scribbles — capture.
 *   4. User draws cytoplasm scribbles — capture.
 *   5. User draws nucleus scribbles and clicks Train Classifier — capture with live overlay.
 *   6. User saves the classifier to a known path, then closes Labkit.
 *   7. Apply the saved classifier with SourcesLabkitClassifyCommand and show the
 *      classified result alongside the raw image in BDV (synchronised views).
 *
 * Because SourcesLabkitOpenCommand blocks until the Labkit window is closed,
 * we submit it as a Future and do the screenshot pauses while Labkit is up.
 * The Future is awaited (module.get()) after step 6.
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/workflows/images
 *
 * Classifier file is saved to:
 *   <user.dir>/labkit-cyto-nuc.classifier
 * (overridable via -Dlabkit.classifier.file=/absolute/path/to/file.classifier)
 *
 * Run from IDE: right-click → Run 'GenerateLabkitScreenshots.main()'
 *
 * Expected output files (prefix_WindowTitle.png):
 *   labkit_step1_BigDataViewer_Raw.png
 *   labkit_step2_Labkit_*.png          (just opened)
 *   labkit_step3_Labkit_*.png          (background scribbles)
 *   labkit_step4_Labkit_*.png          (cytoplasm scribbles)
 *   labkit_step5_Labkit_*.png          (nucleus scribbles + trained)
 *   labkit_step7_BigDataViewer_Raw.png
 *   labkit_step7_BigDataViewer_Classified.png
 */
public class GenerateLabkitScreenshots {

    static {
        LegacyInjector.preinit();
    }

    static final File OUTPUT_DIR = new File(
            System.getProperty("doc.output.dir",
                    "../bigdataviewer-playground-documentation/docs/source/workflows/images")
    );

    static final File CLASSIFIER_FILE = new File(
            System.getProperty("labkit.classifier.file",
                    new File(System.getProperty("user.dir"), "labkit-cyto-nuc.classifier").getAbsolutePath())
    );

    public static void main(String[] args) throws Exception {
        System.out.println("Output directory: " + OUTPUT_DIR.getAbsolutePath());
        System.out.println("Classifier path:  " + CLASSIFIER_FILE.getAbsolutePath());

        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);
        DemoHelper.expandTreeView(ij);

        // Load the LLS7 HeLa dataset (2 channels, single timepoint).
        System.out.println("Loading LLS7 HeLa dataset...");
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ij.context());

        // Auto-adjust brightness on both channels.
        for (SourceAndConverter<?> source : sources) {
            new BrightnessAutoAdjuster<>(source, 0).run();
        }

        ij.get(SourceBdvDisplayService.class).setDefaultBdvSupplier(
                new DefaultBdvSupplier(new SerializableBdvOptions()));

        // =====================================================================
        // Step 1: Open raw image in BDV
        // =====================================================================
        System.out.println("\n=== Step 1: Opening raw LLS7 image in BDV ===");
        BdvHandle bdvRaw = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", sources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvRaw).setTitle("BigDataViewer-Raw");
            BdvHandleHelper.getJFrame(bdvRaw).setLocation(50, 50);
            new ViewerTransformAdjuster(bdvRaw, sources[0]).run();
        });

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step1")
                .waitMs(4000)
                .filter("BigDataViewer-Raw")
                .pause("Step 1 — Raw LLS7 image in BDV (both channels).\n\n" +
                        "Navigate to a good overview where nuclei and cytoplasm are\n" +
                        "clearly visible. Adjust brightness/contrast if needed.\n\n" +
                        "Click Continue to capture.")
                .capture();

        // =====================================================================
        // Step 2: Open Labkit on the two channels
        // =====================================================================
        System.out.println("\n=== Step 2: Opening Labkit ===");

        // Do NOT call .get() — the command blocks until the user closes Labkit.
        // We capture screenshots at various stages of training while it is open,
        // then await completion at the end (step 6).
        Future<CommandModule> labkitFuture = ij.command().run(SourcesLabkitOpenCommand.class, true,
                "sources", sources,
                "resolution_level", 0);

        // Give Labkit a moment to open its window before taking the first shot.
        DemoHelper.waitFor(3000);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step2")
                .waitMs(3000)
                .filter("Labkit")
                .pause("Step 2 — Labkit has opened on the two LLS7 channels.\n\n" +
                        "Create THREE labels in the Labeling panel (right side):\n" +
                        "  1. background\n" +
                        "  2. cytoplasm\n" +
                        "  3. nucleus\n\n" +
                        "Do not draw scribbles yet — just set up the labels.\n" +
                        "Click Continue to capture the initial Labkit window.")
                .capture();

        // =====================================================================
        // Step 3: Background scribbles
        // =====================================================================
        System.out.println("\n=== Step 3: Background scribbles ===");

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step3")
                .waitMs(2000)
                .filter("Labkit")
                .pause("Step 3 — Draw BACKGROUND scribbles.\n\n" +
                        "Select the 'background' label and use the brush tool\n" +
                        "to scribble over empty / dark regions (outside cells).\n" +
                        "A few strokes on several slices are enough.\n\n" +
                        "Click Continue to capture.")
                .capture();

        // =====================================================================
        // Step 4: Cytoplasm scribbles
        // =====================================================================
        System.out.println("\n=== Step 4: Cytoplasm scribbles ===");

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step4")
                .waitMs(2000)
                .filter("Labkit")
                .pause("Step 4 — Draw CYTOPLASM scribbles.\n\n" +
                        "Select the 'cytoplasm' label and scribble over cytoplasmic\n" +
                        "regions (bright but clearly outside the nucleus).\n" +
                        "Use both channels if helpful — toggle channel visibility\n" +
                        "in the Image panel to spot cytoplasm-specific signal.\n\n" +
                        "Click Continue to capture.")
                .capture();

        // =====================================================================
        // Step 5: Nucleus scribbles + Train Classifier
        // =====================================================================
        System.out.println("\n=== Step 5: Nucleus scribbles and training ===");

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step5")
                .waitMs(3000)
                .filter("Labkit")
                .pause("Step 5 — Draw NUCLEUS scribbles and TRAIN.\n\n" +
                        "Select the 'nucleus' label and scribble over nuclei.\n" +
                        "Then click 'Train Classifier' (Segmentation menu or toolbar).\n" +
                        "Wait until the live segmentation overlay appears.\n\n" +
                        "If the result is poor, add more scribbles where it fails\n" +
                        "and retrain.\n\n" +
                        "Click Continue to capture Labkit with the trained overlay.")
                .capture();

        // =====================================================================
        // Step 6: Save classifier, close Labkit
        // =====================================================================
        System.out.println("\n=== Step 6: Save the classifier ===");

        DemoHelper.pause(
                "Step 6 — Save the trained classifier.\n\n" +
                "In Labkit: Segmentation > Save Classifier as...\n" +
                "Save it EXACTLY to this path:\n\n" +
                "  " + CLASSIFIER_FILE.getAbsolutePath() + "\n\n" +
                "Then CLOSE the Labkit window to resume the demo.\n\n" +
                "(No screenshot is taken here — the next step will capture BDV.)");

        // Block until the user closes Labkit.
        System.out.println("Waiting for Labkit to close...");
        labkitFuture.get();
        System.out.println("Labkit closed.");

        if (!CLASSIFIER_FILE.exists()) {
            throw new IllegalStateException(
                    "Classifier file not found at " + CLASSIFIER_FILE.getAbsolutePath() +
                    " — did you save it to the correct path in step 6?");
        }

        // =====================================================================
        // Step 7: Apply the saved classifier and show the result in BDV
        // =====================================================================
        System.out.println("\n=== Step 7: Applying classifier ===");

        SourceAndConverter<?> classifiedSource = (SourceAndConverter<?>) ij.command().run(
                SourcesLabkitClassifyCommand.class, true,
                "sources", sources,
                "classifier_file", CLASSIFIER_FILE,
                "resolution_level", 0,
                "suffix", "_classified",
                "use_gpu", true
        ).get().getOutput("source_out");

        DemoHelper.expandTreeView(ij);

        // Show the classified source in a second BDV window, beside the raw one.
        BdvHandle bdvClassified = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", new SourceAndConverter<?>[]{classifiedSource},
                "adjust_view", false,
                "auto_contrast", false,
                "interpolate", false,
                "make_new_window", true
        ).get().getOutput("bdvh");

        // 3 classes → display range 0..3 to show background / cytoplasm / nucleus LUTs.
        ij.get(sc.fiji.bdvpg.scijava.service.SourceService.class)
                .getConverterSetup(classifiedSource).setDisplayRange(0, 3);

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvClassified).setTitle("BigDataViewer-Classified");
            int rawRight = BdvHandleHelper.getJFrame(bdvRaw).getX()
                    + BdvHandleHelper.getJFrame(bdvRaw).getWidth();
            BdvHandleHelper.getJFrame(bdvClassified).setLocation(rawRight + 10, 50);
            new ViewerTransformAdjuster(bdvClassified, sources[0]).run();
        });
        DemoHelper.waitFor(1000);

        // Synchronise the two views so navigation in one updates the other.
        System.out.println("Starting live view synchronisation...");
        ij.command().run(ViewSynchronizeCommand.class, true,
                "bdvhs", new BdvHandle[]{bdvRaw, bdvClassified},
                "bvvhs", new BvvHandle[]{},
                "synchronizetime", true);
        DemoHelper.waitFor(500);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("labkit_step7")
                .waitMs(4000)
                .filter("BigDataViewer-")
                .pause("Step 7 — Raw vs Classified (lazy segmentation).\n\n" +
                        "Views are synchronised. Navigate to a region where the\n" +
                        "classifier result is visibly correct (nuclei in one colour,\n" +
                        "cytoplasm in another, background in a third).\n" +
                        "Wait for the classified tiles to render.\n\n" +
                        "Click Continue to capture BOTH windows.")
                .capture();

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        System.out.println("Classifier saved at: " + CLASSIFIER_FILE.getAbsolutePath());
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }
}
