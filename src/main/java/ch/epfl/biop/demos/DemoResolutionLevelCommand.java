package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import ch.epfl.biop.demos.utils.GenerativeMultiResolutionSource;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;

@SuppressWarnings("unused")
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Resolution Level")
        }
)
public class DemoResolutionLevelCommand implements BdvPlaygroundActionCommand {

    @Parameter
    SourceBdvDisplayService ds;

    @Override
    public void run() {

        BdvHandle bdvh = ds.getNewBdv();
        Source<UnsignedShortType> src = new GenerativeMultiResolutionSource(100,"Source");
        BdvFunctions.show(src, BdvOptions.options().addTo(bdvh));
    }

}
