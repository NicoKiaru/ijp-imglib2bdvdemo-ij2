package ch.epfl.biop.docs;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.SourcesPyramidizeCommand;
import ch.epfl.biop.command.process.resample.SourcesFuseAndResampleCommand;
import ch.epfl.biop.command.process.resample.SourcesGridModelMakeCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.Demo;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.process.transform.SourceSimpleTransformCommand;

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
 *   <li>apply Rot270 around Z to each tile so they read upright;</li>
 *   <li>show every tile in BDV — "BDV - Raw Tiles";</li>
 *   <li>apply Rot90 globally so the brain section sits in its anatomical orientation;</li>
 *   <li>re-frame the active BDV onto the mosaic;</li>
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

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        Demo.intro("Tile Fusion And Resampling in Fiji",
                "Stitch a tiled brain-section .lif, fuse each channel onto a unified grid, "
                        + "and watch raw / fused / pyramidized views stay in sync — all without "
                        + "writing the fused mosaic to disk.",
                "Lazy fusion — fused tiles are computed only when viewed",
                "Pyramid wrapper adds downsampled levels for smooth zoom-out",
                "Three BDV viewers — raw, flat-fused, pyramidized — synchronized side by side");

        File fileDataset = ch.epfl.biop.DatasetHelper.getDataset(
                "https://zenodo.org/records/19062791/files/BrainSection-Tiles.lif");

        // Single placement area for every BDV window in this demo. Each phase
        // re-partitions it into a different grid: 1×1 (Raw Tiles full area)
        // → 1×3 with Fused spanning cols 1-2 → 1×3 with Pyramidized at col 2.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y + 150, 900, 600);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay(false);

        Step.begin("Open Brain Tile Dataset",
                "We load the tiled Dentate Gyrus brain-section .lif through Bio-Formats — "
                        + "two channels, many tiles, lazily backed by the file.");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", fileDataset.getName(),
                        "We name the dataset after the file itself — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "Browsing to the tiled brain-section .lif.")
                .arg("split_rgb_channels", false,
                        "RGB is irrelevant for this dataset.")
                .arg("auto_pyramidize", true,
                        "Auto-pyramidize so tile previews stay smooth at low zoom.")
                .arg("disable_memo", false,
                        "We keep memoization — fast file opening on subsequent runs.")
                .arg("plane_origin_convention", "TOP LEFT",
                        "Top-left origin convention — matters here because the .lif has many tiles "
                                + "that need to land on the same mosaic frame.")
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
                .arg("adjust_view", true,
                        "Re-centering the view on the loaded tiles.")
                .arg("auto_contrast", true,
                        "Auto-contrast so the mosaic reads at a glance.")
                .arg("interpolate", false,
                        "No interpolation — voxel-precise tile rendering, so tile borders stay visible.")
                .arg("make_new_window", true,
                        "Open a fresh BDV window — leave any existing viewers untouched.")
                .sayOnDialog("Walking through the show-sources dialog.")
                .viaTreePath(fileDataset.getName(),
                        "We select the raw tiled dataset for display.")
                .execute();

        Frame bdvTiles = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvTiles, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Raw Tiles",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
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
                .viaTreePath(fileDataset.getName(),
                        "We pick the tiled dataset whose tiles we want to flip.")
                .execute();
        Step.end();


        Step.begin("Rotate The Whole Mosaic",
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
        Step.end();

        Step.begin("Reframe View On The Mosaic",
                "The rotation moved the tiles out of the current viewport — "
                        + "double-clicking the dataset in the BDV Sources tree re-frames "
                        + "the active BDV onto every source beneath it.");
        Step.say("Double-clicking the dataset in the BDV Sources tree — BDV computes the bounding "
                + "box of every source beneath the node and zooms the view to fit.");
        BdvPlayground.adjustViewOnSource(fileDataset.getName());
        Step.end();


        Step.begin("Define A Resampling Grid",
                "We define a resampling grid spanning every tile at 0.25 µm in XY and 0.4 µm in Z — "
                        + "the model source the fused output will sample onto.");
        Step.say("Right-clicking the dataset — walking to: "
                + CommandExecutor.menuPath(SourcesGridModelMakeCommand.class, 2));
        CommandExecutor.of(SourcesGridModelMakeCommand.class)
                .arg("vox_size_x", 0.25,
                        "0.25 µm in X — slightly oversampled relative to the raw tiles, "
                                + "so the fuse step doesn't lose detail at tile borders.")
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
                .viaTreePath(fileDataset.getName(),
                        "We pick the tiled dataset — the grid is sized to span every tile in it.")
                .execute();
        Step.end();

        Step.begin("Fuse Channel 0 Onto The Grid",
                "We fuse every tile of channel 0 onto the grid — one big source replacing the tile mosaic.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(SourcesFuseAndResampleCommand.class, 2));
        CommandExecutor.of(SourcesFuseAndResampleCommand.class)
                .arg("sources", fileDataset.getName() + ">Channel>ch_0",
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
                .arg("n_threads", 4,
                        "Four threads to fuse in parallel.")
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
                .arg("sources", fileDataset.getName() + ">Channel>ch_1",
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
                .arg("n_threads", 4)
                .arg("name", "fused_ch1",
                        "Output source name — sits beside fused_ch0 under Other Sources.")
                .arg("blending_mode", "AVERAGE",
                        "Same AVERAGE blending — consistent treatment across channels.")
                .sayOnDialog("Same dialog, but ch_1 as input.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Show Fused Sources",
                "The fused channels land under Other Sources. We open them in a second BDV "
                        + "next to the Raw Tiles window — first ch0 creates the window, then ch1 "
                        + "is added to it.");
        // Shrink Raw Tiles to the left column to make room for the fused window.
        bdvArea.place(bdvTiles, 1, 3, 0, 0);
        Ui.rawPause(1500);

        Step.say("Right-clicking fused_ch0 — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("adjust_view", true,
                        "Re-centering on the fused mosaic.")
                .arg("auto_contrast", true,
                        "Auto-contrast for ch0 in this fresh viewer.")
                .arg("interpolate", true,
                        "Linear interpolation — the fused source is one continuous mosaic now, "
                                + "smoother reads better than voxel-edge crunch.")
                .arg("make_new_window", true,
                        "Fresh BDV dedicated to the fused output, separate from the Raw Tiles viewer.")
                .sayOnDialog("Walking through the show-sources dialog for the fused channel.")
                .viaTreePath("Other Sources>fused_ch0",
                        "We pick the fused channel-0 source that just landed under Other Sources.")
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
                .execute();

        // Add ch1 to the same (now active) BDV.
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("adjust_view", false,
                        "Don't move the view — we want ch1 to overlay on the existing ch0 mosaic.")
                .arg("auto_contrast", true,
                        "Auto-contrast for ch1 independently.")
                .arg("interpolate", true,
                        "Same linear interpolation as ch0 for a consistent look.")
                .arg("make_new_window", false,
                        "Reuse the active viewer — both fused channels overlay in the Fused window.")
                .sayOnDialog("Adding fused_ch1 to the same (now active) BDV.")
                .viaTreePath("Other Sources>fused_ch1",
                        "We pick the fused channel-1 source — it will overlay on top of ch0.")
                .execute();

        Ui.rawPause(2000);
        Step.end();

        Step.begin("Pyramidize Fused Channel 0",
                "We wrap fused channel 0 in a lazy multi-resolution pyramid — same data, "
                        + "but with downsampled levels for smooth zoom-out.");
        Step.say("Right-clicking fused_ch0 — walking to: "
                + CommandExecutor.menuPath(SourcesPyramidizeCommand.class, 2));
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .sayOnDialog("Walking through the pyramidize dialog — the fused source has only one "
                        + "resolution level, so zooming out pulls full-res tiles. The pyramid wrapper "
                        + "adds downsampled levels lazily.")
                .viaTreePath("Other Sources>fused_ch0",
                        "We pick the fused channel-0 source as the input to pyramidize.")
                .execute();
        Step.end();

        Step.begin("Pyramidize Fused Channel 1",
                "Same pyramidize pass for fused channel 1.");
        CommandExecutor.of(SourcesPyramidizeCommand.class)
                .sayOnDialog("Same pyramidize pass on fused_ch1 — a matching pyramid lands as fused_ch1_pyramid.")
                .viaTreePath("Other Sources>fused_ch1",
                        "Same command, second channel.")
                .execute();
        Step.end();

        Step.begin("Show Pyramidized Sources",
                "Each pyramidized source lands under Other Sources as <name>_pyramid. We open them "
                        + "in a third BDV — zooming out now reads smoothly thanks to the pyramid levels.");
        // Shrink the fused window to its own column so the pyramidized one can take col 2.
        bdvArea.place(bdvFused, 1, 3, 0, 1);
        Ui.rawPause(500);

        Step.say("Right-clicking fused_ch0_pyramid — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("adjust_view", true,
                        "Re-centering on the pyramidized mosaic.")
                .arg("auto_contrast", true,
                        "Auto-contrast for ch0_pyramid in this fresh viewer.")
                .arg("interpolate", true,
                        "Linear interpolation — same look as the Fused viewer for a fair comparison.")
                .arg("make_new_window", true,
                        "Third BDV so we can watch the pyramid path against the raw and the flat-fused views.")
                .sayOnDialog("Walking through the show-sources dialog for the pyramidized channel.")
                .viaTreePath("Other Sources>fused_ch0_pyramid",
                        "We pick the pyramidized channel-0 source.")
                .execute();

        Frame bdvPyr = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvPyr, 1, 3, 0, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Pyramidized",
                        "Naming this third viewer so the sync command can pair all three by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .execute();

        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("adjust_view", false,
                        "Don't move the view — ch1_pyramid overlays the existing ch0_pyramid.")
                .arg("auto_contrast", true,
                        "Auto-contrast for ch1_pyramid independently.")
                .arg("interpolate", true,
                        "Same linear interpolation as ch0_pyramid.")
                .arg("make_new_window", false,
                        "Reuse the Pyramidized window so both pyramid channels overlay in it.")
                .sayOnDialog("Adding fused_ch1_pyramid into the same viewer.")
                .viaTreePath("Other Sources>fused_ch1_pyramid",
                        "We pick the pyramidized channel-1 source — overlays on ch0_pyramid.")
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
                .execute();
        Step.say("Holding the final frame for ~15 s while every viewer streams its current view — "
                + "zoom out on any window and the pyramid's downsampled levels keep the overview crisp.");
        // Hold long enough for all three views to fill in at the synced view.
        Ui.rawPause(15000);
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked, so it
        // lands in timeline.json's outro block and in youtube-description.md.
        Demo.outro("You've seen tiled stitching end to end — from raw Bio-Formats tiles, through "
                        + "per-tile and global transforms, a resampling grid, fused channels and a lazy "
                        + "pyramid wrapper, into three synchronized BDV viewers — without ever materializing "
                        + "the fused mosaic to disk.",
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