package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import org.scijava.Context;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.scijava.command.source.register.SourcesRealTransformCommand;
import ch.epfl.biop.scijava.command.transform.DisplayEllipseFromTransformCommand;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Elliptical Transform Egg Chamber")
public class DemoEllipticalTransformEggChamberCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String demodescription = "<html><h1>Elliptical Transformation of an Egg Chamber</h1>\n" +
            "    <p>This demo illustrates how to apply an elliptical transformation to an image dataset of an egg chamber using ImgLib2 and BigDataViewer (BDV). The transformation helps in visualizing the dataset in a more meaningful way by adjusting its geometry.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>The demo begins by loading the egg chamber dataset using a helper class <code>DatasetHelper</code>. This dataset is stored in an array of <code>SourceAndConverter</code> objects, which are used to handle and display the image data.</p>\n" +
            "\n" +
            "    <h3>2. Initializing the Viewer</h3>\n" +
            "    <p>A new BDV handle is created to display the dataset. The viewer is centered on the egg chamber, and brightness and contrast are automatically adjusted for better visualization.</p>\n" +
            "\n" +
            "    <h3>3. Setting Up the Elliptical Transformation</h3>\n" +
            "    <p>An <code>Elliptical3DTransform</code> is initialized with specific parameters to define the elliptical shape and its orientation. These parameters include radii along the three axes and rotation angles.</p>\n" +
            "\n" +
            "    <h3>4. Displaying the Ellipsoid</h3>\n" +
            "    <p>The location of the ellipse used for the transformation is displayed in the viewer. This helps in visualizing the region of interest and the effect of the transformation.</p>\n" +
            "\n" +
            "    <h3>5. Applying the Transformation</h3>\n" +
            "    <p>The egg chamber dataset is transformed according to the elliptical transformation. The transformed dataset is then displayed in a new viewer.</p>\n" +
            "\n" +
            "    <h3>6. Interactive Parameter Adjustment</h3>\n" +
            "    <p>A runnable updater is set up to repaint the viewer each time a parameter of the elliptical transform is changed. This allows for interactive adjustment and visualization of the transformation parameters.</p>" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>\n";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

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

    @Override
    public void run() {
        try {

            SourceAndConverter<?>[] eggChamberSources = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.EGG_CHAMBER, ctx);

            BdvHandle bdvh = ds.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdvh, eggChamberSources);

            // Let's center the viewer on the egg chamber
            new ViewerTransformAdjuster(bdvh, eggChamberSources[0]).run();

            // And adjust Brightness and Contrast
            for (SourceAndConverter<?> source : eggChamberSources) {
                new BrightnessAutoAdjuster<>(source, 0).run();
            }

            Elliptical3DTransform e3Dt = new Elliptical3DTransform();
            e3Dt.setParameters(
                    "radiusX", 28.7,
                    "radiusY", 41.4,
                    "radiusZ", 31.8,
                    "rotationX", 0.0,
                    "rotationY", -0.457,
                    "rotationZ", 0.454,
                    "centerX", -6.9,
                    "centerY", 0.26,
                    "centerZ", -19.55);

            // Display the location of the ellipse used for the transform
            SourceAndConverter<?> ellipsoidSource = (SourceAndConverter<?>)
                    cs.run(DisplayEllipseFromTransformCommand.class, true,
                            "r_min", 0.9, "r_max", 1.1, "e3dt", e3Dt).get().getOutput("sac_out");

            new BrightnessAdjuster(ellipsoidSource, 0, 255).run();

            ds.show(bdvh, ellipsoidSource);

            BdvHandle viewer = ds.getNewBdv();

            // Let's transform the egg chamber source according to the elliptical transform

            SourceAndConverter<?>[] transformed_sources = (SourceAndConverter<?>[]) cs.run(SourcesRealTransformCommand.class, true,
                    "sources_in", eggChamberSources,
                    "rt", e3Dt).get().getOutput("sources_out");

            ds.show(viewer, transformed_sources);

            // If you want to play with the parameters live, and trigger a viewer update each time a parameter is changed

            Runnable updater = () -> viewer.getViewerPanel().requestRepaint();

            e3Dt.updateNotifiers.add(updater);
        } catch (Exception e) {
            log.error(e);
        }
    }
}
