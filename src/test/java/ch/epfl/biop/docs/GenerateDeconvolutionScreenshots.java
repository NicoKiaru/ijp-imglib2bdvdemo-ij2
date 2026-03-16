package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import bvv.vistools.BvvHandle;
import org.scijava.module.Module;
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

/**
 * Generates screenshots for the processing_images/deconvolution.md documentation page.
 *
 * Both BDV windows (raw and deconvolved) are kept open simultaneously, their views
 * are synchronised, and both are captured at the end.
 *
 * Workflow:
 *   1. Open raw LLS7 HeLa image (both channels) in BDV.
 *   2. Run lazy Richardson-Lucy deconvolution (both channels, 200 nm PSF).
 *   3. Open deconvolved sources in a second BDV window, placed beside the raw window.
 *   4. Run ViewSynchronizeCommand — both windows now track each other live.
 *   5. Pause — navigate to a good region, wait for GPU tiles, then Continue to capture both.
 *
 * Requirements: CLIJ2 and an OpenCL-compatible GPU.
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/processing_images/images
 *
 * Run from IDE: right-click → Run 'GenerateDeconvolutionScreenshots.main()'
 * Run with custom output dir: set VM option -Ddoc.output.dir=/absolute/path/to/images
 *
 * Expected output files (prefix_WindowTitle.png):
 *   deconvolution_BigDataViewer-Raw.png
 *   deconvolution_BigDataViewer-Deconvolved.png
 */
public class GenerateDeconvolutionScreenshots {

    static {
        LegacyInjector.preinit();
    }

    static final File OUTPUT_DIR = new File(
            System.getProperty("doc.output.dir",
                    "../bigdataviewer-playground-documentation/docs/source/processing_images/images")
    );

    public static void main(String[] args) throws Exception {
        System.out.println("Output directory: " + OUTPUT_DIR.getAbsolutePath());

        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);
        DemoHelper.expandTreeView(ij);

        // Load the LLS7 HeLa dataset (2 channels). Downloads on first run (~500 MB), cached locally after.
        System.out.println("Loading LLS7 HeLa dataset...");
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ij.context());

        // Load the 200 nm PSF for the LLS7 acquisition.
        System.out.println("Loading 200 nm PSF dataset...");
        SourceAndConverter<?>[] psfSources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_PSF_200NM, ij.context());

        // Auto-adjust brightness on the raw sources.
        for (SourceAndConverter<?> source : sources) {
            new BrightnessAutoAdjuster<>(source, 0).run();
        }

        ij.get(SourceBdvDisplayService.class).setDefaultBdvSupplier(
                new DefaultBdvSupplier(new SerializableBdvOptions()));

        // -------------------------------------------------------------------------
        // Step 1: Open the raw image in BDV
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 1: Opening raw image ===");
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

        // -------------------------------------------------------------------------
        // Step 2: Run deconvolution — registers virtual sources instantly
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 2: Registering lazy deconvolution sources ===");
        Module deconvModule = ij.command().run(SourcesDeconvolveCommand.class, true,
                "sources", sources,
                "psf", psfSources[0],
                "num_iterations", 20,
                "block_size_x", 256,
                "block_size_y", 256,
                "block_size_z", 64,
                "overlap_size", 16,
                "non_circulant", true,
                "regularization_factor", 0.002f,
                "output_pixel_type", "Float",
                "suffix", "_deconvolved",
                "n_threads", 4
        ).get();

        SourceAndConverter<?>[] deconvSources =
                (SourceAndConverter<?>[]) deconvModule.getOutput("sources_out");

        // -------------------------------------------------------------------------
        // Step 3: Open the deconvolved sources in a second BDV window beside the raw
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 3: Opening deconvolved image ===");
        BdvHandle bdvDeconv = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", deconvSources,
                "adjust_view", false,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvDeconv).setTitle("BigDataViewer-Deconvolved");
            int rawRight = BdvHandleHelper.getJFrame(bdvRaw).getX()
                    + BdvHandleHelper.getJFrame(bdvRaw).getWidth();
            BdvHandleHelper.getJFrame(bdvDeconv).setLocation(rawRight + 10, 50);
            new ViewerTransformAdjuster(bdvDeconv, sources[0]).run();
        });
        DemoHelper.waitFor(1000);

        // -------------------------------------------------------------------------
        // Step 4: Live synchronisation — navigate in either window and both follow.
        // The command opens a small popup; synchronisation stops when it is closed.
        // Do NOT call .get() here — the command blocks until the popup is dismissed.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 4: Starting live view synchronisation ===");
        ij.command().run(ViewSynchronizeCommand.class, true,
                "bdvhs", new BdvHandle[]{bdvRaw, bdvDeconv},
                "bvvhs", new BvvHandle[]{},
                "synchronizetime", false);
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Pause + capture: navigate (both views follow), wait for GPU, then capture
        // -------------------------------------------------------------------------
        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("deconvolution")
                .waitMs(3000)
                .filter("BigDataViewer-")
                .pause("Views are now synchronised — navigate in either window.\n\n" +
                        "Find a region with clear subcellular detail.\n" +
                        "Tiles in the deconvolved window are computed on-the-fly by the GPU;\n" +
                        "wait until the view is fully rendered.\n\n" +
                        "Adjust brightness/contrast in each window as needed,\n" +
                        "then click Continue to capture BOTH windows.")
                .capture();

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.closeWindow(bdvRaw);
            BdvHandleHelper.closeWindow(bdvDeconv);
        });
        DemoHelper.waitFor(500);

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }
}