package ch.epfl.biop.demos;

import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
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
import org.scijava.ItemVisibility;

import static ch.epfl.biop.demos.utils.BdvHelper.createQuadrant;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - OrthoViewer (v2)")
public class DemoOrthoViewer2Command implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Demo Ortho Viewer (v2)</h1>\n" +
            "    <p>This demo illustrates an alternative setup of an orthoviewer using ImgLib2 and BigDataViewer (BDV). It creates multiple viewers to display different orthogonal views (front, right, bottom) of a dataset, enhancing visualization with multiple perspectives.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>The demo begins by loading a dataset specified by the user, which is then displayed in multiple viewers providing various perspectives.</p>\n" +
            "\n" +
            "    <h3>2. Setting Up Multiple Viewers</h3>\n" +
            "    <p>Three BDV viewers are created to display orthogonal views of the dataset:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Front View</strong>: Shows the front perspective of the dataset.</li>\n" +
            "        <li><strong>Right View</strong>: Displays the right-side perspective.</li>\n" +
            "        <li><strong>Bottom View</strong>: Offers a bottom view of the dataset.</li>\n" +
            "    </ul>\n" +
            "    <p>Additionally, a 3D viewer (Bvv) is set up if the dataset is compatible, enhancing interactive data exploration.</p>\n" +
            "\n" +
            "    <h3>3. Swing GUI Setup</h3>\n" +
            "    <p>A Swing-based GUI is created, arranging the viewers in a divided layout that facilitates simultaneous viewing of orthogonal perspectives and the 3D view (if available).</p>\n" +
            "\n" +
            "    <h3>4. Synchronization of Viewers</h3>\n" +
            "    <p>The views and states of the different BDV viewers are synchronized. This ensures that navigation and adjustments in one viewer are reflected in the others, allowing for a cohesive experience across all perspectives.</p>\n" +
            "\n" +
            "    <h3>5. Displaying the Data</h3>\n" +
            "    <p>The dataset is displayed in each viewer, and brightness adjustments are made to ensure clear visualization across all viewers.</p>\n" +
            "\n" +
            "    <h3>6. Error Handling</h3>\n" +
            "    <p>The demo includes error handling to manage potential issues during dataset loading, viewer setup, and display.</p>\n" +
            "\n" +
            "    <p>This variation of the demo showcases the flexibility of BDV and ImgLib2 in providing multi-perspective visualization of datasets, enhancing spatial understanding and analysis.</p>\n" +
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

    BdvHandlePanel bdvFront, bdvRight, bdvBottom;

    BvvHandle bvv;

    @Override
    public void run() {
        try {
            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            boolean showBvv = DemoDatasetHelper.isBvvCompatible(sources);

            SwingUtilities.invokeAndWait(() -> {
                // Create the main frame

                JFrame frame = new JFrame("Demo Ortho Viewer");

                bdvFront = new BdvHandlePanel(frame, BdvOptions.options());
                bdvRight = new BdvHandlePanel(frame, BdvOptions.options());
                bdvBottom = new BdvHandlePanel(frame, BdvOptions.options());

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
                        bdvRight.getViewerPanel(), bdvBottom.getViewerPanel(),
                        showBvv ?  bvv.getViewerPanel(): new JLabel("Source type incompatible with BVV"));

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