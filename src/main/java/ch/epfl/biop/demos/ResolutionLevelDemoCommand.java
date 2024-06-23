package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import ch.epfl.biop.demos.utils.GenerativeMultiResolutionSource;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Resolution Level")
public class ResolutionLevelDemoCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Override
    public void run() {
        Source src = new GenerativeMultiResolutionSource(100,"Source");
        BdvFunctions.show(src, BdvOptions.options().addTo(bdvh));
    }

}
