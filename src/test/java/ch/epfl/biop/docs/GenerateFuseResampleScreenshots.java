package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.process.SourcesPyramidizeCommand;
import ch.epfl.biop.command.process.resample.SourcesFuseAndResampleCommand;
import ch.epfl.biop.command.process.resample.SourcesGridModelMakeCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import bvv.vistools.BvvHandle;
import org.scijava.module.Module;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.viewer.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.viewer.bdv.supplier.SerializableBdvOptions;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Generates screenshots for the processing_images/fuse_resample.md documentation page.
 *
 * Workflow:
 *   1. Open the Dentate Gyrus tiled brain section dataset (BRAIN_SECTION_3DTILES, 2 channels).
 *   2. Display all tile sources in BDV window 1 ("BigDataViewer-Tiles").
 *   3. Define a resampling grid spanning all tiles at 0.25 µm XY / 0.4 µm Z.
 *   4. Fuse all tiles of channel 0 into one source; repeat for channel 1.
 *   5. Open the two fused channel sources in BDV window 2 ("BigDataViewer-Fused").
 *   6. Synchronise views so both windows track each other.
 *   7. Pause — adjust brightness and view in both windows, then capture.
 *   8. Pyramidize the fused sources and display in BDV window 3 ("BigDataViewer-Pyramidized").
 *   9. Pause — adjust, then capture.
 *
 * Channel splitting assumption:
 *   BioFormats loads LIF series as: series0_ch0, series0_ch1, series1_ch0, series1_ch1, ...
 *   Channel 0 → even indices (0, 2, 4, …); Channel 1 → odd indices (1, 3, 5, …).
 *   If this does not match your dataset, print the source names (see Step 1 output) and adjust
 *   the filterByChannelIndex helper below.
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/processing_images/images
 *
 * Run from IDE: right-click → Run 'GenerateFuseResampleScreenshots.main()'
 *
 * Expected output files (prefix_WindowTitle.png):
 *   fuse_resample_BigDataViewer-Tiles.png
 *   fuse_resample_BigDataViewer-Fused.png
 *   fuse_resample_BigDataViewer-Pyramidized.png
 */
public class GenerateFuseResampleScreenshots {

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

