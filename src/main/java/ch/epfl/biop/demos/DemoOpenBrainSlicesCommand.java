package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DatasetHelper;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Open Brain Slices Dataset")
public class DemoOpenBrainSlicesCommand implements Command {

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

            SourceAndConverter<?>[] brainSlicesSources = DatasetHelper.getData(DatasetHelper.DemoDataset.BRAIN_SLICES, ctx);

            BdvHandle bdvh = ds.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdvh, brainSlicesSources);

            // Let's center the viewer on the egg chamber
            new ViewerTransformAdjuster( bdvh, brainSlicesSources ).run();

            // And adjust Brightness and Contrast
            for (SourceAndConverter<?> source : brainSlicesSources) {
                new BrightnessAutoAdjuster<>( source, 0 ).run();
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            log.error(e);
        }
    }
}
