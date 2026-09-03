#@ File    (label = "Image to deconvolve (multi-channel)", style = "open")                                             imageFile
#@ File    (label = "PSF image (single channel)",           style = "open")                                            psfFile
#@ File    (label = "Output folder",                        style = "directory")                                       outputFolder
#@ String  (label = "Coordinate unit", choices = {"MICROMETER","MILLIMETER","NANOMETER","PIXEL"}, value = "MICROMETER") unit
#@ String  (label = "Output pixel type", choices = {"Keep Pixel Type Of Original Image","Float"}, value = "Keep Pixel Type Of Original Image") outputPixelType
#@ Integer (label = "Block size X (pixels)",                value = 256)                                                blockSizeX
#@ Integer (label = "Block size Y (pixels)",                value = 256)                                                blockSizeY
#@ Integer (label = "Block size Z (pixels)",                value = 64)                                                 blockSizeZ
#@ Integer (label = "Block overlap (pixels)",              value = 16)                                                 overlapSize
#@ Integer (label = "Number of iterations",                value = 20)                                                 numIterations
#@ Boolean (label = "Non-circulant (reduce edge artefacts)", value = true)                                             nonCirculant
#@ Double  (label = "Regularization factor",               value = 0.001)                                              regularizationFactor
#@ Integer (label = "Number of GPU streams / threads",     value = 4)                                                  nThreads
#@ Boolean (label = "Show sources in BigDataViewer",       value = true)                                               showInBdv
#@ String  (label = "OME-TIFF compression", choices = {"LZW","Uncompressed","JPEG-2000","JPEG-2000 Lossy","JPEG"}, value = "LZW") compression
#@ Integer (label = "OME-TIFF resolution levels",          value = 1)                                                  nResolutionLevels
#@ Boolean (label = "Overwrite output if it already exists", value = false)                                            overwrite

#@ CommandService cs
#@ SourceService sourceService

/*
 * Deconvolve a multi-channel image with a matching single-channel PSF and save
 * the result as an OME-TIFF.
 *
 * Pipeline (all through BigDataViewer-Playground commands, lazily / block-by-block):
 *   1. Open the image and the PSF through Bio-Formats as BDV sources.
 *   2. (optionally) show both in BigDataViewer.
 *   3. Run tiled, lazy Richardson-Lucy GPU deconvolution (CLIJ2).
 *   4. Export the deconvolved sources to <outputFolder>/<imageName>.ome.tiff,
 *      keeping the original channel order.
 *
 * The deconvolution is lazy: the output OME-TIFF file is what actually triggers
 * the computation, block by block, on the GPU.
 *
 * Author: BIOP - EPFL. Free to reuse.
 */

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand
import ch.epfl.biop.command.process.deconvolve.SourcesDeconvolveCommand
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand
import bdv.viewer.SourceAndConverter
import mpicbg.spim.data.generic.AbstractSpimData
import org.apache.commons.io.FilenameUtils
import sc.fiji.bdvpg.service.SourceServices

// ---- 0. Resolve and validate the output file -------------------------------

def imageName  = FilenameUtils.removeExtension(imageFile.getName())
def psfName    = FilenameUtils.removeExtension(psfFile.getName())
def outputFile = new File(outputFolder, imageName + ".ome.tiff")

if (outputFile.exists()) {
    if (overwrite) {
        if (!outputFile.delete()) {
            throw new IllegalStateException("Could not delete pre-existing output file: " + outputFile)
        }
    } else {
        throw new IllegalStateException("Output file already exists (tick 'Overwrite' to replace it): " + outputFile)
    }
}

// The OME-TIFF exporter only accepts MILLIMETER or MICROMETER; map anything else.
def exportUnit = (unit == "MILLIMETER") ? "MILLIMETER" : "MICROMETER"

// Helper: open a file with Bio-Formats and return its sources, in setup/channel order.
def openSources = { File file, String name ->
    AbstractSpimData spimData = cs.run(DatasetFromBioFormatsCreateCommand, true,
            "datasetname",             name,
            "unit",                    unit,
            "files",                   ([file] as File[]),
            "split_rgb_channels",      false,
            "auto_pyramidize",         true,
            "plane_origin_convention", "TOP LEFT",
            "disable_memo",            false
    ).get().getOutput("spimdata") as AbstractSpimData

    return sourceService.getSourcesFromDataset(spimData)
}

// ---- 1. Open the image and the PSF -----------------------------------------

def imageSources = openSources(imageFile, imageName)     // multi-channel: one source per channel
def psfSources   = openSources(psfFile,   psfName)       // single channel

if (imageSources.isEmpty()) throw new IllegalStateException("No source found in image file: " + imageFile)
if (psfSources.isEmpty())   throw new IllegalStateException("No source found in PSF file: "   + psfFile)

def psfSource = psfSources.get(0)                        // one PSF for all channels

println "Opened '" + imageName + "' : " + imageSources.size() + " channel(s)"
println "Opened PSF '" + psfName + "' : " + psfSources.size() + " source(s), using the first one"

// ---- 2. Optionally show the raw sources in BDV -----------------------------

if (showInBdv) {
    def bdvRaw = SourceServices.getBdvDisplayService().getNewBdv()
    SourceServices.getBdvDisplayService().show(bdvRaw, imageSources as SourceAndConverter[])
}

// ---- 3. Run the tiled, lazy Richardson-Lucy GPU deconvolution --------------

def deconvolved = cs.run(SourcesDeconvolveCommand, true,
        "sources",               (imageSources as SourceAndConverter[]),
        "psf",                   psfSource,
        "output_pixel_type",     outputPixelType,
        "suffix",                "_deconvolved",
        "block_size_x",          blockSizeX,
        "block_size_y",          blockSizeY,
        "block_size_z",          blockSizeZ,
        "overlap_size",          overlapSize,
        "num_iterations",        numIterations,
        "non_circulant",         nonCirculant,
        "regularization_factor", regularizationFactor.floatValue(),
        "n_threads",             nThreads
).get().getOutput("sources_out") as SourceAndConverter[]

println "Deconvolution set up for " + deconvolved.length + " channel(s) (computed lazily on export)"

// ---- 4. Optionally show the deconvolved sources in a second BDV ------------

if (showInBdv) {
    def bdvDec = SourceServices.getBdvDisplayService().getNewBdv()
    SourceServices.getBdvDisplayService().show(bdvDec, deconvolved)
}

// ---- 5. Export the deconvolved sources to OME-TIFF -------------------------
// Channels are exported in the same order as the input (deconvolved[i] <-> imageSources[i]).
// Leaving range_* blank exports every channel / slice / timepoint.

cs.run(KheopsExportSourcesCommand, true,
        "sources",              deconvolved,
        "range_channels",       "",
        "range_slices",         "",
        "range_frames",         "",
        "file",                 outputFile,
        "unit",                 exportUnit,
        "override_voxel_size",  false,
        "n_resolution_levels",  nResolutionLevels,
        "downscaling",          2,
        "tile_size_x",          512,
        "tile_size_y",          512,
        "n_threads",            nThreads,
        "compression",          compression,
        "compress_temp_files",  false
).get()

println "Done - deconvolved OME-TIFF written to: " + outputFile.getAbsolutePath()
