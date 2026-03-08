package ch.epfl.biop.demos;

import bdv.cache.SharedQueue;
import bdv.util.BdvHandle;
import bdv.util.EmptySource;
import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.source.SourceVoxelProcessor;
import ch.epfl.biop.source.deconvolve.Deconvolver;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.importer.EmptySourceCreator;
import sc.fiji.bdvpg.source.transform.SourceResampler;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Lattice Light Sheet Processing")
        }
)
public class DemoLLS7ProcessingCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context ctx;

    @Parameter
    SourceService ss;

    @Parameter
    CommandService cs;

    @Parameter
    SourceBdvDisplayService ds;

    @Parameter
    boolean deconvolve;

    @Override
    public void run() {
        try {

            // Getting data
            SourceAndConverter<?>[] lls7Channels = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ctx);

            // Getting PSF
            File psfFile = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-200nm.tif");
            AbstractSpimData<?> datasetEC = (AbstractSpimData<?>) cs.run(DatasetFromBioFormatsCreateCommand.class,
                    true,
                    "datasetname", "PSF_LLS7_200nm",
                    "unit", "MICROMETER",
                    "files", new File[]{psfFile},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "CENTER",
                    "auto_pyramidize", true,
                    "disable_memo", false
            ).get().getOutput("spimdata");

            SourceAndConverter[] llsDeconvolved = new SourceAndConverter[lls7Channels.length];

            if (deconvolve) {
                SourceAndConverter<?> psf = ss.getSourcesFromDataset(datasetEC).toArray(new SourceAndConverter<?>[0])[0];

                /*Clij2RichardsonLucyImglib2Cache.Builder builder =
                        Clij2RichardsonLucyImglib2Cache.builder()
                                .nonCirculant(false)
                                .numberOfIterations(40)
                                .psf((RandomAccessibleInterval<? extends RealType<?>>) psf.getSpimSource().getSource(0,0))
                                .overlap(12)
                                .useGPUPool(CLIJxPool.getInstance()) // in fact this is the default behaviour, but one can specify a different pool if necessary here
                                .regularizationFactor(0.0001f);*/

                for (int i = 0;i< lls7Channels.length; i++) {

                    llsDeconvolved[i] = Deconvolver.getDeconvolvedCast(
                            (SourceAndConverter) lls7Channels[i],
                            lls7Channels[i].getSpimSource().getName()+"_Deconvolved",
                            new int[]{128-32, 128-32, 128-32},
                            new int[]{32, 32, 32},
                            40,
                            false,0.001f,
                            (SourceAndConverter<? extends RealType<?>>) psf,
                            new SharedQueue(4,1)
                    );
                }
            }

            EmptySource.EmptySourceParams p = new EmptySource.EmptySourceParams();

            p.nx = (int) (50/0.144);
            p.ny = (int) (50/0.144);
            p.nz = (int) (20/0.144);
            p.setVoxelDimensions("MICROMETER", 0.144, 0.144, 0.144);
            p.name = "Regions where to resample the data";
            p.at3D = new AffineTransform3D();

            p.at3D.scale(0.144, 0.144, 0.144);
            p.at3D.translate(3400,
                    -550,
                    -915);

            SourceAndConverter<?> model = new EmptySourceCreator("Cropped Region", p.at3D, p.nx, p.ny, p.nz).get();

            SourceAndConverter[] processed = new SourceAndConverter[lls7Channels.length];

            for (int i = 0;i< lls7Channels.length; i++) {
                processed[i] = new SourceResampler<>(null,
                        model, "LLS7_processed_ch_"+i, false,
                true, false, 0).apply(
                        deconvolve?llsDeconvolved[i]:(SourceAndConverter)lls7Channels[i]);
            }

            // Display:

            BdvHandle bdvh = ds.getNewBdv();
            ds.show(bdvh, processed);
            new ViewerTransformAdjuster(bdvh, processed).run();

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


}
