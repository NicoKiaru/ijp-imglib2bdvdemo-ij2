package ch.epfl.biop.docs;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.SourcesPyramidizeCommand;
import ch.epfl.biop.command.process.resample.SourcesFuseAndResampleCommand;
import ch.epfl.biop.command.process.resample.SourcesGridModelMakeCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.process.transform.SourceSimpleTransformCommand;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;

import java.awt.Frame;
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
 *   <li>boot Fiji and open the BDV Playground tree;</li>
 *   <li>load the tiled .lif via Bio-Formats;</li>
 *   <li>apply Rot270 around Z so the tiles read upright;</li>
 *   <li>show every tile in BDV — "BDV - Raw Tiles";</li>
 *   <li>define a resampling grid spanning all tiles;</li>
 *   <li>fuse channel 0 onto the grid;</li>
 *   <li>fuse channel 1 onto the grid;</li>
 *   <li>show both fused channels in a second BDV — "BDV - Fused";</li>
 *   <li>pyramidize the two fused sources;</li>
 *   <li>show the pyramidized sources in a third BDV — "BDV - Pyramidized";</li>
 *   <li>synchronise all three BDV views.</li>
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
        CommandExecutor.FORCE_PROGRAMMATIC = true;
        Ui.FAST_MODE = true;

        // Asset capture toggles. Flip these to false while iterating on the
        // demo; flip them back on for the recording pass. Output lands in
        // target/video-assets/FuseResampleVideoDemo/ as NNN-step.{mp4,png}
        // plus a single timeline.json transcript.
        Assets.session("FuseResampleVideoDemo");
        ScreenRecorder.ENABLED = false;
        Screenshotter.ENABLED = false;

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/19062791/files/BrainSection-Tiles.lif");

        // Single placement area for every BDV window in this demo. Each phase
        // re-partitions it into a different grid: 1×1 (Raw Tiles full area)
        // → 1×3 with Fused spanning cols 1-2 → 1×3 with Pyramidized at col 2.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150, 900, 600);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay(false);

        Step.begin("load-tiles",
                "We load the tiled Dentate Gyrus brain-section .lif through Bio-Formats — "
                        + "two channels, many tiles, lazily backed by the file.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", fileDataset.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{fileDataset},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .sayOnDialog("Filling the importer dialog: micrometre units, pyramid generation on. "
                        + "The .lif contains many tiles in two channels — each tile becomes its own source.")
                .viaTreePath()
                .execute();
        Step.say("The dataset opens lazily — every tile shows up under the dataset node, "
                + "grouped by channel.");
        Ui.rawPause(3000);
        Step.end();


        Step.begin("show-tiles-bdv",
                "Right-clicking the dataset shows every tile in a new BigDataViewer window — "
                        + "we'll call this one Raw Tiles and place it next to the tree.");
        Step.say("Right-clicking the dataset node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", false,
                        "make_new_window", true)
                .sayOnDialog("Dialog: every tile from both channels in one viewer with auto-contrast "
                        + "on so the mosaic reads at a glance, make_new_window=true for a fresh viewer.")
                .viaTreePath(fileDataset.getName())
                .execute();

        Frame bdvTiles = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvTiles, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Raw Tiles")
                .sayOnDialog("Renaming the window to \"BDV - Raw Tiles\" so the upcoming sync "
                        + "command can address it by title.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("flip-tiles",
                "The tiles come out mirrored from Bio-Formats — Rot270 around Z brings them "
                        + "into the orientation the documentation expects.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourceSimpleTransformCommand.class));
        CommandExecutor.of(SourceSimpleTransformCommand.class)
                .args("type", "Rot270",
                        "axis", "Z",
                        "ini_timepoint", 0,
                        "n_timepoints", 1,
                        "global_change", false)
                .sayOnDialog("Dialog: Rot270 around Z with global_change=false — each tile rotates "
                        + "in place, preserving the mosaic layout.")
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.end();


        Step.begin("rotate-all-tiles",
                "Rotate all tiles in order to get a more natural brain geometry");
        CommandExecutor.of(SourceSimpleTransformCommand.class)
                .args("type", "Rot90",
                        "axis", "Z",
                        "ini_timepoint", 0,
                        "n_timepoints", 1,
                        "global_change", true)
                .sayOnDialog("Same transform command, but Rot90 with global_change=true — the "
                        + "mosaic as a whole rotates so the brain section reads in its anatomical orientation.")
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.end();

        Step.begin("reframe-on-tiles",
                "The rotation moved the tiles out of the current viewport — "
                        + "double-clicking the dataset in the BDV Sources tree re-frames "
                        + "the active BDV onto every source beneath it.");
        Step.say("Double-clicking the dataset in the BDV Sources tree — BDV computes the bounding "
                + "box of every source beneath the node and zooms the view to fit.");
        BdvPlayground.adjustViewOnSource(fileDataset.getName());
        Step.end();


        Step.begin("define-grid",
                "We define a resampling grid spanning every tile at 0.25 µm in XY and 0.4 µm in Z — "
                        + "the model source the fused output will sample onto.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourcesGridModelMakeCommand.class));
        CommandExecutor.of(SourcesGridModelMakeCommand.class)
                .args("vox_size_x", 0.25,
                        "vox_size_y", 0.25,
                        "vox_size_z", 0.4,
                        "timepoint", 0,
                        "n_timepoints", 1,
                        "n_resolution_levels", 1,
                        "downscale_x", 2,
                        "downscale_y", 2,
                        "downscale_z", 2,
                        "name", "grid_model")
                .sayOnDialog("Dialog: 0.25 µm in XY and 0.4 µm in Z — slightly oversampled relative "
                        + "to the raw tiles, so the fuse step doesn't lose detail at tile borders. "
                        + "Lands as a \"grid_model\" empty source under Other Sources.")
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.end();

        Step.begin("fuse-channel-0",
                "We fuse every tile of channel 0 onto the grid — one big source replacing the tile mosaic.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(SourcesFuseAndResampleCommand.class));
        CommandExecutor.of(SourcesFuseAndResampleCommand.class)
                .args("sources", fileDataset.getName() + ">Channel>ch_0",
                        "model", "Other Sources>grid_model",
                        "reusemipmaps", false,
                        "defaultmipmaplevel", 0,
                        "interpolate", false,
                        "cache", true,
                        "cache_x", 512,
                        "cache_y", 512,
                        "cache_z", 1,
                        "cache_bounds", -1,
                        "n_threads", 4,
                        "name", "fused_ch0",
                        "blending_mode", "AVERAGE")
                .sayOnDialog("Dialog: ch_0 as input, grid_model as the resampling target, AVERAGE "
                        + "blending to smooth tile-overlap seams. Output is cached and computed lazily on demand.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("fuse-channel-1",
                "Same fuse pass for channel 1 — a second fused source on the same grid.");
        CommandExecutor.of(SourcesFuseAndResampleCommand.class)
                .args("sources", fileDataset.getName() + ">Channel>ch_1",
                        "model", "Other Sources>grid_model",
                        "reusemipmaps", false,
                        "defaultmipmaplevel", 0,
                        "interpolate", false,
                        "cache", true,
                        "cache_x", 512,
                        "cache_y", 512,
                        "cache_z", 1,
                        "cache_bounds", -1,
                        "n_threads", 4,
                        "name", "fused_ch1",
                        "blending_mode", "AVERAGE")
                .sayOnDialog("Same dialog, but ch_1 as input — two fused sources now live "
                        + "alongside the raw tiles in the source tree.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("show-fused-bdv",
                "The fused channels land under Other Sources. We open them in a second BDV "
                        + "next to the Raw Tiles window — first ch0 creates the window, then ch1 "
                        + "is added to it.");
        // Shrink Raw Tiles to the left column to make room for the fused window.
        bdvArea.place(bdvTiles, 1, 3, 0, 0);
        Ui.rawPause(1500);

        Step.say("Right-clicking fused_ch0 — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", true)
                .sayOnDialog("Dialog: make_new_window=true gives us a fresh BDV dedicated to the "
                        + "fused output, separate from the Raw Tiles viewer.")
                .viaTreePath("Other Sources>fused_ch0")
                .execute();

        Frame bdvFused = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        // Fused spans the remaining two columns while no pyramidized window exists yet.
        bdvArea.place(bdvFused, 1, 3, 0, 1, 1, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Fused")
                .sayOnDialog("Renaming the new viewer \"BDV - Fused\" so the later sync command "
                        + "can address it by title.")
                .viaTreePath()
                .execute();

        // Add ch1 to the same (now active) BDV.
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", false,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", false)
                .sayOnDialog("Adding fused_ch1 to the same (now active) BDV — make_new_window=false "
                        + "reuses the active viewer, so both fused channels overlay in the Fused window.")
                .viaTreePath("Other Sources>fused_ch1")
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("pyramidize-ch0",
                "We wrap fused channel 0 in a lazy multi-resolution pyramid — same data, "
                        + "but with downsampled levels for smooth zoom-out.");
        Step.say("Right-clicking fused_ch0 — walking to: "
                + CommandExecutor.menuPath(SourcesPyramidizeCommand.class));
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .sayOnDialog("Dialog: the fused source has only one resolution level, so zooming "
                        + "out pulls full-res tiles. The pyramid wrapper adds downsampled levels lazily.")
                .viaTreePath("Other Sources>fused_ch0")
                .execute();
        Step.end();

        Step.begin("pyramidize-ch1",
                "Same pyramidize pass for fused channel 1.");
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .sayOnDialog("Same pyramidize pass on fused_ch1 — a matching pyramid lands as fused_ch1_pyramid.")
                .viaTreePath("Other Sources>fused_ch1")
                .execute();
        Step.end();

        Step.begin("show-pyramidized-bdv",
                "Each pyramidized source lands under Other Sources as <name>_pyramid. We open them "
                        + "in a third BDV — zooming out now reads smoothly thanks to the pyramid levels.");
        // Shrink the fused window to its own column so the pyramidized one can take col 2.
        bdvArea.place(bdvFused, 1, 3, 0, 1);
        Ui.rawPause(500);

        Step.say("Right-clicking fused_ch0_pyramid — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", true)
                .sayOnDialog("Dialog: make_new_window=true gives us a third BDV so we can watch "
                        + "the pyramid path against the raw and the flat-fused views.")
                .viaTreePath("Other Sources>fused_ch0_pyramid")
                .execute();

        Frame bdvPyr = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvPyr, 1, 3, 0, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Pyramidized")
                .sayOnDialog("Renaming this third viewer \"BDV - Pyramidized\" so the sync command can find it.")
                .viaTreePath()
                .execute();

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", false,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", false)
                .sayOnDialog("Adding fused_ch1_pyramid into the same viewer — both pyramid channels "
                        + "overlay in the Pyramidized window, mirroring the layout of the Fused window.")
                .viaTreePath("Other Sources>fused_ch1_pyramid")
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("sync-all-views",
                "Finally, ViewSynchronizeCommand on all three windows — navigation in any "
                        + "one mirrors instantly in the others, and the pyramid keeps the "
                        + "overview crisp at any zoom.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .args("bdvhs", new String[]{"BDV - Raw Tiles", "BDV - Fused", "BDV - Pyramidized"},
                        "bvvhs", null,
                        "synchronizetime", false)
                .sayOnDialog("Dialog: all three BDV titles selected — pan and zoom now propagate "
                        + "to every viewer, so we compare raw tiles, flat fused and pyramid side by side at the same view.")
                .viaTreePath()
                .execute();
        Step.say("Holding the final frame for ~15 s while every viewer streams its current view — "
                + "zoom out on any window and the pyramid's downsampled levels keep the overview crisp.");
        // Hold long enough for all three views to fill in at the synced view.
        Ui.rawPause(15000);
        Step.end();

        // Notify that capture is done so the user knows it's safe to close
        // Fiji. The popup is shown after the last Step.end() — recorder is
        // already stopped, so the popup won't appear in the final clip.
        Step.waitForUser("Video acquisition complete — clips, screenshots and timeline.json "
                + "are written. Click Continue to dismiss.");
    }
}
