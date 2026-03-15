package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import ch.epfl.biop.command.display.bdv.SourcesOverviewCommand;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.module.Module;
import sc.fiji.bdvpg.command.display.bdv.BdvOrthoCreateCommand;
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
import sc.fiji.bdvpg.command.display.bvv.BvvOrthoCreateCommand;

import javax.swing.SwingUtilities;
import java.io.File;

/**
 * Generates screenshots for the visualizing_images.md documentation page.
 *
 * The LLS7 HeLa dataset (Zenodo 14203207) is downloaded on first run and cached locally.
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/visualizing_images/images
 *
 * Run from IDE: right-click → Run 'GenerateVisualizingImagesScreenshots.main()'
 * Run with custom output dir: set VM option -Ddoc.output.dir=/absolute/path/to/images
 *
 * Expected output files (prefix_WindowTitle.png):
 *   bdv_show_sources_BigDataViewer.png
 *   bdv_orthogonal_views_BigDataViewer-XY.png
 *   bdv_orthogonal_views_BigDataViewer-ZY.png
 *   bdv_orthogonal_views_BigDataViewer-XZ.png
 *   bdv_grid_overview_BigDataViewer.png
 *   source_set_color_BigDataViewer.png
 *   bvv_show_sources_BigVolumeViewer.png
 *   bvv_orthogonal_views_BigVolumeViewer-XY.png
 *   bvv_orthogonal_views_BigVolumeViewer-ZY.png
 *   bvv_orthogonal_views_BigVolumeViewer-XZ.png
 */
public class GenerateVisualizingImagesScreenshots {

    static {
        LegacyInjector.preinit();
    }

    static final File OUTPUT_DIR = new File(
            System.getProperty("doc.output.dir",
                    "../bigdataviewer-playground-documentation/docs/source/visualizing_images/images")
    );

    public static void main(String[] args) throws Exception {
        System.out.println("Output directory: " + OUTPUT_DIR.getAbsolutePath());

        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);
        DemoHelper.expandTreeView(ij);

