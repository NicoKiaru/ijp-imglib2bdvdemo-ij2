package ch.epfl.biop.demos;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

import java.io.File;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Bdv Supplier", initializer = "init")
public class BdvSupplierCommand extends DynamicCommand {

    @Parameter
    Context ctx;

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
            BdvHandle bdvh;
            switch (choice) {
                case "Alpha": {
                        AlphaSerializableBdvOptions options = new AlphaSerializableBdvOptions();
                        options.is2D = true;
                        options.white_bg = false;
                        bdvh = new AlphaBdvSupplier(options).get();
                    } break;
                case "Alpha inverted": {
                    AlphaSerializableBdvOptions options = new AlphaSerializableBdvOptions();
                    options.is2D = true;
                    options.white_bg = true;
                    bdvh = new AlphaBdvSupplier(options).get();
                } break;
                case "BIOP": {
                        BiopSerializableBdvOptions options = new BiopSerializableBdvOptions();
                        options.is2D = true;
                        bdvh = new BiopBdvSupplier(options).get();
                    } break;
                default:
                    {   SerializableBdvOptions options = new SerializableBdvOptions();
                        options.is2D = true;
                        bdvh = new DefaultBdvSupplier(options).get();
                    } break;
            }

            // Downloads and cache a sample  vsi file (1.3Gb) from https://zenodo.org/records/6553641
            File wsiBrainSlices = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", "Brain Slice",
                    "unit", "MICROMETER",
                    "files", new File[]{wsiBrainSlices},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true
            ).get().getOutput("spimdata");

            SourceAndConverter<?>[] dapiSources = sourceService.getUI().getRoot().child("Brain Slice")
                    .child("Channel").child("FL DAPI").sources();

            SourceAndConverter<?>[] fredSources = sourceService.getUI().getRoot().child("Brain Slice")
                    .child("Channel").child("FL CY3").sources();

            // I don't use BdvFunctions in order to keep the correct colors
            displayService.show(bdvh, dapiSources);
            displayService.show(bdvh, fredSources);

        } catch (Exception e) {
            logger.error(e);
        }

    }
}
