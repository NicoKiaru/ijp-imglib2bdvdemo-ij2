package ch.epfl.biop.demos.utils;

import bdv.cache.SharedQueue;
import bdv.util.Procedural3DImageShort;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command;
import ch.epfl.biop.atlas.struct.Atlas;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.FinalInterval;
import net.imglib2.display.LinearRange;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import sc.fiji.bdvpg.scijava.command.source.LUTSourceCreatorCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.MandelbrotSourceGetter;
import sc.fiji.bdvpg.sourceandconverter.importer.VoronoiSourceGetter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class DemoDatasetHelper {

    public static SourceAndConverter<?>[] getData(DemoDataset datasetName, Context ctx) throws IOException, ExecutionException, InterruptedException, IllegalArgumentException {
        CommandService cs = ctx.getService(CommandService.class);
        SourceAndConverterService ss = ctx.getService(SourceAndConverterService.class);
        ModuleService ms = ctx.getService(ModuleService.class);
        switch (datasetName) {
            case EGG_CHAMBER:
                File eggChamber = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/1472859/files/DrosophilaEggChamber.tif");
                // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
                AbstractSpimData<?> datasetEC = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                        true,
                        "datasetname", "Egg_Chamber",
                        "unit", "MICROMETER",
                        "files", new File[]{eggChamber},
                        "split_rgb_channels", false,
                        "plane_origin_convention", "CENTER",
                        "auto_pyramidize", true,
                        "disable_memo", false
                ).get().getOutput("spimdata");
                return ss.getSourceAndConverterFromSpimdata(datasetEC).toArray(new SourceAndConverter<?>[0]);

            case BRAIN_SLICES:
                File wsiBrainSlices = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");
                // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
                AbstractSpimData<?> datasetBS = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                        true,
                        "datasetname", "Egg_Chamber",
                        "unit", "MICROMETER",
                        "files", new File[]{wsiBrainSlices},
                        "split_rgb_channels", false,
                        "plane_origin_convention", "TOP LEFT",
                        "auto_pyramidize", true,
                        "disable_memo", false
                ).get().getOutput("spimdata");
                return ss.getSourceAndConverterFromSpimdata(datasetBS).toArray(new SourceAndConverter<?>[0]);

            case MANDELBROT_SET:
                SourceAndConverter<UnsignedShortType> mandelbrotSource = new MandelbrotSourceGetter().get();
                return (SourceAndConverter<?>[])
                        ms.run(cs.getCommand(LUTSourceCreatorCommand.class), true,
                                "sacs", new SourceAndConverter[]{mandelbrotSource}
                        ).get().getOutput("sacs_out");

            case SLOW_MANDELBROT_SET:

                Source<UnsignedShortType> s = new Procedural3DImageShort(p -> {
                    double re = p[0];
                    double im = p[1];
                    int i = 0;
                    for (; i < 2500; ++i) {
                        final double squre = re * re;
                        final double squim = im * im;
                        if (squre + squim > 4) break;
                        im = 2 * re * im + p[1];
                        re = squre - squim + p[0];
                    }
                    return i;
                }).getSource("Mandelbrot Set (Slow)");

                return (SourceAndConverter<?>[])
                    ms.run(cs.getCommand(LUTSourceCreatorCommand.class), true,
                            "sacs", new SourceAndConverter[]{SourceAndConverterHelper.createSourceAndConverter(s)}
                    ).get().getOutput("sacs_out");

            case ALLEN_BRAIN_ATLAS:
                Atlas atlas = (Atlas) cs.run(AllenBrainAdultMouseAtlasCCF2017v3p1Command.class, true).get().getOutput("ba");

                // What channels do we have in this atlas ?
                // System.out.println(atlas.getMap().getImagesKeys());
                // The output will be: [Nissl, Ara, Label Borders, X, Y, Z, Left Right]

                // Let's collect the first two channels
                SourceAndConverter<?> nissl = atlas.getMap().getStructuralImages().get("Nissl");
                SourceAndConverter<?> ara = atlas.getMap().getStructuralImages().get("Ara");
                return new SourceAndConverter[]{nissl, ara};
            case LATTICE_HELA_SKEWED:
                File f = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

                cs.run(LLS7OpenDatasetCommand.class, true,
                        "czi_file", f,
                        "legacy_xy_mode", false).get();

                String datasetNameLattice = FilenameUtils.removeExtension(f.getName());

                return ctx.getService(SourceAndConverterService.class).getUI().getSourceAndConvertersFromPath(datasetNameLattice)
                        .toArray(new SourceAndConverter[0]);

            case RANDOM_GAME_OF_LIFE:

                FunctionRandomAccessible<UnsignedShortType> random =
                        new FunctionRandomAccessible<>(3,
                                (position, pixel) -> {
                                    pixel.set((Math.random() > 0.5)?16:0);
                                }, UnsignedShortType::new);

                SharedQueue queue = new SharedQueue(
                        Runtime.getRuntime().availableProcessors()-1
                );

                SourceAndConverter<UnsignedShortType> gol = GameOfLifeSourcev2.getSourceAndConverter(queue,
                        Views.interval(random, FinalInterval.createMinMax(0,0,0,512,512,1)), 500);

                ((LinearRange) gol.getConverter()).setMax(17);
                ((LinearRange) gol.asVolatile().getConverter()).setMax(17);

                return new SourceAndConverter[]{gol};
            case PLATY:
                try {
                    return SafeDataset.getPlaty();
                } catch (Exception e) {
                    throw new RuntimeException("You need to install the MoBIE update site in order to use the Platy dataset");
                }
            case VORONOI_BIG:
                SourceAndConverter<FloatType> voronoi_big = new VoronoiSourceGetter(new long[]{4096*128, 4096*128, 4096*128}, 10000000, false).get();
                SourceAndConverter<?>[] reColoredVoronoi_big = (SourceAndConverter<?>[])
                        ctx.getService(ModuleService.class).run(ctx.getService(CommandService.class).getCommand(LUTSourceCreatorCommand.class), true,
                                "sacs", new SourceAndConverter[]{voronoi_big}
                        ).get().getOutput("sacs_out");

                return reColoredVoronoi_big;
            case VORONOI_SMALL:
                SourceAndConverter<FloatType> voronoi_small = new VoronoiSourceGetter(new long[]{128, 128, 128}, 10000, false).get();
                SourceAndConverter<?>[] reColoredVoronoi_small = (SourceAndConverter<?>[])
                        ctx.getService(ModuleService.class).run(ctx.getService(CommandService.class).getCommand(LUTSourceCreatorCommand.class), true,
                                "sacs", new SourceAndConverter[]{voronoi_small}
                        ).get().getOutput("sacs_out");

                return reColoredVoronoi_small;
            case EUROPE_PYRAMIDIZE:
                File europePyramidize = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/12738352/files/easterness_edtm_m_240m_s_20000101_20221231_eu_epsg.3035_v20240528.tif");
                // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
                AbstractSpimData<?> datasetEuropePyramidize = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                        true,
                        "datasetname", "Egg_Chamber",
                        "unit", "MICROMETER",
                        "files", new File[]{europePyramidize},
                        "split_rgb_channels", false,
                        "plane_origin_convention", "CENTER",
                        "auto_pyramidize", true,
                        "disable_memo", false
                ).get().getOutput("spimdata");
                return ss.getSourceAndConverterFromSpimdata(datasetEuropePyramidize).toArray(new SourceAndConverter<?>[0]);
            case EUROPE:
                File europe = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/12738352/files/easterness_edtm_m_240m_s_20000101_20221231_eu_epsg.3035_v20240528.tif");
                // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
                AbstractSpimData<?> datasetEurope = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                        true,
                        "datasetname", "Egg_Chamber",
                        "unit", "MICROMETER",
                        "files", new File[]{europe},
                        "split_rgb_channels", false,
                        "plane_origin_convention", "CENTER",
                        "auto_pyramidize", false,
                        "disable_memo", false
                ).get().getOutput("spimdata");
                return ss.getSourceAndConverterFromSpimdata(datasetEurope).toArray(new SourceAndConverter<?>[0]);
        }

        throw new IllegalArgumentException("Unrecognized dataset "+datasetName);
    }

    public enum DemoDataset {
        BRAIN_SLICES,
        EGG_CHAMBER,
        MANDELBROT_SET,
        SLOW_MANDELBROT_SET,
        ALLEN_BRAIN_ATLAS,
        RANDOM_GAME_OF_LIFE,
        LATTICE_HELA_SKEWED,
        VORONOI_SMALL,
        VORONOI_BIG,
        PLATY,
        EUROPE_PYRAMIDIZE,
        EUROPE
    }

    public static boolean isBvvCompatible(SourceAndConverter<?>[] sources) {
        return Arrays.stream(sources)
                .allMatch(source ->
                        (source.getSpimSource().getType() instanceof UnsignedShortType)
                                && !(source.getSpimSource().getClass().getName().contains(Procedural3DImageShort.class.getName())) // Remove mandelbrot set
                );
    }
}
