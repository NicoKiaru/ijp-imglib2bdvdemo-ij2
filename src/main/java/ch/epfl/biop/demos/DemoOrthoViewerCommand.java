package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bvv.BvvCreator;
import sc.fiji.bdvpg.bvv.BvvHandleHelper;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.viewers.ViewerAdapter;
import sc.fiji.bdvpg.viewers.ViewerOrthoSyncStarter;
import sc.fiji.bdvpg.viewers.ViewerStateSyncStarter;
import sc.fiji.bdvpg.viewers.ViewerTransformSyncStarter;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import static ch.epfl.biop.demos.utils.BdvHelper.createQuadrant;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - OrthoViewer")
public class DemoOrthoViewerCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Demo Ortho Viewer</h1>\n" +
            "    <p>This demo illustrates the use of an orthoviewer setup using ImgLib2 and BigDataViewer (BDV). It creates multiple viewers to display different orthogonal views (front, right, bottom) of a dataset, offering comprehensive multi-angle visualization.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>The demo begins by loading a dataset specified by the user. This dataset is displayed in multiple viewers from different perspectives.</p>\n" +
            "\n" +
            "    <h3>2. Setting Up Multiple Viewers</h3>\n" +
            "    <p>Three BDV viewers are created to show orthogonal views of the dataset:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Front View</strong>: One viewer is set to display the front view of the dataset.</li>\n" +
            "        <li><strong>Right View</strong>: Another viewer shows the right-side view.</li>\n" +
            "        <li><strong>Bottom View</strong>: The third viewer provides the bottom view.</li>\n" +
            "    </ul>\n" +
            "    <p>Additionally, a 3D viewer (Bvv) is also set up if the dataset is compatible with it, allowing for a more interactive exploration.</p>\n" +
            "\n" +
            "    <h3>3. Swing GUI Setup</h3>\n" +
            "    <p>A Swing-based GUI is created to display the different viewers in a divided layout. This setup allows for simultaneous viewing of the orthogonal perspectives and the 3D view (if available).</p>\n" +
            "\n" +
            "    <h3>4. Synchronization of Viewers</h3>\n" +
            "    <p>The views and states of the different BDV viewers are synchronized. This ensures that navigation and adjustments in one viewer are reflected in the others, providing a cohesive and synchronized viewing experience across all perspectives.</p>\n" +
            "\n" +
            "    <h3>5. Displaying the Data</h3>\n" +
            "    <p>The loaded dataset is displayed in each viewer. Brightness adjustments are made to ensure optimal visualization across the different viewers.</p>\n" +
            "\n" +
            "    <h3>6. Error Handling</h3>\n" +
            "    <p>The demo includes error handling to manage potential issues during dataset loading, viewer setup, and display.</p>\n" +
            "\n" +
            "    <p>This demo showcases the capabilities of BDV and ImgLib2 in visualizing datasets from multiple orthogonal perspectives and a 3D view, providing comprehensive insights into the spatial structure of the dataset.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;


    @Parameter(persist = false)
    DemoDatasetHelper.DemoDataset dataset_name;

    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    LogService log;

    BdvHandle bdvFront, bdvRight, bdvBottom;

    BvvHandle bvv;

    @Override
    public void run() {
        try {
            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            boolean showBvv = DemoDatasetHelper.isBvvCompatible(sources);

            SwingUtilities.invokeAndWait(() -> {
                // Create the main frame

                JFrame frame = new JFrame("Ortho Viewer Demo");

                bdvFront = ds.getNewBdv();
                BdvHandleHelper.getJFrame(bdvFront).setVisible(false);

                bdvRight = ds.getNewBdv();
                BdvHandleHelper.getJFrame(bdvRight).setVisible(false);

                bdvBottom = ds.getNewBdv();
                BdvHandleHelper.getJFrame(bdvBottom).setVisible(false);

                if (showBvv) {
                    bvv = new BvvCreator(BvvOptions.options()).get();
                    BvvHandleHelper.getJFrame(bvv).setVisible(false);
                }

                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(800, 600);
                frame.setLayout(new BorderLayout());

                // Create the right panel with a JLabel
                JLabel rightLabel = new JLabel("Right Side");
                rightLabel.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel rightPanel = new JPanel(new BorderLayout());
                rightPanel.add(bdvFront.getCardPanel().getComponent(), BorderLayout.CENTER);

                // Create the left panel, which will be divided into 4 parts using JSplitPane
                JPanel leftPanel = createQuadrant(bdvFront.getViewerPanel(),
                        bdvRight.getViewerPanel(), bdvBottom.getViewerPanel(), showBvv?bvv.getViewerPanel(): new JLabel("Source type incompatible with BVV"));

                // Create a JSplitPane to split left and right sides
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
                splitPane.setDividerLocation(400); // Set initial divider location

                // Add the split pane to the frame
                frame.add(splitPane, BorderLayout.CENTER);

                // Make the frame visible
                frame.setVisible(true);

                ViewerOrthoSyncStarter starter = new ViewerOrthoSyncStarter(
                        new ViewerAdapter(bdvFront), new ViewerAdapter(bdvBottom), new ViewerAdapter(
                        bdvRight), true);
                starter.run();

                if (showBvv) {
                    new ViewerTransformSyncStarter(
                            new ViewerAdapter[]{new ViewerAdapter(bdvFront), new ViewerAdapter(bvv)}, true
                    ).run();
                }

                if (showBvv) {
                    new ViewerStateSyncStarter(new ViewerAdapter(bdvFront), new ViewerAdapter(
                            bdvBottom), new ViewerAdapter(bdvRight), new ViewerAdapter(bvv)).run();
                } else {
                    new ViewerStateSyncStarter(new ViewerAdapter(bdvFront), new ViewerAdapter(
                            bdvBottom), new ViewerAdapter(bdvRight)).run();
                }

                // I don't use BdvFunctions in order to keep the correct colors
                ds.show(bdvFront, sources);

            });

            // We apparently need to wait one round of UI refresh to get the view right
            SwingUtilities.invokeLater(() -> {
                new ViewerTransformAdjuster(bdvRight, sources).run();
            });

            // Async at the end -> it can require a lot of computation for the lazy computed game of life for instance
            for (SourceAndConverter<?> source : sources) {
                new BrightnessAutoAdjuster<>( source, 0 ).run();
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        } catch (IOException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}