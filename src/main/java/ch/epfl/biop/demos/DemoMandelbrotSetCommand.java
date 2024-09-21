package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.command.source.LUTSourceCreatorCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.sourceandconverter.importer.MandelbrotSourceGetter;

import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Mandelbrot Set")
public class DemoMandelbrotSetCommand implements Command {

    @Parameter
    CommandService cs;

    @Parameter
    ModuleService ms;

    @Parameter
    LogService log;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Override
    public void run() {
        try {
            BdvHandle bdvh = ds.getNewBdv();
            SourceAndConverter<UnsignedShortType> mandelbrotSource = new MandelbrotSourceGetter().get();

            SourceAndConverter<?>[] reColoredMandelbrotSource = (SourceAndConverter<?>[])
                    ms.run(cs.getCommand(LUTSourceCreatorCommand.class), true,
                        "sacs", new SourceAndConverter[]{mandelbrotSource}
                ).get().getOutput("sacs_out");

            ds.show(bdvh, reColoredMandelbrotSource);
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        }

    }
}
