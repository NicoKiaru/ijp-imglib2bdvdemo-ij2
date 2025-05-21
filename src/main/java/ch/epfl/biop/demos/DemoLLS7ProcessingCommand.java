package ch.epfl.biop.demos;

import bdv.cache.SharedQueue;
import bdv.util.BdvHandle;
import bdv.util.EmptySource;
import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.demos.utils.DemoDatasetHelper;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.haesleinhuepf.clijx.imglib2cache.Clij2RichardsonLucyImglib2Cache;
import net.haesleinhuepf.clijx.parallel.CLIJxPool;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Lattice Light Sheet Processing")
public class DemoLLS7ProcessingCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    boolean deconvolve;

    @Override
    public void run() {
        try {

            // Getting data
            SourceAndConverter<?>[] lls7Channels = DemoDatasetHelper.getData(DemoDatasetHelper.DemoDataset.LATTICE_HELA_SKEWED, ctx);

            // Getting PSF
            File psfFile = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14505724/files/psf-200nm.tif");
            AbstractSpimData<?> datasetEC = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
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
                SourceAndConverter<?> psf = ss.getSourceAndConverterFromSpimdata(datasetEC).toArray(new SourceAndConverter<?>[0])[0];

                Clij2RichardsonLucyImglib2Cache.Builder builder =
                        Clij2RichardsonLucyImglib2Cache.builder()
                                .nonCirculant(false)
                                .numberOfIterations(40)
                                .psf((RandomAccessibleInterval<? extends RealType<?>>) psf.getSpimSource().getSource(0,0))
                                .overlap(64)
                                .useGPUPool(CLIJxPool.getInstance()) // in fact this is the default behaviour, but one can specify a different pool if necessary here
                                .regularizationFactor(0.0001f);

                for (int i = 0;i< lls7Channels.length; i++) {

                    llsDeconvolved[i] = getDeconvolved(
                            (SourceAndConverter) lls7Channels[i],
                            lls7Channels[i].getSpimSource().getName()+"_Deconvolved",
                            new int[]{512, 512, 128},
                            builder,
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

            SourceAndConverter<?> model = new EmptySourceAndConverterCreator("Cropped Region", p.at3D, p.nx, p.ny, p.nz).get();

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

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static <T extends RealType<T>> SourceAndConverter<FloatType> getDeconvolved(final SourceAndConverter<T> source,
                                                                                       String name,
                                                                                       int[] cellDimensions,
                                                                                       Clij2RichardsonLucyImglib2Cache.Builder deconvolveBuilder,
                                                                                       SharedQueue queue) {

        List<Clij2RichardsonLucyImglib2Cache<FloatType, T, T>> ops = new ArrayList<>();

        int numTimepoints = SourceAndConverterHelper.getMaxTimepoint(source);

        // create the version of clij2 RL that works on cells
        for (int t = 0; t<numTimepoints; t++) {
            // One op per timepoint, but because the same builder is reused, the same gpu pool will be shared
            ops.add((Clij2RichardsonLucyImglib2Cache<FloatType, T, T>) deconvolveBuilder/*.psf(psf)*/.rai(source.getSpimSource().getSource(t,0)).build());
        }

        VoxelProcessedSource.Processor<T, FloatType> deconvolver =
                new VoxelProcessedSource.Processor<T, FloatType>() {

                    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer,RandomAccessibleInterval<FloatType>>> cachedRAIs
                            = new ConcurrentHashMap<>();

                    RandomAccessibleInterval<FloatType> buildSource(RandomAccessibleInterval<T> rai, int t, int level) {

                        CellGrid grid = new CellGrid(rai.dimensionsAsLongArray(), cellDimensions);
                        FloatType type = new FloatType();
                        Cache<Long, Cell<?>> cache = (new GlobalLoaderCache(new Object(), t, level))
                                .withLoader(LoadedCellCacheLoader.get(grid, cell -> {
                                    ops.get(t).accept(cell);
                                }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        CachedCellImg img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.BYTE, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        return img;
                    }

                    @Override
                    public synchronized RandomAccessibleInterval<FloatType> process(RandomAccessibleInterval<T> rai, int t, int level) {
                        if (!cachedRAIs.containsKey(t)) {
                            cachedRAIs.put(t, new ConcurrentHashMap<>());
                        }
                        if (!cachedRAIs.get(t).containsKey(level)) {
                            cachedRAIs.get(t).put(level, buildSource(rai, t, level));
                        }
                        return cachedRAIs.get(t).get(level);
                    }
                };

        return new SourceVoxelProcessor<>(name, source, deconvolver, new FloatType(), queue).get();
    }
}
