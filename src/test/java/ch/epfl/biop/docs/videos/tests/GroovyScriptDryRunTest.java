package ch.epfl.biop.docs.videos.tests;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Timeline;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * Dry-runs the deconvolution demo's command invocations through
 * {@link CommandExecutor#previewToScript()} (no Fiji, no Robot) and prints
 * the resulting {@code script.groovy}. Use it to iterate on the Groovy
 * emitter without re-recording the whole video.
 */
public class GroovyScriptDryRunTest {

    @Test
    public void emitDeconvolutionScript() throws Exception {
        ScreenRecorder.ENABLED = false;
        Screenshotter.ENABLED = false;
        Timeline.ENABLED = false;

        Assets.session("DeconvolutionScriptDryRun");

        File fileDataset = new File("C:\\datasets\\ZeissLLS7Demo.czi");
        File filePSF = new File("C:\\datasets\\psf-400nm.tif");
        String datasetName = "ZeissLLS7Demo";
        String psfName = "psf-400nm";
        int tIni = 28, tEnd = 30;
        File exportFile = new File("C:\\Users\\Nicolas\\Desktop",
                datasetName + "_deconvolved_t" + tIni + "-" + tEnd + ".ome.tiff");

        GroovyScript.nameFile(fileDataset, "datasetFile", "HeLa LLS7 dataset (CZI)");
        GroovyScript.nameFile(filePSF,     "psfFile",     "400 nm point-spread function");
        GroovyScript.nameFile(exportFile,  "exportFile",  "Output OME-TIFF");

        GroovyScript.beginSection("open-hela", "Open the raw HeLa CZI through Bio-Formats.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", datasetName, "Name the dataset after the file itself.")
                .arg("unit", "MICROMETER", "Coordinates in microns.")
                .arg("files", new File[]{fileDataset}, "The HeLa CZI.")
                .arg("split_rgb_channels", false)
                .arg("auto_pyramidize", true)
                .arg("plane_origin_convention", "TOP LEFT")
                .arg("disable_memo", false)
                .viaTreePath()
                .previewToScript();
        GroovyScript.endSection();

        GroovyScript.beginSection("open-psf", "Open the matching 400 nm PSF.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", psfName)
                .arg("unit", "MICROMETER")
                .arg("files", new File[]{filePSF}, "The PSF.")
                .arg("split_rgb_channels", false)
                .arg("auto_pyramidize", true)
                .arg("plane_origin_convention", "TOP LEFT")
                .arg("disable_memo", false)
                .viaTreePath()
                .previewToScript();
        GroovyScript.endSection();

        GroovyScript.beginSection("deconvolve", "Lazy Richardson-Lucy deconvolution, 20 iters.");
        CommandExecutor.of(SourcesDeconvolveCommand.class)
                .arg("sources", datasetName, "HeLa input.")
                .arg("psf", psfName, "PSF.")
                .arg("output_pixel_type", "Keep Pixel Type Of Original Image")
                .arg("suffix", "_deconvolved")
                .arg("block_size_x", 256)
                .arg("block_size_y", 256)
                .arg("block_size_z", 64)
                .arg("overlap_size", 16)
                .arg("num_iterations", 20)
                .arg("non_circulant", true)
                .arg("regularization_factor", 0.001f, "Light regularization.")
                .arg("n_threads", 4)
                .viaTreePath()
                .previewToScript();
        GroovyScript.endSection();

        GroovyScript.beginSection("export-ome-tiff", "Export deconvolved sources as OME-TIFF.");
        CommandExecutor.of(KheopsExportSourcesCommand.class)
                .arg("range_frames", tIni + ":" + tEnd, "Limit timepoint range.")
                .arg("file", exportFile, "Output OME-TIFF.")
                .arg("unit", "MICROMETER")
                .arg("override_voxel_size", false)
                .arg("n_resolution_levels", 1)
                .arg("tile_size_x", 512)
                .arg("tile_size_y", 512)
                .arg("n_threads", 4)
                .viaTreePath("Other Sources", "Picking the deconvolved branch.")
                .previewToScript();
        GroovyScript.endSection();

        File out = new File(Assets.dir(), GroovyScript.FILENAME);
        assertTrue("script.groovy was not written: " + out, out.exists());
        String content = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        System.out.println("===== " + out.getAbsolutePath() + " =====");
        System.out.println(content);

        // Light structural checks.
        assertTrue(content.contains("#@CommandService cs"));
        assertTrue(content.contains("import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand"));
        assertTrue(content.contains("cs.run(SourcesDeconvolveCommand.class, true"));
        // File args must hoist as #@File params, not inline new File(...)
        assertTrue(content.contains("#@File"));
        assertTrue(!content.contains("new File(\"C:"));
        // Preferred names from nameFile(...) must replace the fileN fallback.
        assertTrue("datasetFile param missing", content.contains("#@File (label=\"HeLa LLS7 dataset (CZI)\""));
        assertTrue(content.contains(") datasetFile"));
        assertTrue(content.contains(") psfFile"));
        assertTrue(content.contains(") exportFile"));
        assertTrue("auto fallback file1 leaked", !content.contains(") file1\n"));
    }
}
