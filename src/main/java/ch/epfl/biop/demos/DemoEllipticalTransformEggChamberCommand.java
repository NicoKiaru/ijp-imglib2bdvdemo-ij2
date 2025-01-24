package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import org.scijava.Context;
import ch.epfl.biop.demos.utils.DatasetHelper;
import ch.epfl.biop.scijava.command.source.register.SourcesRealTransformCommand;
import ch.epfl.biop.scijava.command.transform.DisplayEllipseFromTransformCommand;

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

            SourceAndConverter<?>[] eggChamberSources = DatasetHelper.getData(DatasetHelper.DemoDataset.EGG_CHAMBER, ctx);

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
