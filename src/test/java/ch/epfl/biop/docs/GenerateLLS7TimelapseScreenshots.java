package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.command.workflow.lls7.LLS7CropCommand;
import ch.epfl.biop.command.workflow.lls7.LLS7ZDriftCompensateCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.command.CommandModule;
import org.scijava.module.Module;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bvv.BvvSourcesShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.viewer.ViewerAdapter;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.viewer.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.viewer.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.viewer.bvv.BvvCreator;
import sc.fiji.bdvpg.viewer.bvv.BvvHandleHelper;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Generates screenshots for the workflows/lls7_timelapse.md documentation page.
 *
 * Uses the LATTICE_HELA_SKEWED_TIMELAPSE dataset (60 timepoints) and
 * LATTICE_PSF_400NM for deconvolution.
 *
 * Workflow:
 *   1. Open raw LLS7 timelapse in BDV — capture raw view.
 *   2. Run lazy Richardson-Lucy deconvolution — capture raw vs deconvolved side by side.
 *   3. Run Z-drift compensation — capture tree view showing corrected sources.
 *   4. Run interactive Crop 3D — pause for user to set bounding box, capture result.
 *   5. Show cropped deconvolved result in BVV (3D volume rendering).
 *
 * Requirements: CLIJ2 and an OpenCL-compatible GPU.
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/workflows/images
 *
 * Run from IDE: right-click → Run 'GenerateLLS7TimelapseScreenshots.main()'
 *
 * Expected output files (prefix_WindowTitle.png):
 *   lls7_step1_BigDataViewer_Raw_Timelapse.png
 *   lls7_step2_BigDataViewer_Raw.png
 *   lls7_step2_BigDataViewer_Deconvolved.png
 *   lls7_step3_BigDataViewer_Drift_Corrected.png
 *   lls7_step4_BigDataViewer_Crop_Selection.png
 *   lls7_step5_BigVolumeViewer_Cropped_Deconvolved.png
 */
public class GenerateLLS7TimelapseScreenshots {

    static {
        LegacyInjector.preinit();
    }

    static final File OUTPUT_DIR = new File(
            System.getProperty("doc.output.dir",
                    "../bigdataviewer-playground-documentation/docs/source/workflows/images")
    );

    public static void main(String[] args) throws Exception {
        System.out.println("Output directory: " + OUTPUT_DIR.getAbsolutePath());

        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);
        DemoHelper.expandTreeView(ij);

