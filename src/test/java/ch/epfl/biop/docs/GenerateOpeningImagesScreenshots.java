package ch.epfl.biop.docs;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.DemoHelper;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

import java.io.File;

/**
 * Generates screenshots for the opening_images.md documentation page.
 *
 * Scenarios:
 *   1. Import submenu (clipboard — snip Plugins > BigDataViewer-Playground > Import)
 *   2. BDV Playground window — tree view after loading LLS7 dataset (auto-shot)
 *   3. Right-click context menu on a tree node (clipboard)
 *
 * Output directory is controlled by the system property {@code doc.output.dir}.
 * Default: ../bigdataviewer-playground-documentation/docs/source/opening_images/images
 *
 * Run from IDE: right-click → Run 'GenerateOpeningImagesScreenshots.main()'
 */
public class GenerateOpeningImagesScreenshots {

    static {
        LegacyInjector.preinit();
    }

    static final File OUTPUT_DIR = new File(
            System.getProperty("doc.output.dir",
                    "../bigdataviewer-playground-documentation/docs/source/opening_images/images")
    );

    public static void main(String[] args) throws Exception {
        System.out.println("Output directory: " + OUTPUT_DIR.getAbsolutePath());

        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);

        // -------------------------------------------------------------------------
        // Scenario 1: Import submenu (clipboard screenshot)
        // Open the menu and snip it before clicking OK.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 1: Import submenu (clipboard) ===");
        DemoHelper.clipboardShot()
                .to(OUTPUT_DIR)
                .filename("import_menu")
                .withMessage("Open the menu:\n" +
                        "  Plugins > BigDataViewer-Playground > Import\n\n" +
                        "Hover over 'Import' so the submenu is fully visible.\n" +
                        "Snip it with Win+Shift+S, then click OK.")
                .capture();

        // -------------------------------------------------------------------------
        // Scenario 2: BDV Playground tree view (auto-shot)
        // Load the LLS7 dataset so the tree has multiple channels to show.
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 2: Loading LLS7 dataset for tree view ===");
        System.out.println("(Downloads ~500 MB on first run, cached locally after.)");
        DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ij.context());

        DemoHelper.expandTreeView(ij, 3);
        DemoHelper.waitFor(1000);

        // The window is titled "BDV Sources" in version 0.20.4.
        DemoHelper.shot()
                .to(OUTPUT_DIR)
                .prefix("sources_tree")
                .waitMs(2000)
                .filter("BDV Sources")
                .pause("Scenario 2 – Sources Tree View\n\n" +
                        "The BDV Playground window should show the source tree expanded\n" +
                        "with the LLS7 dataset and its channels as child nodes.\n\n" +
                        "Resize or rearrange the window so the tree is clearly visible.\n" +
                        "Click Continue to capture.")
                .capture();

        // -------------------------------------------------------------------------
        // Scenario 3: Right-click context menu on a tree node (clipboard)
        // -------------------------------------------------------------------------
        System.out.println("\n=== Scenario 3: Tree context menu (clipboard) ===");
        DemoHelper.clipboardShot()
                .to(OUTPUT_DIR)
                .filename("tree_context_menu")
                .withMessage("In the BDV Playground tree, right-click on any source node\n" +
                        "(e.g. one of the LLS7 channels) to open the context menu.\n\n" +
                        "Keep the menu open, snip it with Win+Shift+S, then click OK.")
                .capture();

        System.out.println("\n=== Done. Screenshots saved to: " + OUTPUT_DIR.getAbsolutePath() + " ===");
        // Fiji stays open so you can inspect the results. Close it manually when done.
    }
}