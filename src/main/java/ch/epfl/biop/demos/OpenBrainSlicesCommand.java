package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Open Brain Slices Dataset")
public class OpenBrainSlicesCommand implements Command {

    @Parameter
    CommandService cs;

    @Parameter
    BdvHandle bdvh;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    LogService log;

    @Override
    public void run() {
        try {
            // Downloads and cache a sample  vsi file (1.3Gb) from https://zenodo.org/records/6553641
            File wsiBrainSlices = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", "Egg_Chamber",
                    "unit", "MICROMETER",
                    "files", new File[]{wsiBrainSlices},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true
            ).get().getOutput("spimdata");

            SourceAndConverter<?>[] brainSlicesSources = ss.getSourceAndConverterFromSpimdata(dataset).toArray(new SourceAndConverter<?>[0]);

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdvh, brainSlicesSources);

            // Let's center the viewer on the egg chamber
            new ViewerTransformAdjuster( bdvh, brainSlicesSources ).run();

            // And adjust Brightness and Contrast
            for (SourceAndConverter<?> source : brainSlicesSources) {
                new BrightnessAutoAdjuster<>( source, 0 ).run();
            }
        } catch (InterruptedException | ExecutionException | IOException e) {
            log.error(e);
        }
    }
}
