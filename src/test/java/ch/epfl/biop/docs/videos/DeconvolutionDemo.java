package ch.epfl.biop.docs.videos;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.scijava.widget.SwingSourceSortedListWidget;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Generates a demo for deconvolution. Should be used for video documentation.
 *
 * Workflow:
 *   1. Open raw LLS7 HeLa image (both channels) in BDV.
 *   2. Run lazy Richardson-Lucy deconvolution (both channels, 200 nm PSF).
 *   3. Open deconvolved sources in a second BDV window, placed beside the raw window.
 *   4. Run ViewSynchronizeCommand — both windows now track each other live.
 *   5. Pause — navigate to a good region, wait for GPU tiles, then Continue to capture both.
 */
public class DeconvolutionDemo {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();
        Robot.openBDVPlaygroundTree();

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi");
        Robot.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", fileDataset.getName(),
                "unit", "MICROMETER",
                "files", new File[]{fileDataset},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);


        File filePSF = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-400nm.tif");
        Robot.runFromBDVPlaygroundTree("Sources>Other Sources",
                DatasetFromBioFormatsCreateCommand.class,
                "datasetname", filePSF.getName(),
                "unit", "MICROMETER",
                "files", new File[]{filePSF},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "disable_memo", false);

        Robot.runFromBDVPlaygroundTree("Sources>"+fileDataset.getName(), SingleBdvSourcesShowCommand.class,
                "adjust_view", true,
                "auto_contrast", true,
                "interpolate", false,
                "make_new_window", true);

        Frame bdvRaw = RobotHelper.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        RobotHelper.resizeFrame(bdvRaw, 800, 300);
        RobotHelper.dragFrame(bdvRaw, RobotHelper.DEFAULT_FRAME_X + 450, RobotHelper.DEFAULT_FRAME_Y + 150);

        Robot.runFromBDVPlaygroundTree("Sources>"+fileDataset.getName(), SourcesDeconvolveCommand.class,
                "psf", "Sources>"+filePSF.getName(),
                "num_iterations", 20,
                "block_size_x", 256,
                "block_size_y", 256,
                "block_size_z", 64,
                "overlap_size", 16,
                "non_circulant", true,
                "regularization_factor", 0.002f,
                "output_pixel_type", "Float",
                "suffix", "_deconvolved",
                "n_threads", 4);



        //Robot.placeLatestBDVWindow()

        //JTree jtree =
        //CommandService cs = FijiCommands.context().getService(CommandService.class);

        //SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
        //        DemoDatasetHelper.DemoDataset.BRAIN_SECTION_3DTILES, FijiCommands.context());

        // Look for the path of the import bio-formats command in bdv sources



        /*String treePath = "Sources>BrainSection-Tiles>Channel>ch_0";
        //System.out.println("[RobotSourceTreeRightClickTest] right-clicking node at: " + treePath);
        RobotTree.rightClickPath(jtree, treePath);

        String treePath = "Sources>BrainSection-Tiles>Channel>ch_0";
        //System.out.println("[RobotSourceTreeRightClickTest] right-clicking node at: " + treePath);
        RobotTree.rightClickPath(jtree, treePath);*/


        /*String menuPath = "Display>BDV>BDV - Show Sources";
        System.out.println("[RobotSourceTreeRightClickTest] navigating popup: " + menuPath);
        RobotPopup.clickPath(menuPath);*/

        // Show BDV Source tree view


/*        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();

        String treePath = "Sources>BrainSection-Tiles>Channel>ch_0";
        System.out.println("[RobotSourceTreeRightClickTest] right-clicking node at: " + treePath);
        RobotTree.rightClickPath(jtree, treePath);


        RobotHelper.placeFrame(bdvSources, 700, 100);
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        // Load the LLS7 HeLa dataset (2 channels).
        // Get the command path
        MenuPath menuPath = cs.getCommand(DatasetFromBioFormatsCreateCommand.class).getMenuPath();
        menuPath.getMenuString();
        RobotPopup.clickPath(menuPath.getMenuString());

        /*SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(
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
        // Fiji stays open so you can inspect the results. Close it manually when done.*/
    }
}