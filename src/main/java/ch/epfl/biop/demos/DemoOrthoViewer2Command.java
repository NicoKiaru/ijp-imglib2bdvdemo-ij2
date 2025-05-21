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

import static ch.epfl.biop.demos.utils.BdvHelper.createQuadrant;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - OrthoViewer (v2)")
public class DemoOrthoViewer2Command implements Command {

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