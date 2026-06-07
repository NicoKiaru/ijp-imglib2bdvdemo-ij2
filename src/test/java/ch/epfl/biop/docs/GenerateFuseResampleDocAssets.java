package ch.epfl.biop.docs;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.SourcesPyramidizeCommand;
import ch.epfl.biop.command.process.resample.SourcesFuseAndResampleCommand;
import ch.epfl.biop.command.process.resample.SourcesGridModelMakeCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.Demo;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.process.transform.SourceSimpleTransformCommand;
import sc.fiji.bdvpg.command.workspace.tree.SourceInspectCommand;

import java.awt.Frame;
import java.awt.Insets;
import java.io.File;

/**
 * Generates a Fuse-and-Resample tiled-stitching demo for video documentation.
 * <p>
 * Uses the BrainSection-Tiles dataset (2 channels, many tiles, LIF). Every
 * tile is loaded, flipped, and shown in a first BDV; a resampling grid is
 * defined; each channel is fused onto that grid; the fused channels are
 * shown in a second BDV; finally the fused sources are pyramidized and
 * displayed in a third BDV.
 *
 * <p>Workflow (each step a separate clip + script line + auto-screenshot):
 * <ol>
 *   <li>boot Fiji, open the Tasks panel and the BDV Playground tree;</li>
 *   <li>load the tiled .lif via Bio-Formats;</li>
 *   <li>apply Rot270 around Z to each tile so they read upright;</li>
 *   <li>show every tile in BDV — "BDV - Raw Tiles";</li>
 *   <li>apply Rot90 globally so the brain section sits in its anatomical orientation;</li>
 *   <li>re-frame the active BDV onto the mosaic;</li>
 *   <li>inspect one raw tile to read its voxel size (justifies the grid choice);</li>
 *   <li>define a resampling grid spanning all tiles;</li>
 *   <li>fuse channel 0 onto the grid;</li>
 *   <li>fuse channel 1 onto the grid;</li>
 *   <li>show both fused channels in a second BDV — "BDV - Fused";</li>
 *   <li>pyramidize the two fused sources;</li>
 *   <li>show the pyramidized sources in a third BDV — "BDV - Pyramidized";</li>
 *   <li>synchronise all three BDV views;</li>
 *   <li>inspect the pyramidized source to show its full provenance hierarchy;</li>
 *   <li>export one fused channel as a 4-level OME-TIFF pyramid (downscale 2),
 *       every fifth Z slice only to keep the demo short.</li>
 * </ol>
 *
 * <p>All three BDV windows share a single {@link Layout} area to the right
 * of the BDV Sources tree. Raw Tiles takes the full area while alone, then
 * shrinks to one of three side-by-side columns as Fused and Pyramidized join
 * it — so each call re-partitions the same area into a different grid.</p>
 */
public class GenerateFuseResampleDocAssets {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String[] args) throws Exception {

        // Flip both to true while iterating on this demo — every CommandExecutor
        // runs programmatically (no Robot, no dialog) and Ui frame placement /
        // pauses become instant. Flip back to false to record.
        CommandExecutor.FORCE_PROGRAMMATIC = false;
        Ui.FAST_MODE = false;

        // Asset capture toggles. Flip these to false while iterating on the
        // demo; flip them back on for the recording pass. Output lands in
        // target/video-assets/FuseResampleVideoDemo/ as NNN-step.{mp4,png}
        // plus a single timeline.json transcript.
        Assets.session("FuseResampleVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        Demo.intro("Tile Fusion (And Resampling) in Fiji",
                "Stitch a tiled brain-section .lif, fuse each channel onto a unified grid, "
                        + "watch raw / fused / pyramidized views stay in sync, then export one "
                        + "fused channel as a multi-resolution OME-TIFF.",
                "Lazy fusion — fused tiles are computed only when viewed",
                "Pyramid wrapper adds downsampled levels for smooth zoom-out",
                "Three BDV viewers — raw, flat-fused, pyramidized — synchronized side by side",
                "Export to a 4-level OME-TIFF pyramid straight from the fused source");

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/19062791/files/BrainSection-Tiles.lif");
        String datasetName = FilenameUtils.removeExtension(fileDataset.getName());

