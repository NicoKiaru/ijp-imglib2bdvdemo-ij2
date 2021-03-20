package ch.epfl.biop.ij2command;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import net.imagej.ImageJ;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.IOException;
import java.net.URL;

/**
 * This example illustrates how to create an ImageJ 2 {@link Command} plugin.
 * The pom file of this project is customized for the PTBIOP Organization (biop.epfl.ch)
 * <p>
 * The code here is opening the biop website. The command can be tested in the java DummyCommandTest class.
 * </p>
 */

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Imglib2 Bdv>Procedural 2D image")
public class Procedural2DImage implements Command {

    @Override
    public void run() {

        int nDimensions = 2;

        FunctionRealRandomAccessible<DoubleType> wave =
                new FunctionRealRandomAccessible<>(nDimensions, (position, pixel) -> {

                    double px = position.getDoublePosition(0);
                    double py = position.getDoublePosition(1);

                    pixel.set(Math.cos(px)*Math.sin(py));

                }, () -> new DoubleType());

        BdvHelper.display2D(wave, 255, 120, 0, -1, 1,"Wave",null);
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(Procedural2DImage.class, true);
    }
}
