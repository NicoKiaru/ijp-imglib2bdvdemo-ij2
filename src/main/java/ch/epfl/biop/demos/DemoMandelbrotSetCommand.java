package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Mandelbrot Set")
public class DemoMandelbrotSetCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Visualizing the Mandelbrot Set</h1>\n" +
            "    <p>This demo illustrates how to load and visualize the Mandelbrot set using ImgLib2 and BigDataViewer (BDV). The Mandelbrot set is a famous fractal, which is visualized here using image processing libraries.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Setting Up the Viewer</h3>\n" +
            "    <p>The demo begins by creating a new <code>BdvHandle</code> to display the Mandelbrot set. This handle represents the viewer that will render the fractal.</p>\n" +
            "\n" +
            "    <h3>2. Loading the Mandelbrot Set Data</h3>\n" +
            "    <p>The demo uses a helper class <code>DatasetHelper</code> to retrieve the Mandelbrot set data. The data is fetched as a predefined dataset and is then ready to be visualized.</p>\n" +
            "\n" +
            "    <h3>3. Displaying the Mandelbrot Set</h3>\n" +
            "    <p>The loaded Mandelbrot set data is displayed in the BDV viewer. The data is passed to the display service, which handles the rendering of the image in the viewer.</p>\n" +
            "\n" +
            "    <h3>4. Error Handling</h3>\n" +
            "    <p>The demo includes error handling to manage potential issues that may arise during the loading and display of the Mandelbrot set data. This includes handling exceptions related to interruptions, execution, and input/output operations.</p>\n" +
            "\n" +
            "    <p>This demo showcases the use of ImgLib2 and BDV to visualize complex mathematical structures like the Mandelbrot set, providing an interactive and insightful view into the world of fractals.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;


    @Parameter
    CommandService cs;

    @Parameter
    ModuleService ms;

    @Parameter
    Context ctx;

    @Parameter
    LogService log;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Override
    public void run() {
        try {
            BdvHandle bdvh = ds.getNewBdv();
            ds.show(bdvh, DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.MANDELBROT_SET, ctx));
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