        // OME-TIFF export target — desktop, channel encoded in the name so a
        // viewer of the resulting file knows what's inside. Wiped at start so
        // the export step always runs against a clean slate (otherwise
        // OMETiffWriter throws when the target exists).
        File exportFile = new File("C:\\Users\\Nicolas\\Desktop",
                datasetName + "_fused_ch0.ome.tiff");
        if (exportFile.exists() && !exportFile.delete()) {
            throw new IllegalStateException(
                    "Could not delete pre-existing export file: " + exportFile);
        }

        // Name the File-typed inputs so the auto-generated script.groovy
        // surfaces them as readable #@File params instead of the anonymous
        // fileN fallback. The labels are what shows up in the SciJava
        // script-editor widget when a viewer re-runs the script.
        // Labels describe the role, not the specific instance — a viewer
        // re-running the script may point datasetFile at a completely
        // different file.
        GroovyScript.nameFile(fileDataset, "datasetFile", "Input tiled dataset");
        GroovyScript.nameFile(exportFile,  "exportFile",  "Output OME-TIFF");

        // Single placement area for every BDV window in this demo. Each phase
        // re-partitions it into a different grid: 1×1 (Raw Tiles full area)
        // → 1×3 with Fused spanning cols 1-2 → 1×3 with Pyramidized at col 2.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150, 900, 600);

        GenerateDocAssetsCommon.boot();

