package ch.epfl.biop.demos;


import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.source.SourceVoxelProcessor;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;

import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Fuse Tiles")
        }
)
public class DemoFusionCommand implements BdvPlaygroundActionCommand {

    @Parameter
    CommandService cs;

    @Parameter
    SourceService sourceService;

    @Parameter
    SourceBdvDisplayService displayService;

    @Parameter
    LogService logger;

    @Override
    public void run() {
        try {
            /*String rootURL = "https://zenodo.org/records/13680725/files/";
            int nTiles = 25;
            File[] tiles = new File[nTiles];
            for (int i = 0; i<nTiles; i++) {
                tiles[i] = DatasetHelper.getDataset(
                        rootURL + "tiling-sample-brain-section_A01_G001_"+
                                new DecimalFormat("0000").format(i+1)+
                                ".oir");
            }*/
            File f = DatasetHelper.getDataset("https://zenodo.org/records/8305531/files/MouseBrain_41Slices_2x2Tiles_3Channels_2Illuminations_1Angle.czi");

            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(DatasetFromBioFormatsCreateCommand.class, true,
                    "datasetname", "Tiles",
                    "unit", "BIGSTITCHER COMPATIBLE",
                    "files", new File[]{f},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true,
                    "disable_memo", false
                    ).get().getOutput("spimdata");

            List<SourceAndConverter<?>> sources = sourceService.getSourcesFromDataset(dataset);

            BdvHandle bdvh = displayService.getNewBdv();

            displayService.show(bdvh, sources.toArray(new SourceAndConverter[0]));

            new ViewerTransformAdjuster(bdvh, sources.toArray(new SourceAndConverter[0])).run();

        } catch (Exception e) {
            logger.error(e);
        }

    }
}
