package ch.epfl.biop.demos;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import org.embl.mobie.io.imagedata.N5ImageData;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = "Plugins>BIOP>Demos>Demo - Open N5",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Open N5")
        },
        initializer = "init")
public class DemoOpenN5Command extends DynamicCommand {

    @Parameter
    Context ctx;

    @Parameter
    String url;

    @Parameter
    SourceBdvDisplayService displayService;

    @Parameter
    SourceService sourceService;

    @Parameter
    CommandService cs;

    @Parameter
    LogService logger;

    @Override
    public void run() {
        try {
            N5ImageData< ? > n5ImageData = new N5ImageData<>(url);
            SourceAndConverter<?>[] sources = n5ImageData.getSourcesAndConverters().toArray(new SourceAndConverter[0]);

            BdvHandle bdvh = displayService.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            displayService.show(bdvh, sources);

            new ViewerTransformAdjuster(bdvh, sources).run();

        } catch (Exception e) {
            logger.error(e);
        }

    }
}