        Step.begin("Open Tasks Window",
                "Before we shrink Fiji out of the way, we open the Tasks panel — "
                        + "a click in the bottom-right corner of the Fiji main window. "
                        + "We'll watch the OME-TIFF export progress here at the end of the video.");
        Step.say("Clicking the small Tasks indicator at the bottom-right of the Fiji status bar.");
        Frame fijiFrame = Fiji.getFijiFrame();
        Insets fijiInsets = fijiFrame.getInsets();
        int tasksX = fijiFrame.getX() + fijiFrame.getWidth() - fijiInsets.right - 5;
        int tasksY = fijiFrame.getY() + fijiFrame.getHeight() - fijiInsets.bottom - 5;
        Ui.moveTo(tasksX, tasksY);
        Ui.click();
        Frame tasksFrame = Ui.waitForFrame("Tasks", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.placeFrame(tasksFrame, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        Ui.resizeFrame(tasksFrame, 450, 100);
        Step.end();

        GenerateDocAssetsCommon.showBdvTreeDisplay(true);

        Step.begin("Open Brain Tile Dataset",
                "We load the tiled Dentate Gyrus brain-section .lif through Bio-Formats — "
                        + "two channels, many tiles, lazily backed by the file.");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", datasetName,
                        "We name the dataset after the file itself — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "Browsing to the tiled brain-section .lif.")
                .arg("split_rgb_channels", false,
                        "RGB is irrelevant for this dataset.")
                .arg("auto_pyramidize", true,
                        "Auto-pyramidize so tile previews stay smooth at low zoom.")
                .arg("plane_origin_convention", "TOP LEFT",
                        "Top-left origin convention — matters here because the .lif has many tiles "
                                + "that need to land on the same mosaic frame.")
                .arg("disable_memo", false,
                        "We keep memoization — fast file opening on subsequent runs.")
                .sayOnDialog("Walking through the Bio-Formats importer dialog.")
                .viaTreePath()
                .execute();
        Step.say("The dataset opens lazily — every tile shows up under the dataset node, "
                + "grouped by channel.");
        Ui.rawPause(3000);
        Step.end();


        Step.begin("Show Brain Tile Dataset",
                "We display every tile in a BigDataViewer window — "
                        + "we'll set its title to 'BDV - Raw Tiles'.");
        Step.say("Right-clicking the dataset node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast so the mosaic reads at a glance.")
                .arg("adjust_view", true,
                        "Re-centering the view on the loaded tiles.")
                .arg("make_new_window", true,
                        "Open a fresh BDV window — leave any existing viewers untouched.")
                .arg("interpolate", false,
                        "No interpolation — voxel-precise tile rendering, so tile borders stay visible.")
                .sayOnDialog("Walking through the show-sources dialog.")
                .viaTreePath(datasetName,
                        "We select the raw tiled dataset for display.")
                .skipInScript()
                .execute();

        Frame bdvTiles = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvTiles, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Tiles",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.end();

        Step.begin("Rotate Each Tile In Place",
                "The tiles come out mirrored from Bio-Formats — Rot270 around Z, applied to each "
                        + "tile in place, brings them into the orientation the documentation expects.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourceSimpleTransformCommand.class, 2));
        CommandExecutor.of(SourceSimpleTransformCommand.class)
                .arg("type", "Rot270",
                        "Rot270 — a 270° rotation will un-mirror the tile content.")
                .arg("axis", "Z",
                        "Around the Z axis — rotating in the XY plane.")
                .arg("ini_timepoint", 0,
                        "Start at timepoint 0.")
                .arg("n_timepoints", 1,
                        "Single timepoint — this dataset is one volume.")
                .arg("global_change", false,
                        "Per-tile transform — each tile rotates around its own centre, "
                                + "so the mosaic layout is preserved.")
                .sayOnDialog("Walking through the transform dialog.")
                .viaTreePath(datasetName,
                        "We pick the tiled dataset whose tiles we want to flip.")
                .execute();
        Step.end();


        /*Step.begin("Rotate The Whole Mosaic",
                "Now Rot90 around Z applied globally — the whole mosaic rotates as one, "
                        + "so the brain section reads in its anatomical orientation.");
        CommandExecutor.of(SourceSimpleTransformCommand.class)
                .arg("type", "Rot90",
                        "Rot90 — a 90° rotation in the opposite sense.")
                .arg("axis", "Z",
                        "Same Z axis as before — XY plane rotation.")
                .arg("ini_timepoint", 0)
                .arg("n_timepoints", 1)
                .arg("global_change", true,
                        "Global transform — the mosaic as a whole rotates, so tile positions "
                                + "move together and the anatomy stays intact.")
                .sayOnDialog("Walking through the transform dialog again, this time with global_change=true.")
                .viaTreePath(fileDataset.getName(),
                        "Same dataset — but the transform now applies to the whole mosaic.")
                .execute();
        Step.end();*/

        Step.begin("Reframe View On The Mosaic",
                "The rotation moved the tiles out of the current viewport — "
                        + "double-clicking the dataset in the BDV Sources tree re-frames "
                        + "the active BDV onto every source beneath it.");
        Step.say("Double-clicking the dataset in the BDV Sources tree — BDV computes the bounding "
                + "box of every source beneath the node and zooms the view to fit.");
        BdvPlayground.adjustViewOnSource(datasetName);
        Step.end();


        Step.begin("Inspect One Raw Tile",
                "Before sizing the resampling grid, we inspect a single raw tile to read off its "
                        + "dimensions and voxel size — the numbers that justify the grid we're about to define.");
        Step.say("Right-clicking one tile under Channel > ch_0 > All Sources — walking to: "
                + CommandExecutor.menuPath(SourceInspectCommand.class, 2));
        CommandExecutor.of(SourceInspectCommand.class)
                .sayOnDialog("Walking through the inspect dialog.")
                .viaTreePath(datasetName + ">Channel>ch_0>All Sources>Tilescan 1-ch_0",
                        "We pick a single tile — every tile and channel in this dataset shares the same calibration.")
                .skipInScript()
                .execute();
        Step.say("Each tile is 512 × 512 × 90 voxels at 0.241 × 0.241 × 0.4 µm.");
        Step.waitForUser("Expand the new \"Inspect Results [Tilescan 1-ch_0]\" node and "
                + "multi-select the Dimensions and Voxel Size lines, then click Continue.");
        Step.end();

        Step.begin("Define A Resampling Grid",
                "Now we define the resampling grid: 0.25 µm in XY (slightly undersampled "
                        + "vs the 0.241 µm raw tiles we just inspected) and 0.4 µm in Z — "
                        + "the model source the fused output will sample onto.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourcesGridModelMakeCommand.class, 2));
        CommandExecutor.of(SourcesGridModelMakeCommand.class)
                .arg("vox_size_x", 0.25,
                        "0.25 µm in X — slightly undersampled relative to the 0.241 µm raw tiles, "
                                + "but close enough to preserve detail at tile borders.")
                .arg("vox_size_y", 0.25,
                        "Same 0.25 µm in Y — isotropic in-plane.")
                .arg("vox_size_z", 0.4,
                        "0.4 µm in Z — matches the section thickness of the raw acquisition.")
                .arg("timepoint", 0)
                .arg("n_timepoints", 1,
                        "Single-timepoint grid — this dataset is one volume.")
                .arg("n_resolution_levels", 1,
                        "One resolution level — we'll pyramidize the fused output later instead.")
                .arg("downscale_x", 2)
                .arg("downscale_y", 2)
                .arg("downscale_z", 2)
                .arg("name", "grid_model",
                        "Lands as a \"grid_model\" empty source under Other Sources.")
                .sayOnDialog("Walking through the grid-model dialog — the parameters that define the resampling target.")
                .viaTreePath(datasetName,
                        "We pick the tiled dataset — the grid is sized to span every tile in it.")
                .execute();
        Step.end();

        Step.begin("Fuse Channel 0 Onto The Grid",
                "We fuse every tile of channel 0 onto the grid — one big source replacing the tile mosaic.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(SourcesFuseAndResampleCommand.class, 2));
        CommandExecutor.of(SourcesFuseAndResampleCommand.class)
                .arg("sources", datasetName + ">Channel>ch_0",
                        "Picking channel 0 of the tiled dataset as the input to fuse.")
                .arg("model", "Other Sources>grid_model",
                        "Use the grid_model we just defined as the resampling target.")
                .arg("reusemipmaps", false,
                        "Don't reuse source mipmaps — always sample from the chosen level.")
                .arg("defaultmipmaplevel", 0,
                        "Sample from the highest-resolution level of each tile.")
                .arg("interpolate", false,
                        "No interpolation — voxel-precise sampling.")
                .arg("cache", true,
                        "Cache the fused output so re-visited regions don't recompute.")
                .arg("cache_x", 512,
                        "512×512×1 cache blocks — matches typical tile-overlap regions.")
                .arg("cache_y", 512)
                .arg("cache_z", 1)
                .arg("cache_bounds", -1,
                        "Unbounded cache — the LRU eviction is enough.")
                .arg("n_threads", 12,
                        "12 threads to fuse in parallel.")
                .arg("name", "fused_ch0",
                        "Output source name — lands under Other Sources.")
                .arg("blending_mode", "AVERAGE",
                        "AVERAGE blending to smooth tile-overlap seams.")
                .sayOnDialog("Walking through the fuse-and-resample dialog — the parameters that shape the fused output.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Fuse Channel 1 Onto The Grid",
                "Same fuse pass for channel 1 — a second fused source on the same grid.");
        CommandExecutor.of(SourcesFuseAndResampleCommand.class)
                .arg("sources", datasetName + ">Channel>ch_1",
                        "Picking channel 1 this time — same dataset, second channel.")
                .arg("model", "Other Sources>grid_model",
                        "Same grid_model target — the two fused channels stay aligned.")
                .arg("reusemipmaps", false)
                .arg("defaultmipmaplevel", 0)
                .arg("interpolate", false)
                .arg("cache", true)
                .arg("cache_x", 512)
                .arg("cache_y", 512)
                .arg("cache_z", 1)
                .arg("cache_bounds", -1)
                .arg("n_threads", 12)
                .arg("name", "fused_ch1",
                        "Output source name — sits beside fused_ch0 under Other Sources.")
                .arg("blending_mode", "AVERAGE",
                        "Same AVERAGE blending — consistent treatment across channels.")
                .sayOnDialog("Same dialog, but ch_1 as input.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Show Fused Sources",
                "The fused channels land under Other Sources. We Ctrl+click both, then "
                        + "open them together in a second BDV next to the Raw Tiles window.");
        // Shrink Raw Tiles to the left column to make room for the fused window.
        bdvArea.place(bdvTiles, 1, 3, 0, 0);
        Ui.rawPause(1500);

        Step.say("Click fused_ch0, Ctrl+click fused_ch1, then walk: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast each channel independently.")
                .arg("adjust_view", true,
                        "Re-centering on the fused mosaic.")
                .arg("make_new_window", true,
                        "Fresh BDV dedicated to the fused output, separate from the Raw Tiles viewer.")
                .arg("interpolate", false,
                        "No interpolation.")
                .sayOnDialog("Walking through the show-sources dialog for both fused channels.")
                .viaTreePaths(new String[]{"Other Sources>fused_ch0", "Other Sources>fused_ch1"},
                        "We pick both fused channels — Ctrl+click adds ch1 to the ch0 selection.")
                .skipInScript()
                .execute();

        Frame bdvFused = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        // Fused spans the remaining two columns while no pyramidized window exists yet.
        bdvArea.place(bdvFused, 1, 3, 0, 1, 1, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Fused",
                        "Naming the new viewer so the later sync command can pair it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("Pyramidize Both Fused Channels",
                "We wrap both fused channels in a lazy multi-resolution pyramid — same data, "
                        + "but with downsampled levels for smooth zoom-out. One command call, "
                        + "two pyramid outputs.");
        Step.say("Click fused_ch0, Ctrl+click fused_ch1, then walk: "
                + CommandExecutor.menuPath(SourcesPyramidizeCommand.class, 2));
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .sayOnDialog("Walking through the pyramidize dialog — the fused sources have only one "
                        + "resolution level, so zooming out pulls full-res tiles. The pyramid wrapper "
                        + "adds downsampled levels lazily, one pyramid per input source.")
                .viaTreePaths(new String[]{"Other Sources>fused_ch0", "Other Sources>fused_ch1"},
                        "We pick both fused channels — each gets its own pyramid wrapper.")
                .execute();
        Step.end();

        Step.begin("Show Pyramidized Sources",
                "Each pyramidized source lands under Other Sources as <name>_pyramid. We "
                        + "Ctrl+click both and open them together in a third BDV — zooming out "
                        + "now reads smoothly thanks to the pyramid levels.");
        // Shrink the fused window to its own column so the pyramidized one can take col 2.
        bdvArea.place(bdvFused, 1, 3, 0, 1);
        Ui.rawPause(500);

        Step.say("Click fused_ch0_pyramid, Ctrl+click fused_ch1_pyramid, then walk: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", false,
                        "No auto contrast: carry over display values from the original sources.")
                .arg("adjust_view", true,
                        "Re-centering on the pyramidized mosaic.")
                .arg("make_new_window", true,
                        "Third BDV so we can watch the pyramid path against the raw and the flat-fused views.")
                .arg("interpolate", false)
                .sayOnDialog("Walking through the show-sources dialog for both pyramidized channels.")
                .viaTreePaths(new String[]{
                                "Other Sources>fused_ch0_pyramid",
                                "Other Sources>fused_ch1_pyramid"},
                        "We pick both pyramidized channels at once.")
                .skipInScript()
                .execute();

        Frame bdvPyr = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvPyr, 1, 3, 0, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Pyramidized",
                        "Naming this third viewer so the sync command can pair all three by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("Synchronize All Three Views",
                "Finally, ViewSynchronizeCommand on all three windows — navigation in any "
                        + "one mirrors instantly in the others, and the pyramid keeps the "
                        + "overview crisp at any zoom.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class, 2));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .arg("bdvhs", new String[]{"BDV - Raw Tiles", "BDV - Fused", "BDV - Pyramidized"},
                        "Selecting all three BDV windows we just named — the viewers that will share state.")
                .arg("bvvhs", null)
                .arg("synchronizetime", false,
                        "No timepoint sync needed — this dataset is a single volume.")
                .sayOnDialog("Walking through the synchronize dialog.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.say("Holding the final frame for ~15 s while every viewer streams its current view — "
                + "zoom out on any window and the pyramid's downsampled levels keep the overview crisp.");

        // Stash the sync window beside the Tasks panel so it can be closed in
        // the export step — closing it then reveals the Tasks panel underneath
        // for live progress.
        Frame syncWindow = Ui.waitForFrame("Close window to stop synchronization", 6000);
        if (syncWindow != null) {
            Ui.resizeFrame(syncWindow, 450, 100);
            Ui.dragFrame(syncWindow, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);
        }

        // Hold long enough for all three views to fill in at the synced view.
        Ui.rawPause(15000);
        Step.end();

        Step.begin("Inspect The Pyramidized Source",
                "Before exporting, we inspect the pyramidized source — the tree reveals its "
                        + "full provenance: a pyramid wrapper over the fused source, which itself "
                        + "is built from all 84 raw tiles.");
        Step.say("Right-clicking the pyramidized source — walking to: "
                + CommandExecutor.menuPath(SourceInspectCommand.class, 2));
        CommandExecutor.of(SourceInspectCommand.class)
                .sayOnDialog("Walking through the inspect dialog.")
                .viaTreePath("Other Sources>fused_ch0_pyramid",
                        "We pick the pyramidized fused channel 0.")
                .skipInScript()
                .execute();
        Step.say("The hierarchy reads pyramid → fused → 84 tiles — the full provenance is recoverable from the source itself.");
        Step.waitForUser("Expand the new \"Inspect Results [fused_ch0_pyramid]\" node and "
                + "highlight the hierarchy lines on camera, then click Continue.");
        Step.end();

        Step.begin("Save One Fused Channel To OME-TIFF",
                "We export one fused channel as a multi-resolution OME-TIFF — "
                        + "4 pyramid levels with a 2× downscale between each, "
                        + "written straight to the desktop. To keep the demo short, "
                        + "we only export every fifth slice (0:5:-1).");
        // Close the sync window so the Tasks panel placed underneath becomes
        // visible — we want the export progress in frame while it runs.
        if (syncWindow != null) {
            Ui.closeWithMouse(syncWindow);
            Ui.rawPause(500);
        }
        Step.say("Right-clicking the \"Other Sources\" branch — walking to: "
                + CommandExecutor.menuPath(KheopsExportSourcesCommand.class, 2));
        CommandExecutor.of(KheopsExportSourcesCommand.class)
                .arg("range_channels", "",
                        "Single-source selection — no channel sub-range needed.")
                // TODO: check whether the voxel size is correctly carried over
                //  when range_slices selects a subset like 0:5:-1 — gut feeling
                //  is that the Z calibration of the exported OME-TIFF will NOT
                //  be multiplied by 5 to compensate, which would make the
                //  written file under-report its true Z extent.
                .arg("range_slices", "0:5:-1",
                        "Every fifth slice (start:step:end, -1 = last) — keeps the demo short.")
                .arg("range_frames", "",
                        "Single timepoint — nothing to sub-range.")
                .arg("file", exportFile,
                        "Saving straight to the desktop.")
                .arg("unit", "MICROMETER",
                        "Coordinates in micrometers.")
                .arg("override_voxel_size", false,
                        "No need to override the voxel sizes — they carry over from the fused source.")
                .arg("n_resolution_levels", 4,
                        "Four resolution levels in the output pyramid.")
                .arg("downscaling", 2,
                        "Each level is 2× smaller than the previous one.")
                .arg("tile_size_x", 512,
                        "Pretty standard block saving size: 512 × 512 pixels.")
                .arg("tile_size_y", 512)
                .arg("n_threads", 15,
                        "15 fetcher threads (= #core in this computer -1).")
                .arg("compression", "Uncompressed",
                        "Uncompressed.")
                .sayOnDialog("Walking through the OME-TIFF export dialog.")
                .viaTreePath("Other Sources>fused_ch0_pyramid",
                        "We pick one pyramidized fused channel as the export source.")
                .execute();
        Step.say("Each block of the fused channel is computed on demand and streamed straight to disk — "
                + "the Tasks panel reports progress.");

        // TREE-mode .execute() returns once OK is clicked; the export then
        // runs on the SciJava worker thread. Poll the output file until its
        // size has been stable for a few seconds (or hit the safety timeout)
        // so the export progress runs to completion on video before we end
        // the clip.
        long stableSince = -1;
        long lastSize = -1;
        long deadline = System.currentTimeMillis() + 10 * 60_000_000L;
        while (System.currentTimeMillis() < deadline) {
            long size = exportFile.exists() ? exportFile.length() : 0L;
            if (size > 0 && size == lastSize) {
                if (stableSince < 0) stableSince = System.currentTimeMillis();
                if (System.currentTimeMillis() - stableSince > 5000) break;
            } else {
                stableSince = -1;
            }
            lastSize = size;
            Ui.rawPause(10000);
        }
        Step.say("Done — the OME-TIFF is ready on the desktop.");
        Ui.rawPause(15000);
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked, so it
        // lands in timeline.json's outro block and in youtube-description.md.
        Demo.outro("You've seen tiled stitching end to end — from raw Bio-Formats tiles, through "
                        + "per-tile and global transforms, a resampling grid, fused channels, a lazy "
                        + "pyramid wrapper, three synchronized BDV viewers and a multi-resolution OME-TIFF "
                        + "written straight to the desktop.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "Sample brain-section tiles: https://zenodo.org/records/19062791");

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}