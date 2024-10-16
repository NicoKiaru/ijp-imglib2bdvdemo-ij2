package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.util.BdvHandlePanel;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvOptions;
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command;
import ch.epfl.biop.atlas.struct.Atlas;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.RayCastPositionerSliderAdder;
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
import java.awt.Color;
import java.io.File;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - OrthoViewer")
public class DemoOrthoViewerCommand implements Command {

    @Parameter(choices = {"Egg Chamber", "Allen Atlas"})
    String dataset_choice;

    @Parameter
    CommandService cs;

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

            ;
            SourceAndConverter<?>[] sources;

            if (dataset_choice.equals("Egg Chamber")) {
                // Downloads and cache the sample file (90Mb)
                File eggChamber = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/1472859/files/DrosophilaEggChamber.tif");

                // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
                AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                        true,
                        "datasetname", "Egg_Chamber",
                        "unit", "MICROMETER",
                        "files", new File[]{eggChamber},
                        "split_rgb_channels", false,
                        "plane_origin_convention", "CENTER",
                        "auto_pyramidize", true,
                        "disable_memo", false
                ).get().getOutput("spimdata");
                sources = ss.getSourceAndConverterFromSpimdata(dataset).toArray(new SourceAndConverter<?>[0]);

            } else {
                Atlas atlas = (Atlas) cs.run(AllenBrainAdultMouseAtlasCCF2017v3p1Command.class, true).get().getOutput("ba");

                // What channels do we have in this atlas ?
                System.out.println(atlas.getMap().getImagesKeys());
                // The output will be: [Nissl, Ara, Label Borders, X, Y, Z, Left Right]

                // Let's collect the first two channels
                SourceAndConverter<?> nissl = atlas.getMap().getStructuralImages().get("Nissl");
                SourceAndConverter<?> ara = atlas.getMap().getStructuralImages().get("Ara");
                sources = new SourceAndConverter[]{nissl, ara};
            }


            /*BdvHandle bdvh = ds.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdvh, eggChamberSources);

            // Let's center the viewer on the egg chamber
            new ViewerTransformAdjuster( bdvh, eggChamberSources[0] ).run();

            // And adjust Brightness and Contrast
            for (SourceAndConverter<?> source : eggChamberSources) {
                new BrightnessAutoAdjuster<>( source, 0 ).run();
            }*/

            JFrame frame = new JFrame("Draggable Quadrants with Panels");

            bdvFront = ds.getNewBdv();
            // Let's center the viewer on the egg chamber
            //new ViewerTransformAdjuster( bdvFront, eggChamberSources[0] ).run();
            BdvHandleHelper.getJFrame(bdvFront).setVisible(false);

            bdvRight = ds.getNewBdv();
            BdvHandleHelper.getJFrame(bdvRight).setVisible(false);

            bdvBottom = ds.getNewBdv();
            BdvHandleHelper.getJFrame(bdvBottom).setVisible(false);

            bvv = new BvvCreator(BvvOptions.options()).get();
            BvvHandleHelper.getJFrame(bvv).setVisible(false);

            /*ds.registerBdvHandle(bdvFront);
            ds.registerBdvHandle(bdvRight);
            ds.registerBdvHandle(bdvRight);*/

            SwingUtilities.invokeLater(() -> {
                // Create the main frame

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(800, 600);
                frame.setLayout(new BorderLayout());

                // Create the right panel with a JLabel
                JLabel rightLabel = new JLabel("Right Side");
                rightLabel.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel rightPanel = new JPanel(new BorderLayout());
                rightPanel.add(bdvFront.getCardPanel().getComponent(), BorderLayout.CENTER);

                // Create the left panel, which will be divided into 4 parts using JSplitPane
                JPanel leftPanel = createLeftPanel();

                // Create a JSplitPane to split left and right sides
                JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
                splitPane.setDividerLocation(400); // Set initial divider location

                new RayCastPositionerSliderAdder(bdvFront).run();
                new RayCastPositionerSliderAdder(bdvRight).run();
                new RayCastPositionerSliderAdder(bdvBottom).run();

                BdvHandleHelper.addCenterCross(bdvFront);
                BdvHandleHelper.addCenterCross(bdvBottom);
                BdvHandleHelper.addCenterCross(bdvRight);

                // Add the split pane to the frame
                frame.add(splitPane, BorderLayout.CENTER);

                // Make the frame visible
                frame.setVisible(true);
            });

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


        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        }
    }

    // Method to create the left panel with synchronized vertical dividers
    private JPanel createLeftPanel() {
        // Create JPanel to hold the entire left side
        JPanel leftPanel = new JPanel(new BorderLayout());

        // Create four JPanels for the quadrants
        JPanel topLeftPanel = new JPanel();
        topLeftPanel.setBackground(Color.RED);
        topLeftPanel.add(bdvFront.getViewerPanel());

        JPanel topRightPanel = new JPanel();
        topRightPanel.setBackground(Color.GREEN);
        topRightPanel.add(bdvRight.getViewerPanel());

        JPanel bottomLeftPanel = new JPanel();
        bottomLeftPanel.setBackground(Color.BLUE);
        bottomLeftPanel.add(bdvBottom.getViewerPanel());

        JPanel bottomRightPanel = new JPanel();
        bottomRightPanel.setBackground(Color.YELLOW);
        bottomRightPanel.add(bvv.getViewerPanel());
        //bottomRightPanel.add(new JLabel("Bottom Right"));

        // Split the top part (topLeft and topRight)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bdvFront.getViewerPanel(), bdvRight.getViewerPanel());
        topSplitPane.setDividerLocation(200); // Initial divider position
        topSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Split the bottom part (bottomLeft and bottomRight)
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bdvBottom.getViewerPanel(), bottomRightPanel);
        bottomSplitPane.setDividerLocation(200); // Initial divider position
        bottomSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Synchronize the vertical dividers of top and bottom
        synchronizeVerticalDividers(topSplitPane, bottomSplitPane);

        // Finally, split the top and bottom parts vertically
        JSplitPane verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, bottomSplitPane);
        verticalSplitPane.setDividerLocation(150); // Initial divider position
        verticalSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Add the vertical split pane to the left panel
        leftPanel.add(verticalSplitPane, BorderLayout.CENTER);

        return leftPanel;
    }

    // Method to synchronize the vertical dividers between two JSplitPanes
    private static void synchronizeVerticalDividers(JSplitPane topSplitPane, JSplitPane bottomSplitPane) {
        // Listen for changes in the top split pane's divider
        topSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            // When the top divider changes, update the bottom divider to the same position
            int newLocation = (int) evt.getNewValue();
            bottomSplitPane.setDividerLocation(newLocation);
        });

        // Listen for changes in the bottom split pane's divider
        bottomSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            // When the bottom divider changes, update the top divider to the same position
            int newLocation = (int) evt.getNewValue();
            topSplitPane.setDividerLocation(newLocation);
        });
    }
}