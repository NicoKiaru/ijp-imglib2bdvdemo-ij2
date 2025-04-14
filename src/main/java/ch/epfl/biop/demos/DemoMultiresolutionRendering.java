package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DatasetHelper;
import org.scijava.Context;
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

    @Parameter(persist = false)
    DatasetHelper.DemoDataset dataset_name;

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
            SourceAndConverter<?>[] sources = DatasetHelper.getData(dataset_name, ctx);

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
