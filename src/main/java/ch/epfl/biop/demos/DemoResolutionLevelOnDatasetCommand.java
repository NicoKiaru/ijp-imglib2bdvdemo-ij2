package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.demos.utils.ReIndexedPyramidSource;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static ch.epfl.biop.demos.utils.BdvHelper.createTri;

@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Pyramidal Image Loading")
public class DemoResolutionLevelOnDatasetCommand implements Command {

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

    BdvHandle bdvTopLevel, bdvBottom, normalBdv;

    @Override
    public void run() {
        try {
            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            if (sources[0].getSpimSource().getNumMipmapLevels() == 1) {
                System.out.println("The dataset is not multiresolution.");
                return;
            }

            JFrame frame = new JFrame("Demo Pyramidal Image");

            BiopBdvSupplier supplier = new BiopBdvSupplier();
            BiopSerializableBdvOptions opts = new BiopSerializableBdvOptions();

            bdvTopLevel = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(bdvTopLevel);
            BdvHandleHelper.getJFrame(bdvTopLevel).setVisible(false);

            bdvBottom = new BiopBdvSupplier(opts).get();
            ds.registerBdvHandle(bdvBottom);
            BdvHandleHelper.getJFrame(bdvBottom).setVisible(false);

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

            List<Integer> top = new ArrayList<>();
            top.add(0);

            SourceAndConverter<?>[] topSources = new SourceAndConverter[sources.length];
            SourceAndConverter<?>[] bottomSources = new SourceAndConverter[sources.length];

            for (int i = 0; i < sources.length; i++) {

                SourceAndConverter<?> source  = sources[i];

                ss.getConverterSetup(source).setDisplayRange(0,2500);

                new BrightnessAutoAdjuster<>( source, 0 ).run();

                List<Integer> bottom = new ArrayList<>();
                bottom.add(source.getSpimSource().getNumMipmapLevels()-1);

                Source<?> topSource = new ReIndexedPyramidSource(source.getSpimSource(), top);
                Source<?> vTopSource = new ReIndexedPyramidSource(source.asVolatile().getSpimSource(), top);

                SourceAndConverter<?> topSac = new SourceAndConverter(topSource, source.getConverter(),
                        new SourceAndConverter(vTopSource, source.asVolatile().getConverter()));

                Source<?> bottomSource = new ReIndexedPyramidSource(source.getSpimSource(), bottom);
                Source<?> vBottomSource = new ReIndexedPyramidSource(source.asVolatile().getSpimSource(), bottom);

                SourceAndConverter<?> bottomSac = new SourceAndConverter(bottomSource, source.getConverter(),
                        new SourceAndConverter(vBottomSource, source.asVolatile().getConverter()));

                topSources[i] = topSac;
                bottomSources[i] = bottomSac;
                ss.register(topSac);
                ss.register(bottomSac);
            }

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(normalBdv, sources);
            ds.show(bdvTopLevel, topSources);
            ds.show(bdvBottom, bottomSources);


        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