        // Load the Dentate Gyrus tiled dataset (2 channels, ~4 GB). Downloaded once and cached.
        System.out.println("Loading BRAIN_SECTION_3DTILES dataset...");
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, ij.context());

        // Print source names so you can verify the channel split is correct.
        System.out.println("Loaded " + sources.length + " sources:");
        for (int i = 0; i < sources.length; i++) {
            System.out.println("  [" + i + "] " + sources[i].getSpimSource().getName());
        }

        // Auto-adjust brightness on all tile sources.
        /*for (SourceAndConverter<?> source : sources) {
            new BrightnessAutoAdjuster<>(source, 0).run();
        }*/

        ij.get(SourceBdvDisplayService.class).setDefaultBdvSupplier(
                new BiopBdvSupplier(new BiopSerializableBdvOptions()));

        // -------------------------------------------------------------------------
        // Step 1: Open all tile sources in BDV window 1 to verify their placement
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 1: Opening raw tile sources ===");
        BdvHandle bdvTiles = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", sources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        BdvHandleHelper.getJFrame(bdvTiles).setTitle("BigDataViewer-Tiles");
        BdvHandleHelper.getJFrame(bdvTiles).setLocation(50, 50);
        new ViewerTransformAdjuster(bdvTiles, sources[0]).run();

        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Step 2: Define a resampling grid spanning all tiles (0.25 µm XY, 0.4 µm Z)
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 2: Defining resampling grid ===");
        Module gridModule = ij.command().run(SourcesGridModelMakeCommand.class, true,
                "sources", sources,
                "name", "grid_model",
                "vox_size_x", 0.25,
                "vox_size_y", 0.25,
                "vox_size_z", 0.4,
                "n_resolution_levels", 1,
                "n_timepoints", 1,
                "timepoint", 0,
                "downscale_x", 2,
                "downscale_y", 2,
                "downscale_z", 2
        ).get();

        SourceAndConverter<?> gridModel = (SourceAndConverter<?>) gridModule.getOutput("source_out");
        System.out.println("Grid model: " + gridModel.getSpimSource().getName());

        // -------------------------------------------------------------------------
        // Step 3: Split sources by channel and fuse each channel onto the grid.
        //   Channel 0 → even-indexed sources; Channel 1 → odd-indexed sources.
        //   Adjust filterByChannelIndex if needed after reviewing the names printed above.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 3: Fusing channel 0 ===");

        SourceAndConverter<?>[] ch0Sources = ij.get(SourceService.class).tree().root().getAt("BrainSection-Tiles").child("Channel").child("ch_0").sources();
        SourceAndConverter<?>[] ch1Sources = ij.get(SourceService.class).tree().root().getAt("BrainSection-Tiles").child("Channel").child("ch_1").sources();

        System.out.println("Channel 0: " + ch0Sources.length + " tiles");
        System.out.println("Channel 1: " + ch1Sources.length + " tiles");

        Module fuseModule0 = ij.command().run(SourcesFuseAndResampleCommand.class, true,
                "sources", ch0Sources,
                "model", gridModel,
                "name", "fused_ch0",
                "blending_mode", "AVERAGE",
                "interpolate", true,
                "reusemipmaps", false,
                "defaultmipmaplevel", 0,
                "cache", true,
                "cache_x", 512,
                "cache_y", 512,
                "cache_z", 1,
                "cache_bounds", -1,
                "n_threads", 4
        ).get();
        SourceAndConverter<?> fused0 = (SourceAndConverter<?>) fuseModule0.getOutput("source_out");
        //new BrightnessAutoAdjuster<>(fused0, 0).run();

        System.out.println("\n=== Step 3: Fusing channel 1 ===");
        Module fuseModule1 = ij.command().run(SourcesFuseAndResampleCommand.class, true,
                "sources", ch1Sources,
                "model", gridModel,
                "name", "fused_ch1",
                "blending_mode", "AVERAGE",
                "interpolate", true,
                "reusemipmaps", false,
                "defaultmipmaplevel", 0,
                "cache", true,
                "cache_x", 512,
                "cache_y", 512,
                "cache_z", 1,
                "cache_bounds", -1,
                "n_threads", 4
        ).get();
        SourceAndConverter<?> fused1 = (SourceAndConverter<?>) fuseModule1.getOutput("source_out");
        //new BrightnessAutoAdjuster<>(fused1, 0).run();

        SourceAndConverter<?>[] fusedSources = new SourceAndConverter<?>[]{fused0, fused1};

        // -------------------------------------------------------------------------
        // Step 4: Open fused sources in BDV window 2, placed beside the tiles window
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 4: Opening fused sources ===");
        BdvHandle bdvFused = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", fusedSources,
                "adjust_view", false,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        BdvHandleHelper.getJFrame(bdvFused).setTitle("BigDataViewer-Fused");
        int tilesRight = BdvHandleHelper.getJFrame(bdvTiles).getX()
                + BdvHandleHelper.getJFrame(bdvTiles).getWidth();
        BdvHandleHelper.getJFrame(bdvFused).setLocation(tilesRight + 10, 50);
        new ViewerTransformAdjuster(bdvFused, sources[0]).run();

        DemoHelper.waitFor(1000);

        // -------------------------------------------------------------------------
        // Step 5: Synchronise both views — navigate in either window and both follow.
        // Do NOT call .get() — the command blocks until the popup is dismissed.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 5: Synchronising views ===");
        ij.command().run(ViewSynchronizeCommand.class, true,
                "bdvhs", new BdvHandle[]{bdvTiles, bdvFused},
                "bvvhs", new BvvHandle[]{},
                "synchronizetime", false);
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Pause + capture: tiles (left) and fused (right)
        // -------------------------------------------------------------------------
        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("fuse_resample")
                .waitMs(2000)
                .filter("BigDataViewer-Tiles")
                .pause("Left: raw tiles — Right: fused result.\n\n" +
                        "Navigate to a region showing clear tile boundaries and the merged result.\n" +
                        "Adjust brightness/contrast in each window as needed.\n\n" +
                        "Click Continue to capture the TILES window.")
                .capture();

        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("fuse_resample")
                .waitMs(2000)
                .filter("BigDataViewer-Fused")
                .pause("Now capturing the FUSED window.\n" +
                        "Make sure both windows show the same view region.\n\n" +
                        "Click Continue to capture.")
                .capture();

        BdvHandleHelper.closeWindow(bdvTiles);
        BdvHandleHelper.closeWindow(bdvFused);

        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Step 6: Pyramidize the fused sources for multi-resolution viewing
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 6: Pyramidizing fused sources ===");
        Module pyramidModule = ij.command().run(SourcesPyramidizeCommand.class, true,
                "sources", fusedSources
        ).get();
        SourceAndConverter<?>[] pyramidizedSources =
                (SourceAndConverter<?>[]) pyramidModule.getOutput("sources_out");

        // -------------------------------------------------------------------------
        // Step 7: Open pyramidized sources in a third BDV window
        // -------------------------------------------------------------------------
        System.out.println("\n=== Step 7: Opening pyramidized sources ===");
        BdvHandle bdvPyramidized = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", pyramidizedSources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        BdvHandleHelper.getJFrame(bdvPyramidized).setTitle("BigDataViewer-Pyramidized");
        BdvHandleHelper.getJFrame(bdvPyramidized).setLocation(50, 50);
        new ViewerTransformAdjuster(bdvPyramidized, pyramidizedSources[0]).run();

        DemoHelper.waitFor(1000);

        // -------------------------------------------------------------------------
        // Pause + capture: pyramidized view at overview zoom
        // -------------------------------------------------------------------------
        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("fuse_resample")
                .waitMs(2000)
                .filter("BigDataViewer-Pyramidized")
                .pause("Pyramidized fused sources are now displayed.\n\n" +
                        "Zoom out to show the full field of view — notice that tiles load\n" +
                        "smoothly at any zoom level thanks to the pyramid levels.\n\n" +
                        "Adjust brightness/contrast as needed, then click Continue to capture.")
                .capture();

        SwingUtilities.invokeAndWait(() -> BdvHandleHelper.closeWindow(bdvPyramidized));
        DemoHelper.waitFor(500);

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }

    /**
     * Selects every {@code numChannels}-th source starting at {@code channelIdx}.
     *
     * For a dataset loaded as [series0_ch0, series0_ch1, series1_ch0, series1_ch1, ...]:
     *   channel 0 → channelIdx=0, numChannels=2 → indices 0, 2, 4, …
     *   channel 1 → channelIdx=1, numChannels=2 → indices 1, 3, 5, …
     *
     * Print the source names at the top of main() to verify this matches your dataset.
     */
    private static SourceAndConverter<?>[] filterByChannelIndex(
            SourceAndConverter<?>[] all, int channelIdx, int numChannels) {
        return IntStream.range(0, all.length)
                .filter(i -> i % numChannels == channelIdx)
                .mapToObj(i -> all[i])
                .toArray(SourceAndConverter[]::new);
    }
}