package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.viewer.ViewerAdapter;
import sc.fiji.bdvpg.viewer.ViewerTransformSyncStarter;

import java.io.IOException;
import java.util.concurrent.ExecutionException;


@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Lazy Pyramidize 2D Image")
        }
)
public class DemoLazyDownscaling implements BdvPlaygroundActionCommand {

    @Parameter
    Context ctx;

    @Parameter
    SourceBdvDisplayService display;

    @Parameter
    SourceService sourceService;

    @Override
    public void run() {

        try {
            SourceAndConverter<?> europePyramidize = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.EUROPE_PYRAMIDIZE, ctx)[0];
            SourceAndConverter<?> europe = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.EUROPE, ctx)[0];

            BiopSerializableBdvOptions options = new BiopSerializableBdvOptions();
            options.is2D = true;

            BdvHandle bdv1 = new BiopBdvSupplier(options).get();
            BdvHandle bdv2 = new BiopBdvSupplier(options).get();

            display.show(bdv1, europePyramidize);
            display.show(bdv2, europe);

            sourceService.getConverterSetup(europe).setColor(new ARGBType(ARGBType.rgba(255,255,255,255)));
            sourceService.getConverterSetup(europePyramidize).setColor(new ARGBType(ARGBType.rgba(255,255,255,255)));

            // Let's sync the viewer views
            new ViewerTransformSyncStarter(new ViewerAdapter[]{
                    new ViewerAdapter(bdv1.getViewerPanel()),
                    new ViewerAdapter(bdv2.getViewerPanel())}, true).run();

            // Let's adjust the view on the source. One is enough since they are synchronized
            new ViewerTransformAdjuster(bdv1, europe).run();


        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}
