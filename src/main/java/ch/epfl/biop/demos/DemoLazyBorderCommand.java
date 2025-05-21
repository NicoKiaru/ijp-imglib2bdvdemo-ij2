package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;


@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Lazy compute image border")
public class DemoLazyBorderCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter(persist = false)
    DemoDatasetHelper.DemoDataset dataset_name;

    @Parameter
    SourceAndConverterBdvDisplayService displayService;

    @Parameter
    LogService logger;

    @Override
    public void run() {
        try {

            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            int idx = 0;

            while ((idx<sources.length)&&!(sources[idx].getSpimSource().getType() instanceof RealType)) idx ++;

            if (idx == sources.length) {
                logger.error("No pixel compatible channel found for this demo.");
                return;
            }

            SourceAndConverter<?> source = sources[idx];

            if (sources.length>1) {
                logger.warn("For this demo only the first (pixel compatible) channel is taken into consideration");
            }

            SourceAndConverter<UnsignedByteType> borders = SourceVoxelProcessor.getBorders((SourceAndConverter) source);

            BdvHandle bdvh = displayService.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            displayService.show(bdvh, source);
            displayService.show(bdvh, borders);

            SourceAndConverterServices
                    .getSourceAndConverterService()
                    .getConverterSetup(borders).setDisplayRange(-10,100); // Display the 0 values in gray -> show progression

            new ViewerTransformAdjuster(bdvh, sources).run();

        } catch (Exception e) {
            logger.error(e);
        }

    }
}
