package ch.epfl.biop.demos.utils;

import bdv.cache.SharedQueue;
import bdv.util.Procedural3DImageShort;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command;
import ch.epfl.biop.atlas.struct.Atlas;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.FinalInterval;
import net.imglib2.display.LinearRange;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import sc.fiji.bdvpg.command.process.SourceWithLUTDuplicateCommand;
import sc.fiji.bdvpg.command.process.transform.SourceSimpleTransformCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.importer.MandelbrotSourceCreator;
import sc.fiji.bdvpg.source.importer.VoronoiSourceCreator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class DemoDatasetHelper {

    // ── BioFormats loading helper ──────────────────────────────────────────

    /**
     * Loads a dataset through BioFormats with commonly shared parameters.
     */
    private static AbstractSpimData<?> loadBioFormatsDataset(
            CommandService cs,
            File file,
            String datasetName,
            String planeOrigin,
            boolean autoPyramidize) throws ExecutionException, InterruptedException {

        return (AbstractSpimData<?>) cs.run(DatasetFromBioFormatsCreateCommand.class,
                true,
                "datasetname", datasetName,
                "unit", "MICROMETER",
                "files", new File[]{file},
                "split_rgb_channels", false,
                "plane_origin_convention", planeOrigin,
                "auto_pyramidize", autoPyramidize,
                "disable_memo", false
        ).get().getOutput("spimdata");
    }

    /**
     * Downloads a file, loads it via BioFormats, and returns the sources
     * looked up by dataset name through the source tree.
     */
    private static SourceAndConverter<?>[] loadFromUrlByTree(
            Context ctx,
            CommandService cs,
            String url,
            String planeOrigin,
            boolean autoPyramidize) throws IOException, ExecutionException, InterruptedException {

        File file = ch.epfl.biop.DatasetHelper.getDataset(url);
        String name = FilenameUtils.removeExtension(file.getName());
        loadBioFormatsDataset(cs, file, name, planeOrigin, autoPyramidize);
        return ctx.getService(SourceService.class).tree().getSources(name)
                .toArray(new SourceAndConverter[0]);
    }

    /**
     * Downloads a file, loads it via BioFormats, and returns the sources
     * directly from the SpimData dataset.
     */
    private static SourceAndConverter<?>[] loadFromUrlBySpimData(
            SourceService ss,
            CommandService cs,
            String url,
            String datasetName,
            String planeOrigin,
            boolean autoPyramidize) throws IOException, ExecutionException, InterruptedException {

        File file = ch.epfl.biop.DatasetHelper.getDataset(url);
        AbstractSpimData<?> dataset = loadBioFormatsDataset(cs, file, datasetName, planeOrigin, autoPyramidize);
        return ss.getSourcesFromDataset(dataset).toArray(new SourceAndConverter<?>[0]);
    }

    // ── LUT re-coloring helper ─────────────────────────────────────────────

    /**
     * Wraps one or more sources with a LUT via {@link SourceWithLUTDuplicateCommand}.
     */
    private static SourceAndConverter<?>[] recolorWithLUT(
            ModuleService ms,
            CommandService cs,
            SourceAndConverter<?>... sources) throws ExecutionException, InterruptedException {

        return (SourceAndConverter<?>[]) ms.run(
                cs.getCommand(SourceWithLUTDuplicateCommand.class), true,
                "sacs", sources
        ).get().getOutput("sacs_out");
    }

    // ── Main entry point ───────────────────────────────────────────────────

    public static SourceAndConverter<?>[] getData(DemoDataset datasetName, Context ctx)
            throws IOException, ExecutionException, InterruptedException, IllegalArgumentException {

        CommandService cs = ctx.getService(CommandService.class);
        SourceService ss = ctx.getService(SourceService.class);
        ModuleService ms = ctx.getService(ModuleService.class);

        switch (datasetName) {

            // ── BioFormats datasets retrieved via SpimData ─────────────────

            case EGG_CHAMBER:
                return loadFromUrlBySpimData(ss, cs,
                        "https://zenodo.org/records/1472859/files/DrosophilaEggChamber.tif",
                        "Egg_Chamber", "CENTER", true);

            case BRAIN_SLICES:
                File wsiBrainSlices = new File(
                        ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");
                AbstractSpimData<?> datasetBS = loadBioFormatsDataset(
                        cs, wsiBrainSlices, "Slide_03", "TOP LEFT", true);
                return ss.getSourcesFromDataset(datasetBS).toArray(new SourceAndConverter<?>[0]);

            case EUROPE_PYRAMIDIZE:
                return loadFromUrlBySpimData(ss, cs,
                        "https://zenodo.org/records/12738352/files/easterness_edtm_m_240m_s_20000101_20221231_eu_epsg.3035_v20240528.tif",
                        "Egg_Chamber", "CENTER", true);

            case EUROPE:
                return loadFromUrlBySpimData(ss, cs,
                        "https://zenodo.org/records/12738352/files/easterness_edtm_m_240m_s_20000101_20221231_eu_epsg.3035_v20240528.tif",
                        "Egg_Chamber", "CENTER", false);

            // ── BioFormats datasets retrieved via source tree ──────────────

            case LATTICE_HELA_SKEWED:
                return loadFromUrlByTree(ctx, cs,
                        "https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi",
                        "TOP LEFT", true);

            case LATTICE_HELA_SKEWED_TIMELAPSE:
                return loadFromUrlByTree(ctx, cs,
                        "https://zenodo.org/records/19047136/files/ZeissLLS7Demo.czi",
                        "TOP LEFT", true);

            case BRAIN_SECTION_3DTILES:
                SourceAndConverter<?>[] sources = loadFromUrlByTree(ctx, cs,
                        "https://zenodo.org/records/19062791/files/BrainSection-Tiles.lif",
                        "TOP LEFT", true);
                // They need to be flipped
                cs.run(SourceSimpleTransformCommand.class, true,
                        "sources", sources,
                        "type", "Rot270",
                        "axis", "Z",
                        "ini_timepoint", 0,
                        "n_timepoints", 1,
                        "global_change", false
                        ).get();
                return sources;

            case LATTICE_PSF_200NM:
                return loadFromUrlByTree(ctx, cs,
                        "https://zenodo.org/records/14505724/files/psf-200nm.tif",
                        "TOP LEFT", true);

            case LATTICE_PSF_400NM:
                return loadFromUrlByTree(ctx, cs,
                        "https://zenodo.org/records/14505724/files/psf-400nm.tif",
                        "TOP LEFT", true);

            // ── Generative sources ─────────────────────────────────────────

            case MANDELBROT_SET:
                SourceAndConverter<UnsignedShortType> mandelbrotSource = new MandelbrotSourceCreator().get();
                return recolorWithLUT(ms, cs, mandelbrotSource);

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
                return recolorWithLUT(ms, cs, SourceHelper.createSourceAndConverter(s));

            case VORONOI_BIG:
                SourceAndConverter<FloatType> voronoiBig =
                        new VoronoiSourceCreator(new long[]{4096 * 128, 4096 * 128, 4096 * 128}, 10000000, false).get();
                return recolorWithLUT(ms, cs, voronoiBig);

            case VORONOI_SMALL:
                SourceAndConverter<FloatType> voronoiSmall =
                        new VoronoiSourceCreator(new long[]{128, 128, 128}, 10000, false).get();
                return recolorWithLUT(ms, cs, voronoiSmall);

            case RANDOM_GAME_OF_LIFE:
                FunctionRandomAccessible<UnsignedShortType> random =
                        new FunctionRandomAccessible<>(3,
                                (position, pixel) -> pixel.set((Math.random() > 0.5) ? 16 : 0),
                                UnsignedShortType::new);
                SharedQueue queue = new SharedQueue(
                        Runtime.getRuntime().availableProcessors() - 1);
                SourceAndConverter<UnsignedShortType> gol = GameOfLifeSourcev2.getSourceAndConverter(
                        queue,
                        Views.interval(random, FinalInterval.createMinMax(0, 0, 0, 512, 512, 1)),
                        500);
                ((LinearRange) gol.getConverter()).setMax(17);
                ((LinearRange) gol.asVolatile().getConverter()).setMax(17);
                return new SourceAndConverter[]{gol};

            // ── Atlas / remote sources ─────────────────────────────────────

            case ALLEN_BRAIN_ATLAS:
                Atlas atlas = (Atlas) cs.run(AllenBrainAdultMouseAtlasCCF2017v3p1Command.class, true)
                        .get().getOutput("ba");
                SourceAndConverter<?> nissl = atlas.getMap().getStructuralImages().get("Nissl");
                SourceAndConverter<?> ara = atlas.getMap().getStructuralImages().get("Ara");
                return new SourceAndConverter[]{nissl, ara};

            case PLATY:
                try {
                    return SafeDataset.getPlaty();
                } catch (Exception e) {
                    throw new RuntimeException(
                            "You need to install the MoBIE update site in order to use the Platy dataset");
                }

            case MACRO:
                try {
                    return SafeDataset.getMacro();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage());
                }

            default:
                throw new IllegalArgumentException("Unrecognized dataset " + datasetName);
        }
    }

    // ── Dataset enum ───────────────────────────────────────────────────────

    public enum DemoDataset {
        BRAIN_SLICES("Mouse Brain Sections (1.3Gb, SXYC)"),
        EGG_CHAMBER("Fly Egg Chamber (90Mb, XYZC)"),
        MANDELBROT_SET("Mandelbrot Set (Generative, XY)"),
        SLOW_MANDELBROT_SET("Slow Mandelbrot Set (Generative, XY)"),
        ALLEN_BRAIN_ATLAS("Allen Brain Atlas (3Gb, XYZC)"),
        RANDOM_GAME_OF_LIFE("Game Of Life (Generative)"),
        LATTICE_HELA_SKEWED("Hela Kyoto, LLS7 Skewed, 1 Timepoint (3Gb, XYz'C)"),
        LATTICE_HELA_SKEWED_TIMELAPSE("Hela Kyoto Division, LLS7 Skewed, 60 Timepoint (15Gb, XYz'CT)"),
        LATTICE_PSF_200NM("LLS7 Skewed PSF 200 NM (15Gb, XYz')"),
        LATTICE_PSF_400NM("LLS7 Skewed PSF 400 NM (15Gb, XYz')"),
        VORONOI_SMALL("Voronoi Sample Dataset (Generative, XYZ)"),
        VORONOI_BIG("Big Voronoi Sample Dataset (Generative, XYZ)"),
        PLATY("Platy EM, (Streamed, XYZ)"),
        BRAIN_SECTION_3DTILES("Dentate Gyrus Tiles (4Gb, SXYZC)"),
        EUROPE_PYRAMIDIZE("Europe Height Map Pyramidized (110Mb, XY)"),
        EUROPE("Europe Height Map (110Mb, XY)"),
        MACRO("Mistery dataset");

        final String name;

        DemoDataset(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────

    public static boolean isBvvCompatible(SourceAndConverter<?>[] sources) {
        return Arrays.stream(sources)
                .allMatch(source ->
                        (!(source.getSpimSource().getType() instanceof ARGBType))
                                && !(source.getSpimSource().getClass().getName()
                                .contains(Procedural3DImageShort.class.getName()))
                );
    }
}