package ch.epfl.biop.demos;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Bdv Supplier", initializer = "init")
public class DemoBdvSupplierCommand extends DynamicCommand {

    @Parameter
    Context ctx;

    @Parameter(persist = false)
    DemoDatasetHelper.DemoDataset dataset_name;

    @Parameter(choices = {"Default", "BIOP", "Alpha", "Alpha inverted"})
    String choice;

    @Parameter
    SourceAndConverterBdvDisplayService displayService;

    @Parameter
    SourceAndConverterService sourceService;

    @Parameter
    CommandService cs;

    @Parameter
    LogService logger;

    @Override
    public void run() {
        try {

            SourceAndConverter<?>[] sources = DemoDatasetHelper.getData(dataset_name, ctx);

            BdvHandle bdvh;
            switch (choice) {
                case "Alpha": {
                        AlphaSerializableBdvOptions options = new AlphaSerializableBdvOptions();
                        options.white_bg = false;
                        bdvh = new AlphaBdvSupplier(options).get();
                    } break;
                case "Alpha inverted": {
                    AlphaSerializableBdvOptions options = new AlphaSerializableBdvOptions();
                    options.white_bg = true;
                    bdvh = new AlphaBdvSupplier(options).get();
                } break;
                case "BIOP": {
                        BiopSerializableBdvOptions options = new BiopSerializableBdvOptions();
                        bdvh = new BiopBdvSupplier(options).get();
                    } break;
                default:
                    {   SerializableBdvOptions options = new SerializableBdvOptions();
                        bdvh = new DefaultBdvSupplier(options).get();
                    } break;
            }

            // I don't use BdvFunctions in order to keep the correct colors
            displayService.show(bdvh, sources);

            new ViewerTransformAdjuster(bdvh, sources).run();

        } catch (Exception e) {
            logger.error(e);
        }

    }
}
