package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import ch.epfl.biop.demos.utils.GenerativeMultiResolutionSource;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;

@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Resolution Level")
public class DemoResolutionLevelCommand implements Command {

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Override
    public void run() {

        BdvHandle bdvh = ds.getNewBdv();
        Source<UnsignedShortType> src = new GenerativeMultiResolutionSource(100,"Source");
        BdvFunctions.show(src, BdvOptions.options().addTo(bdvh));
    }

}
