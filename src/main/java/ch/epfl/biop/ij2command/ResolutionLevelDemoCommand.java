package ch.epfl.biop.ij2command;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.Source;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Imglib2 Bdv>Resolution level Demo")
public class ResolutionLevelDemoCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Override
    public void run() {
        Source src = new GenerativeMultiResolutionSource(100,"Source");
        BdvFunctions.show(src, BdvOptions.options().addTo(bdvh));
    }

    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(ResolutionLevelDemoCommand.class, true);
    }
}
