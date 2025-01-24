package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import ch.epfl.biop.demos.utils.DatasetHelper;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
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
import java.util.concurrent.ExecutionException;

import static ch.epfl.biop.demos.utils.BdvHelper.createQuadrant;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - OrthoViewer (v2)")
public class DemoOrthoViewer2Command implements Command {

    @Parameter
    DatasetHelper.DemoDataset dataset_name;

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
            SourceAndConverter<?>[] sources = DatasetHelper.getData(dataset_name, ctx);


            SwingUtilities.invokeLater(() -> {
                // Create the main frame

                JFrame frame = new JFrame("Draggable Quadrants with Panels");

                bdvFront = new BdvHandlePanel(frame, BdvOptions.options());//ds.getNewBdv();
                // Let's center the viewer on the egg chamber
                // new ViewerTransformAdjuster( bdvFront, eggChamberSources[0] ).run();
                // BdvHandleHelper.getJFrame(bdvFront).setVisible(false);

                // bdvRight = ds.getNewBdv();
                bdvRight = new BdvHandlePanel(frame, BdvOptions.options());//ds.getNewBdv();
                //BdvHandleHelper.getJFrame(bdvRight).setVisible(false);


                bdvBottom = new BdvHandlePanel(frame, BdvOptions.options());//ds.getNewBdv();
                //bdvBottom = ds.getNewBdv();
                //BdvHandleHelper.getJFrame(bdvBottom).setVisible(false);

                bvv = new BvvCreator(BvvOptions.options()).get();
                BvvHandleHelper.getJFrame(bvv).setVisible(false);


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
                        bdvRight.getViewerPanel(), bdvBottom.getViewerPanel(), bvv.getViewerPanel());

                // Create a JSplitPane to split left and right sides
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
                splitPane.setDividerLocation(400); // Set initial divider location

                /*new RayCastPositionerSliderAdder(bdvFront).run();
                new RayCastPositionerSliderAdder(bdvRight).run();
                new RayCastPositionerSliderAdder(bdvBottom).run();

                BdvHandleHelper.addCenterCross(bdvFront);
                //BdvHandleHelper.addCenterCross(bdvBottom);
                //BdvHandleHelper.addCenterCross(bdvRight);*/

                // Add the split pane to the frame
                frame.add(splitPane, BorderLayout.CENTER);

                // Make the frame visible
                frame.setVisible(true);

                ViewerOrthoSyncStarter starter = new ViewerOrthoSyncStarter(
                        new ViewerAdapter(bdvFront), new ViewerAdapter(bdvBottom), new ViewerAdapter(
                        bdvRight), true);
                starter.run();

                new ViewerTransformSyncStarter(
                        new ViewerAdapter[]{new ViewerAdapter(bdvFront), new ViewerAdapter(bvv)}, true
                ).run();

                new ViewerStateSyncStarter(new ViewerAdapter(bdvFront), new ViewerAdapter(
                        bdvBottom), new ViewerAdapter(bdvRight), new ViewerAdapter(bvv)).run();

                // I don't use BdvFunctions in order to keep the correct colors
                ds.show(bdvFront, sources);


                for (SourceAndConverter<?> source : sources) {
                    new BrightnessAutoAdjuster<>( source, 0 ).run();
                }

            });

        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}