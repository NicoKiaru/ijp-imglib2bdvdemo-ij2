package ch.epfl.biop;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;

import java.io.File;
import java.util.List;

/**
 * Compile-time mirror of Deconvolve_And_Save_OMETIFF.groovy — NOT run, just a
 * strongly-typed twin so the Java compiler validates the command classes,
 * service methods and output types the groovy script relies on.
 */
public class DeconvolveAndSaveCompileCheck {

    public static void main(String[] args) throws Exception {

        CommandService cs = null;
        SourceService sourceService = null;

        File imageFile = new File("image.tif");
        File psfFile = new File("psf.tif");
        File outputFile = new File("out.ome.tiff");
        String unit = "MICROMETER";
        String outputPixelType = "Keep Pixel Type Of Original Image";
        String compression = "LZW";

        // ---- open image ----
        AbstractSpimData<?> imageSpimData = (AbstractSpimData<?>) cs.run(
                DatasetFromBioFormatsCreateCommand.class, true,
                "datasetname", "image",
                "unit", unit,
                "files", new File[]{imageFile},
                "split_rgb_channels", false,
                "auto_pyramidize", true,
                "plane_origin_convention", "TOP LEFT",
                "disable_memo", false
        ).get().getOutput("spimdata");

        List<SourceAndConverter<?>> imageSources =
                sourceService.getSourcesFromDataset(imageSpimData);

        AbstractSpimData<?> psfSpimData = (AbstractSpimData<?>) cs.run(
                DatasetFromBioFormatsCreateCommand.class, true,
                "files", new File[]{psfFile}
        ).get().getOutput("spimdata");

        List<SourceAndConverter<?>> psfSources =
                sourceService.getSourcesFromDataset(psfSpimData);

        SourceAndConverter<?> psfSource = psfSources.get(0);

        // ---- show ----
        BdvHandle bdvRaw = SourceServices.getBdvDisplayService().getNewBdv();
        SourceServices.getBdvDisplayService().show(bdvRaw,
                imageSources.toArray(new SourceAndConverter[0]));

        // ---- deconvolve ----
        SourceAndConverter<?>[] deconvolved = (SourceAndConverter<?>[]) cs.run(
                SourcesDeconvolveCommand.class, true,
                "sources", imageSources.toArray(new SourceAndConverter[0]),
                "psf", psfSource,
                "output_pixel_type", outputPixelType,
                "suffix", "_deconvolved",
                "block_size_x", 256,
                "block_size_y", 256,
                "block_size_z", 64,
                "overlap_size", 16,
                "num_iterations", 20,
                "non_circulant", true,
                "regularization_factor", 0.001f,
                "n_threads", 4
        ).get().getOutput("sources_out");

        BdvHandle bdvDec = SourceServices.getBdvDisplayService().getNewBdv();
        SourceServices.getBdvDisplayService().show(bdvDec, deconvolved);

        // ---- export ----
        cs.run(KheopsExportSourcesCommand.class, true,
                "sources", deconvolved,
                "range_channels", "",
                "range_slices", "",
                "range_frames", "",
                "file", outputFile,
                "unit", unit,
                "override_voxel_size", false,
                "n_resolution_levels", 1,
                "downscaling", 2,
                "tile_size_x", 512,
                "tile_size_y", 512,
                "n_threads", 4,
                "compression", compression,
                "compress_temp_files", false
        ).get();
    }
}