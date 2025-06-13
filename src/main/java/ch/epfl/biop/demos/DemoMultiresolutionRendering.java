package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.viewers.ViewerAdapter;
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
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Demo Multiresolution Rendering")
public class DemoMultiresolutionRendering implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Demo Multiresolution Rendering</h1>\n" +
            "    <p>This demo illustrates multiresolution rendering using ImgLib2 and BigDataViewer (BDV). It sets up multiple viewers to display the same dataset at different resolution scales, demonstrating the trade-offs between rendering speed and detail.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>The demo begins by loading a dataset specified by the user. This dataset is used across multiple viewers to demonstrate multiresolution rendering.</p>\n" +
            "\n" +
            "    <h3>2. Setting Up Multiple Viewers</h3>\n" +
            "    <p>Three BDV viewers are created with different resolution scales:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>High Resolution (1x):</strong> Displays the dataset at its highest resolution. This provides the most detail but can be slower to render.</li>\n" +
            "        <li><strong>Medium Resolution (1/4x):</strong> Displays the dataset at a quarter of the highest resolution. This is faster to render but lacks finer details.</li>\n" +
            "        <li><strong>Low Resolution (1/16x):</strong> Displays the dataset at a sixteenth of the highest resolution. This is super fast to render and lacks details.</li>\n" +
            "        <li><strong>Multi-resolution (1x, 1/4x, 1/16x):</strong> The default BDV behavior that progressively renders the dataset at multiple resolutions. Low resolutions are rendered quickly for immediate feedback, while higher resolutions are rendered as they become available, providing both responsiveness and detail.</li>\n" +
            "    </ul>\n" +
            "\n" +
            "    <h3>3. Swing GUI Setup</h3>\n" +
            "    <p>A Swing-based GUI is created to display the different viewers side by side. This layout allows for a direct comparison of the rendering strategies.</p>\n" +
            "\n" +
            "    <h3>4. Synchronization of Viewers</h3>\n" +
            "    <p>The views and states of the different BDV viewers are synchronized. This ensures that navigation and adjustments in one viewer are reflected in the others, providing a cohesive experience.</p>\n" +
            "\n" +
            "    <h3>5. Displaying the Data</h3>\n" +
            "    <p>The loaded dataset is displayed in each viewer. Brightness adjustments are made to ensure optimal visualization across the different resolution scales.</p>\n" +
            "\n" +
            "    <h3>6. Error Handling</h3>\n" +
            "    <p>The demo includes error handling to manage potential issues during dataset loading, viewer setup, and display.</p>\n" +
            "\n" +
            "    <p>This demo showcases the capabilities of BDV and ImgLib2 in visualizing datasets at multiple resolutions, offering insights into how different resolutions can enhance data visualization and analysis by balancing rendering speed and detail.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

    @Parameter(persist = false)
    DemoDatasetHelper.DemoDataset dataset_name;

    @Parameter
    Context ctx;

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    LogService log;

    BdvHandle bdv16x, bdv4x, bdv1x, normalBdv;

    @Override
    public void run() {
        try {
            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            JFrame frame = new JFrame("Demo Multiresolution Rendering");

            BiopBdvSupplier supplier = new BiopBdvSupplier();
            BiopSerializableBdvOptions opts = new BiopSerializableBdvOptions();

            opts.screenScales = new double[]{1/16.0};
            bdv16x = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(bdv16x);
            BdvHandleHelper.getJFrame(bdv16x).setVisible(false);

            opts.screenScales = new double[]{1/4.0};
            bdv4x = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(bdv4x);
            BdvHandleHelper.getJFrame(bdv4x).setVisible(false);

            opts.screenScales = new double[]{1.0};
            bdv1x = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(bdv1x);
            BdvHandleHelper.getJFrame(bdv1x).setVisible(false);

            opts.screenScales = new double[]{1.0, 1/4.0, 1/16.0};
            normalBdv = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(normalBdv);
            BdvHandleHelper.getJFrame(normalBdv).setVisible(false);

            SwingUtilities.invokeAndWait(() -> {
                // Create the main frame

                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(800, 600);
                frame.setLayout(new BorderLayout());

                // Create the right panel with a JLabel
                JLabel rightLabel = new JLabel("Right Side");
                rightLabel.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel rightPanel = new JPanel(new BorderLayout());
                rightPanel.add(bdv16x.getCardPanel().getComponent(), BorderLayout.CENTER);

                // Create the left panel, which will be divided into 4 parts using JSplitPane
                JPanel leftPanel = createQuadrant(bdv16x.getViewerPanel(),
                        bdv4x.getViewerPanel(), bdv1x.getViewerPanel(), normalBdv.getViewerPanel());

                // Create a JSplitPane to split left and right sides
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
                splitPane.setDividerLocation(400); // Set initial divider location

                // Add the split pane to the frame
                frame.add(splitPane, BorderLayout.CENTER);

                // Make the frame visible
                frame.setVisible(true);
            });

            ViewerTransformSyncStarter starter = new ViewerTransformSyncStarter(
                    new ViewerAdapter[]{new ViewerAdapter(bdv16x), new ViewerAdapter(bdv1x), new ViewerAdapter(
                    bdv4x), new ViewerAdapter(normalBdv)}, true);
            starter.run();

            new ViewerStateSyncStarter(new ViewerAdapter(bdv16x), new ViewerAdapter(
                    bdv1x), new ViewerAdapter(bdv4x), new ViewerAdapter(normalBdv)).run();

            // We apparently need to wait one round of UI refresh to get the view right
            SwingUtilities.invokeLater(() -> {
                new ViewerTransformAdjuster(normalBdv, sources).run();
            });

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdv16x, sources);


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