        // Load the LLS7 HeLa timelapse dataset (60 timepoints, 2 channels).
        System.out.println("Loading LLS7 HeLa timelapse dataset...");
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED_TIMELAPSE, ij.context());

        // Load the 400 nm PSF for deconvolution.
        System.out.println("Loading 400 nm PSF dataset...");
        SourceAndConverter<?>[] psfSources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_PSF_400NM, ij.context());

        // Auto-adjust brightness on the raw sources.
        for (SourceAndConverter<?> source : sources) {
            new BrightnessAutoAdjuster<>(source, 0).run();
        }

        ij.get(SourceBdvDisplayService.class).setDefaultBdvSupplier(
                new DefaultBdvSupplier(new SerializableBdvOptions()));

        // =====================================================================
        // Step 1: Open raw timelapse in BDV
        // =====================================================================
        System.out.println("\n=== Step 1: Opening raw timelapse ===");
        BdvHandle bdvRaw = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", sources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvRaw).setTitle("BigDataViewer-Raw Timelapse");
            BdvHandleHelper.getJFrame(bdvRaw).setLocation(50, 50);
            new ViewerTransformAdjuster(bdvRaw, sources[0]).run();
        });

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("lls7_step1")
                .waitMs(4000)
                .filter("BigDataViewer-Raw Timelapse")
                .pause("Step 1 — Raw LLS7 timelapse in BDV.\n\n" +
                        "Navigate to a good overview of the skewed data.\n" +
                        "Adjust brightness/contrast if needed.\n\n" +
                        "Click Continue to capture.")
                .capture();

        // =====================================================================
        // Step 2: Deconvolution — raw vs deconvolved side by side
        // =====================================================================
        System.out.println("\n=== Step 2: Running lazy deconvolution ===");
        Module deconvModule = ij.command().run(SourcesDeconvolveCommand.class, true,
                "sources", sources,
                "psf", psfSources[0],
                "num_iterations", 30,
                "block_size_x", 256,
                "block_size_y", 256,
                "block_size_z", 64,
                "overlap_size", 16,
                "non_circulant", true,
                "regularization_factor", 0.002f,
                "output_pixel_type", "Keep Pixel Type Of Original Image",
                "suffix", "_deconvolved",
                "n_threads", 4
        ).get();

        SourceAndConverter<?>[] deconvSources =
                (SourceAndConverter<?>[]) deconvModule.getOutput("sources_out");

        DemoHelper.expandTreeView(ij);

        // Open deconvolved sources in a second BDV window
        System.out.println("Opening deconvolved image...");
        BdvHandle bdvDeconv = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", deconvSources,
                "adjust_view", false,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        // Rename raw window for step 2 screenshots
        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvRaw).setTitle("BigDataViewer-Raw");
            BdvHandleHelper.getJFrame(bdvDeconv).setTitle("BigDataViewer-Deconvolved");
            int rawRight = BdvHandleHelper.getJFrame(bdvRaw).getX()
                    + BdvHandleHelper.getJFrame(bdvRaw).getWidth();
            BdvHandleHelper.getJFrame(bdvDeconv).setLocation(rawRight + 10, 50);
            new ViewerTransformAdjuster(bdvDeconv, sources[0]).run();
        });
        DemoHelper.waitFor(1000);

        // Synchronise the two views
        System.out.println("Starting live view synchronisation...");
        ij.command().run(ViewSynchronizeCommand.class, true,
                "bdvhs", new BdvHandle[]{bdvRaw, bdvDeconv},
                "bvvhs", new BvvHandle[]{},
                "synchronizetime", true);
        DemoHelper.waitFor(500);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("lls7_step2")
                .waitMs(4000)
                .filter("BigDataViewer-")
                .pause("Step 2 — Raw vs Deconvolved.\n\n" +
                        "Views are synchronised — navigate in either window.\n" +
                        "Find a region with clear subcellular detail.\n" +
                        "Wait until deconvolved tiles are fully rendered by the GPU.\n\n" +
                        "Adjust brightness/contrast in each window as needed,\n" +
                        "then click Continue to capture BOTH windows.")
                .capture();

        // Close the deconvolved window for now
        SwingUtilities.invokeAndWait(() -> BdvHandleHelper.closeWindow(bdvDeconv));
        DemoHelper.waitFor(500);

        // =====================================================================
        // Step 3: Z-drift correction
        // =====================================================================
        System.out.println("\n=== Step 3: Running Z-drift compensation ===");

        // Restore the raw window title
        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvRaw).setTitle("BigDataViewer-Drift Corrected");
        });

        ij.command().run(LLS7ZDriftCompensateCommand.class, true,
                "model_source", sources[0],
                "sources_to_correct", sources,
                "threshold", 130.0,
                "mode", "Mutate",
                "debug", false
        ).get();

        DemoHelper.expandTreeView(ij);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("lls7_step3")
                .waitMs(4000)
                .filter("BigDataViewer-Drift Corrected")
                .pause("Step 3 — After Z-drift correction.\n\n" +
                        "Scrub through timepoints (use the slider) to verify\n" +
                        "that drift has been corrected.\n\n" +
                        "Click Continue to capture.")
                .capture();

        // =====================================================================
        // Step 4: Crop and Deskew
        // =====================================================================
        System.out.println("\n=== Step 4: Interactive Crop 3D ===");

        // Rename window for the crop step
        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvRaw).setTitle("BigDataViewer-Crop Selection");
        });

        // Run the Crop command — this is interactive (shows a 3D bounding box)
        // Do NOT call .get() — the command blocks until the user confirms the crop.
        Future<CommandModule> module = ij.command().run(LLS7CropCommand.class, true,
                "bdvh", bdvRaw,
                "image_name", "LLS7_cropped",
                "sources", deconvSources);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("lls7_step4")
                .waitMs(4000)
                .filter("BigDataViewer-Crop Selection")
                .pause("Step 4 — Crop 3D bounding box.\n\n" +
                        "Adjust the 3D bounding box to encompass your region of interest.\n" +
                        "Confirm the crop selection in the dialog.\n\n" +
                        "Click Continue to capture.")
                .capture();

        module.get();

        DemoHelper.expandTreeView(ij);

        // Close the BDV crop window
        SwingUtilities.invokeAndWait(() -> BdvHandleHelper.closeWindow(bdvRaw));
        DemoHelper.waitFor(500);

        // =====================================================================
        // Step 5: Show cropped deconvolved result in BVV (3D volume rendering)
        // =====================================================================
        System.out.println("\n=== Step 5: Opening cropped result in BVV ===");

        // Retrieve the cropped sources — they were registered by the crop command
        // with the name "LLS7_cropped" in the Source service.
        List<SourceAndConverter<?>> allSources =
                ij.get(SourceService.class).getSources();
        SourceAndConverter<?>[] croppedSources = allSources.stream()
                .filter(s -> s.getSpimSource().getName().contains("cropped"))
                .toArray(SourceAndConverter<?>[]::new);


        // Create BVV window and show the cropped deconvolved sources
        BvvHandle bvv = new BvvCreator(BvvOptions.options()).get();

        ij.command().run(BvvSourcesShowCommand.class, true,
                "bvvh", bvv,
                "sources", croppedSources,
                "adjust_view", true
        ).get();

        BvvHandleHelper.setWindowTitle(bvv.getBvvHandle(), "BigVolumeViewer-Cropped Deconvolved");

        DemoHelper.waitFor(1000);

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("lls7_step5")
                .waitMs(4000)
                .filter("BigVolumeViewer-Cropped Deconvolved")
                .pause("Step 5 — Cropped deconvolved result in BVV (3D volume rendering).\n\n" +
                        "Rotate and adjust the 3D view to show the result.\n" +
                        "Adjust brightness/contrast as needed.\n\n" +
                        "Click Continue to capture.")
                .capture();

        //SwingUtilities.invokeAndWait(() -> BvvHandleHelper.closeWindow(bvv));
        DemoHelper.waitFor(500);

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }
}
