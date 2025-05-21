package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
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
