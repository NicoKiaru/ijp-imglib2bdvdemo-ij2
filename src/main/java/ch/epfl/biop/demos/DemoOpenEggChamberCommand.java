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
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Open Egg Chamber Dataset")
public class DemoOpenEggChamberCommand implements Command {

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    LogService log;

    @Override
    public void run() {
        try {
            // Downloads and cache the sample file (90Mb)
            File eggChamber = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/1472859/files/DrosophilaEggChamber.tif");

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", "Egg_Chamber",
                    "unit", "MICROMETER",
                    "files", new File[]{eggChamber},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "CENTER",
                    "auto_pyramidize", true,
                    "disable_memo", false
            ).get().getOutput("spimdata");

            SourceAndConverter<?>[] eggChamberSources = ss.getSourceAndConverterFromSpimdata(dataset).toArray(new SourceAndConverter<?>[0]);

            BdvHandle bdvh = ds.getNewBdv();

            // I don't use BdvFunctions in order to keep the correct colors
            ds.show(bdvh, eggChamberSources);

            // Let's center the viewer on the egg chamber
            new ViewerTransformAdjuster( bdvh, eggChamberSources[0] ).run();

            // And adjust Brightness and Contrast
            for (SourceAndConverter<?> source : eggChamberSources) {
                new BrightnessAutoAdjuster<>( source, 0 ).run();
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
        }
    }
}