        // Load the LLS7 HeLa dataset. Downloads on first run (~500 MB), cached locally after.
        System.out.println("Loading LLS7 HeLa dataset...");
        SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
                DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ij.context());

        // Auto-adjust brightness once; settings persist across all scenarios below.
        for (SourceAndConverter<?> source : sources) {
            new BrightnessAutoAdjuster<>(source, 0).run();
        }

        ij.get(SourceBdvDisplayService.class).setDefaultBdvSupplier(new DefaultBdvSupplier(new SerializableBdvOptions()));
        // -------------------------------------------------------------------------
        // Scenario 1: BDV - Show Sources
        // Illustrates the standard way to open a BDV window with sources.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 1: BDV - Show Sources ===");
        BdvHandle bdv1 = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", sources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdv1).setTitle("BigDataViewer");
            new ViewerTransformAdjuster(bdv1, sources[0]).run();
        });
        DemoHelper.pause("Scenario 1 – BDV Show Sources\nAdjust the view if needed, then click Continue to capture.");
        DemoHelper.shot(OUTPUT_DIR, "bdv_show_sources", 4000, "BigDataViewer");
        SwingUtilities.invokeAndWait(() -> BdvHandleHelper.closeWindow(bdv1));
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Scenario 2: BDV - Orthogonal Views
        // Creates three synchronized BDV windows (XY, ZY, XZ).
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 2: BDV - Orthogonal Views ===");
        Module orthoModule = ij.command().run(BdvOrthoCreateCommand.class, true,
                "sizex", 600,
                "sizey", 500,
                "ntimepoints", 1,
                "interpolate", true,
                "drawcrosses", true,
                "synchronize_sources", true,
                "screen", 0,
                "locationx", 50,
                "locationy", 50
        ).get();

        BdvHandle bdvFront  = (BdvHandle) orthoModule.getOutput("bdvhx");
        BdvHandle bdvRight  = (BdvHandle) orthoModule.getOutput("bdvhy");
        BdvHandle bdvBottom = (BdvHandle) orthoModule.getOutput("bdvhz");

        ij.get(SourceBdvDisplayService.class).show(bdvFront, sources);

        // Rename windows with a shared prefix so all three are captured in one shot()
        // and each gets a unique filename (XY, ZY, XZ).
        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdvFront) .setTitle("BigDataViewer-XY");
            BdvHandleHelper.getJFrame(bdvRight) .setTitle("BigDataViewer-ZY");
            BdvHandleHelper.getJFrame(bdvBottom).setTitle("BigDataViewer-XZ");
            new ViewerTransformAdjuster(bdvFront, sources[0]).run();
        });
        DemoHelper.pause("Scenario 2 – BDV Orthogonal Views\nAll three windows (XY, ZY, XZ) will be captured.\nAdjust the view in any window if needed, then click Continue.");
        DemoHelper.shot(OUTPUT_DIR, "bdv_orthogonal_views", 5000, "BigDataViewer-");
        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.closeWindow(bdvFront);
            BdvHandleHelper.closeWindow(bdvRight);
            BdvHandleHelper.closeWindow(bdvBottom);
        });
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Scenario 3: BDV - Show Sources On Grid
        // Arranges all channels side by side in a single BDV window.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 3: Grid Overview ===");
        ij.command().run(SourcesOverviewCommand.class, true,
                "sources", sources,
                "n_columns", 2,
                "entities_split", "channel",
                "timepoint_begin", 0
        );//.get();

        Thread.sleep(3000);

        DemoHelper.pause("Scenario 3 – BDV Grid Overview\nAdjust the view if needed, then click Continue to capture.");
        DemoHelper.shot(OUTPUT_DIR, "bdv_grid_overview", 5000, "BigDataViewer");
        SwingUtilities.invokeAndWait(() ->
                DemoHelper.getFilteredVisibleFrames("BigDataViewer").forEach(f -> f.dispose())
        );

        // -------------------------------------------------------------------------
        // Scenario 4: Source - Set Color
        // Demonstrates changing the display color of individual sources.
        // Channel 0 → cyan, channel 1 → magenta.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 4: Source - Set Color ===");
        SourceService ss = ij.get(SourceService.class);
        ss.getConverterSetup(sources[0]).setColor(new ARGBType(ARGBType.rgba(0,   255, 255, 0)));
        ss.getConverterSetup(sources[1]).setColor(new ARGBType(ARGBType.rgba(255, 0,   255, 0)));

        BdvHandle bdv4 = (BdvHandle) ij.command().run(SingleBdvSourcesShowCommand.class, true,
                "sources", sources,
                "adjust_view", true,
                "auto_contrast", false,
                "interpolate", true,
                "make_new_window", true
        ).get().getOutput("bdvh");

        SwingUtilities.invokeAndWait(() -> {
            BdvHandleHelper.getJFrame(bdv4).setTitle("BigDataViewer");
            new ViewerTransformAdjuster(bdv4, sources[0]).run();
        });
        DemoHelper.pause("Scenario 4 – Source Set Color\nChannel 0 = cyan, channel 1 = magenta.\nAdjust if needed, then click Continue to capture.");
        DemoHelper.shot(OUTPUT_DIR, "source_set_color", 4000, "BigDataViewer");
        SwingUtilities.invokeAndWait(() -> BdvHandleHelper.closeWindow(bdv4));
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Scenario 5: BVV - Show Sources (Volume Rendering)
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 5: BVV Volume Rendering ===");
        BvvHandle bvv = new BvvCreator(BvvOptions.options()).get();
        ij.command().run(BvvSourcesShowCommand.class, true,
                "bvvh", bvv,
                "sources", sources,
                "adjust_view", true
        ).get();

        new ViewerTransformAdjuster(new ViewerAdapter(bvv), new SourceAndConverter[]{sources[0]}).run();

        DemoHelper.pause("Scenario 5 – BVV Show Sources\nRotate or zoom the volume if needed, then click Continue to capture.");
        DemoHelper.shot(OUTPUT_DIR, "bvv_show_sources", 6000, "BigVolumeViewer");
        SwingUtilities.invokeAndWait(() -> BvvHandleHelper.closeWindow(bvv));
        DemoHelper.waitFor(500);

        // -------------------------------------------------------------------------
        // Scenario 6: BVV - Create Orthogonal Views
        // Creates three synchronized BVV windows (XY, ZY, XZ).
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 6: BVV - Orthogonal Views ===");
        Module bvvOrthoModule = ij.command().run(BvvOrthoCreateCommand.class, true,
                "sizex", 500,
                "sizey", 400,
                "ntimepoints", 1,
                "interpolate", true,
                "synchronize_sources", true,
                "screen", 0,
                "locationx", 50,
                "locationy", 50
        ).get();

        BvvHandle bvvFront  = (BvvHandle) bvvOrthoModule.getOutput("bvvhx");
        BvvHandle bvvRight  = (BvvHandle) bvvOrthoModule.getOutput("bvvhy");
        BvvHandle bvvBottom = (BvvHandle) bvvOrthoModule.getOutput("bvvhz");

        Thread.sleep(2500);
        ij.command().run(BvvSourcesShowCommand.class, true,
                "bvvh", bvvFront,
                "sources", sources,
                "adjust_view", false
        );//.get();

        Thread.sleep(2000);

        new ViewerTransformAdjuster(new ViewerAdapter(bvvFront), new SourceAndConverter[]{sources[0]}).run();


        SwingUtilities.invokeAndWait(() -> {
            BvvHandleHelper.getJFrame(bvvFront) .setTitle("BigVolumeViewer-XY");
            BvvHandleHelper.getJFrame(bvvRight) .setTitle("BigVolumeViewer-ZY");
            BvvHandleHelper.getJFrame(bvvBottom).setTitle("BigVolumeViewer-XZ");
        });
        DemoHelper.pause("Scenario 6 – BVV Orthogonal Views\nAll three windows (XY, ZY, XZ) will be captured.\nAdjust the view if needed, then click Continue.");
        DemoHelper.shot(OUTPUT_DIR, "bvv_orthogonal_views", 6000, "BigVolumeViewer-");
        SwingUtilities.invokeAndWait(() -> {
            BvvHandleHelper.closeWindow(bvvFront);
            BvvHandleHelper.closeWindow(bvvRight);
            BvvHandleHelper.closeWindow(bvvBottom);
        });

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }
}
