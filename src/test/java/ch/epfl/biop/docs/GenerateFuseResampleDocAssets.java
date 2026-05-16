package ch.epfl.biop.docs;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.SourcesPyramidizeCommand;
import ch.epfl.biop.command.process.resample.SourcesFuseAndResampleCommand;
import ch.epfl.biop.command.process.resample.SourcesGridModelMakeCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
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
        // plus a single script.md transcript.
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

        Step.begin("boot-fiji",
                "We start Fiji and place its main window on the secondary monitor.");
        Fiji.startFiji();
        Step.end();

        Step.begin("open-bdv-tree",
                "From Fiji's search bar we open the BDV Playground Sources tree, "
                        + "where every loaded source will land.");
        CommandExecutor.of(SourceServiceShowCommand.class)
                .viaSearch("show bdv play")
                .execute();
        Frame bdvSources = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvSources, 450, 600);
        Ui.dragFrame(bdvSources, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y + 150);
        Step.end();

        Step.begin("load-tiles",
                "We load the tiled Dentate Gyrus brain-section .lif through Bio-Formats — "
                        + "two channels, many tiles, lazily backed by the file.");
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .args("datasetname", fileDataset.getName(),
                        "unit", "MICROMETER",
                        "files", new File[]{fileDataset},
                        "split_rgb_channels", false,
                        "auto_pyramidize", true,
                        "disable_memo", false,
                        "plane_origin_convention", "TOP LEFT")
                .viaTreePath()
                .execute();
        Ui.rawPause(3000);
        Step.end();


        Step.begin("show-tiles-bdv",
                "Right-clicking the dataset shows every tile in a new BigDataViewer window — "
                        + "we'll call this one Raw Tiles and place it next to the tree.");
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", false,
                        "make_new_window", true)
                .viaTreePath(fileDataset.getName())
                .execute();

        Frame bdvTiles = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvTiles, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Raw Tiles")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("flip-tiles",
                "The tiles come out mirrored from Bio-Formats — Rot270 around Z brings them "
                        + "into the orientation the documentation expects.");
        CommandExecutor.of(SourceSimpleTransformCommand.class)
                .args("type", "Rot270",
                        "axis", "Z",
                        "ini_timepoint", 0,
                        "n_timepoints", 1,
                        "global_change", false)
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
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.end();

        Step.begin("reframe-on-tiles",
                "The rotation moved the tiles out of the current viewport — "
                        + "double-clicking the dataset in the BDV Sources tree re-frames "
                        + "the active BDV onto every source beneath it.");
        BdvPlayground.adjustViewOnSource(fileDataset.getName());
        Step.end();


        Step.begin("define-grid",
                "We define a resampling grid spanning every tile at 0.25 µm in XY and 0.4 µm in Z — "
                        + "the model source the fused output will sample onto.");
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
                .viaTreePath(fileDataset.getName())
                .execute();
        Step.end();

        Step.begin("fuse-channel-0",
                "We fuse every tile of channel 0 onto the grid — one big source replacing the tile mosaic.");
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

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", true)
                .viaTreePath("Other Sources>fused_ch0")
                .execute();

        Frame bdvFused = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        // Fused spans the remaining two columns while no pyramidized window exists yet.
        bdvArea.place(bdvFused, 1, 3, 0, 1, 1, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Fused")
                .viaTreePath()
                .execute();

        // Add ch1 to the same (now active) BDV.
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", false,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", false)
                .viaTreePath("Other Sources>fused_ch1")
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("pyramidize-ch0",
                "We wrap fused channel 0 in a lazy multi-resolution pyramid — same data, "
                        + "but with downsampled levels for smooth zoom-out.");
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .viaTreePath("Other Sources>fused_ch0")
                .execute();
        Step.end();

        Step.begin("pyramidize-ch1",
                "Same pyramidize pass for fused channel 1.");
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .viaTreePath("Other Sources>fused_ch1")
                .execute();
        Step.end();

        Step.begin("show-pyramidized-bdv",
                "Each pyramidized source lands under Other Sources as <name>_pyramid. We open them "
                        + "in a third BDV — zooming out now reads smoothly thanks to the pyramid levels.");
        // Shrink the fused window to its own column so the pyramidized one can take col 2.
        bdvArea.place(bdvFused, 1, 3, 0, 1);
        Ui.rawPause(500);

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", true,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", true)
                .viaTreePath("Other Sources>fused_ch0_pyramid")
                .execute();

        Frame bdvPyr = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvPyr, 1, 3, 0, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .args("title", "BDV - Pyramidized")
                .viaTreePath()
                .execute();

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .args("adjust_view", false,
                        "auto_contrast", true,
                        "interpolate", true,
                        "make_new_window", false)
                .viaTreePath("Other Sources>fused_ch1_pyramid")
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("sync-all-views",
                "Finally, ViewSynchronizeCommand on all three windows — navigation in any "
                        + "one mirrors instantly in the others, and the pyramid keeps the "
                        + "overview crisp at any zoom.");
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .args("bdvhs", new String[]{"BDV - Raw Tiles", "BDV - Fused", "BDV - Pyramidized"},
                        "bvvhs", null,
                        "synchronizetime", false)
                .viaTreePath()
                .execute();
        // Hold long enough for all three views to fill in at the synced view.
        Ui.rawPause(15000);
        Step.end();
    }
}
