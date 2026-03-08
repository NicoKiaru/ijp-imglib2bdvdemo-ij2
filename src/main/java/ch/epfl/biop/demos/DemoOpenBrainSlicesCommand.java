package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.Context;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Open Brain Slices Dataset")
        }
)
public class DemoOpenBrainSlicesCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context ctx;

    @Parameter
    CommandService cs;

    @Parameter
    SourceBdvDisplayService ds;

    @Parameter
    SourceService ss;

    @Parameter
    LogService log;

    @Override
    public void run() {
        try {

            SourceAndConverter<?>[] brainSlicesSources = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.BRAIN_SLICES, ctx);

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
