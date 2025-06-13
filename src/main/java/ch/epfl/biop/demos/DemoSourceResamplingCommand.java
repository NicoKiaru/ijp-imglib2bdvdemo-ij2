package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import sc.fiji.bdvpg.viewers.ViewerAdapter;
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

import static ch.epfl.biop.demos.utils.BdvHelper.createTri;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Resample a source according to another one")
public class DemoSourceResamplingCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Resampling a Source According to Another</h1>\n" +
            "    <p>This demo illustrates how to resample one image source to match the geometry of another image source using ImgLib2 and BigDataViewer (BDV). The process involves loading data, selecting sources for resampling, and displaying the results in a synchronized viewer setup.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading and Selecting Data</h3>\n" +
            "    <p>The demo loads a dataset of brain slices. It then selects specific sources from this dataset: one to be resampled and another to act as a model for the resampling process.</p>\n" +
            "\n" +
            "    <h3>2. Resampling Process</h3>\n" +
            "    <p>The demo uses the <code>SourceResampler</code> class to adjust the first source's geometry to match that of the model source. This process ensures that the transformed source aligns with the target model both spatially and in terms of scale.</p>\n" +
            "\n" +
            "    <h3>3. Setting Up Viewers</h3>\n" +
            "    <p>Three BDV viewers are set up to visualize:</p>\n" +
            "    <ul>\n" +
            "        <li><strong>Original Source</strong>: The source image before resampling.</li>\n" +
            "        <li><strong>Model Source</strong>: The source image used as a model for resampling.</li>\n" +
            "        <li><strong>Resampled Source</strong>: The result of the resampling process, adjusted to the model source's geometry.</li>\n" +
            "    </ul>\n" +
            "\n" +
            "    <h3>4. Swing GUI Setup</h3>\n" +
            "    <p>A Swing-based GUI is created to display the three viewers in a divided layout. This allows users to visually compare the original, model, and resampled image sources side by side.</p>\n" +
            "\n" +
            "    <h3>5. Synchronization and Display</h3>\n" +
            "    <p>The viewers are synchronized so that interactions with one viewer (such as zooming or panning) are reflected in the others. This synchronization helps maintain a consistent view across panels, facilitating direct comparison between the original, model, and resampled sources.</p>\n" +
            "\n" +
            "    <p>This demo showcases the flexibility of BDV and ImgLib2 in handling and visualizing multi-resolution image data, as well as their capabilities in image transformation and alignment tasks.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;


    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterService source_service;


    @Parameter
    SourceAndConverterBdvDisplayService display_service;

    @Parameter
    boolean swap_sources;


    BdvHandle bdvTopLevel, bdvBottom, normalBdv;


    @Override
    public void run() {
        // BRAIN_SLICES
        // Let's resample the BF overview image similarly as one

        try {
            //SourceAndConverter<?>[] sources =
                    DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.BRAIN_SLICES, ctx);

            SourceAndConverterServiceUI.Node datasetNode = source_service.getUI().getRoot().child("Slide_03");

            // Let's get the overview

            SourceAndConverter bfToResample = datasetNode.child("ImageName").child("overview").sources()[0];
            SourceAndConverter<?> model = datasetNode.child("ImageName").child("10x_01").sources()[0];

            if (swap_sources) {
                SourceAndConverter temp = model;
                model = bfToResample;
                bfToResample = temp;
            }

            SourceAndConverter resampled = new SourceResampler<>(bfToResample,
                model, "Overview_Resampled_Like_Fluo", true,
                true, true, 0).get();

            BiopSerializableBdvOptions opts = new BiopSerializableBdvOptions();
            opts.is2D = true;

            bdvTopLevel = new BiopBdvSupplier(opts).get();
            display_service.registerBdvHandle(bdvTopLevel);
            BdvHandleHelper.getJFrame(bdvTopLevel).setVisible(false);

            bdvBottom = new BiopBdvSupplier(opts).get();
            display_service.registerBdvHandle(bdvBottom);
            BdvHandleHelper.getJFrame(bdvBottom).setVisible(false);

            normalBdv = new BiopBdvSupplier(opts).get();
            display_service.registerBdvHandle(normalBdv);
            BdvHandleHelper.getJFrame(normalBdv).setVisible(false);

            JFrame frame = new JFrame("Demo Pyramidal Image");

            SwingUtilities.invokeAndWait(() -> {
                // Create the main frame

                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(800, 600);
                frame.setLayout(new BorderLayout());

                // Create the right panel with a JLabel
                JLabel rightLabel = new JLabel("Right Side");
                rightLabel.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel rightPanel = new JPanel(new BorderLayout());
                rightPanel.add(bdvTopLevel.getCardPanel().getComponent(), BorderLayout.CENTER);

                // Create the left panel, which will be divided into 4 parts using JSplitPane
                JPanel leftPanel = createTri(bdvTopLevel.getViewerPanel(),
                        bdvBottom.getViewerPanel(), normalBdv.getViewerPanel());

                // Create a JSplitPane to split left and right sides
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
                splitPane.setDividerLocation(400); // Set initial divider location

                // Add the split pane to the frame
                frame.add(splitPane, BorderLayout.CENTER);

                // Make the frame visible
                frame.setVisible(true);
            });


            ViewerTransformSyncStarter starter = new ViewerTransformSyncStarter(
                    new ViewerAdapter[]{new ViewerAdapter(bdvTopLevel), new ViewerAdapter(bdvBottom), new ViewerAdapter(normalBdv)}, true);
            starter.run();

            display_service.show(normalBdv, resampled);
            display_service.show(bdvTopLevel, bfToResample);
            display_service.show(bdvBottom, model);

            new ViewerTransformAdjuster(bdvTopLevel, bfToResample).run();

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }



}
