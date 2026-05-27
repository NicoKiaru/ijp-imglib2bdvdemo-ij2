package ch.epfl.biop.docs;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.docs.videos.CommandExecutor;
import ch.epfl.biop.docs.videos.Demo;
import ch.epfl.biop.docs.videos.GenerateDocAssetsCommon;
import ch.epfl.biop.docs.videos.bdv.Bdv;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.Layout;
import ch.epfl.biop.docs.videos.core.ScreenRecorder;
import ch.epfl.biop.docs.videos.core.Screenshotter;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.bdv.supplier.biop.BdvStyleBIOPSetCommand;
import sc.fiji.bdvpg.command.display.ViewSynchronizeCommand;
import sc.fiji.bdvpg.command.display.bdv.SingleBdvSourcesShowCommand;
import sc.fiji.bdvpg.command.display.bdv.settings.BdvTitleSetCommand;
import sc.fiji.bdvpg.command.process.resample.SourceResampleCommand;
import sc.fiji.bdvpg.service.SourceServices;

import java.awt.Frame;
import java.io.File;

/**
 * Generates a demo for source resampling. Should be used for video documentation.
 * <p>
 * Workflow:
 *   1. Set the default BDV supplier to the BIOP 2D style.
 *   2. Open the Slide_03.vsi brain-slides dataset via Bio-Formats.
 *   3. Double-click into the tree to reveal the dataset's structure.
 *   4. Show the low-resolution overview in a first BDV (top-left).
 *   5. Show the high-magnification 10x_01 fluorescence tile in a second BDV (top-right).
 *   6. Run SourceResampleCommand — resample the overview onto the 10x_01 voxel grid.
 *   7. Show the resampled source in a third BDV spanning the bottom row.
 *   8. Synchronize all three viewers — pan / zoom propagate live.
 */
public class GenerateSourceResamplingDocAssets {

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
        // target/video-assets/SourceResamplingVideoDemo/ as NNN-step.{mp4,png}
        // plus a single timeline.json transcript.
        Assets.session("SourceResamplingVideoDemo");
        ScreenRecorder.ENABLED = true;
        Screenshotter.ENABLED = true;

        // Intro / title-card metadata — the "why should I watch this" hook.
        // Lands in timeline.json's intro block and the youtube-description.md.
        Demo.intro("Resample a Source Onto Another's Voxel Grid",
                "Take a low-resolution whole-slide overview and resample it onto the voxel grid "
                        + "of a high-magnification fluorescence tile — entirely lazily, via BDV Playground.",
                "Lazy resampling — no eager pixel copy, blocks computed on demand",
                "Voxel-perfect alignment for downstream pipelines",
                "Side-by-side synchronized views: original, model and resampled");

        // Hardcoded path — corresponds to https://zenodo.org/records/6553641
        // (Slide_03.vsi). DemoDatasetHelper.BRAIN_SLICES uses the same file.
        File fileDataset = new File(
                "C:\\Users\\Nicolas\\CachedSamples\\records\\6553641\\files\\Slide_03.vsi");
        String datasetName = "Slide_03";
        String resampledName = "Overview_Resampled_Like_Fluo";

        GroovyScript.nameFile(fileDataset, "datasetFile", "Input dataset");

        // Single placement area for the three BDV windows. The first window
        // fills the area on its own, then we re-partition into a 2×2 grid —
        // overview top-left, model top-right, resampled spanning the bottom
        // row (rowSpan=1, colSpan=2) so the result sits flat under the inputs.
        Layout bdvArea = Layout.area(Ui.DEFAULT_FRAME_X + 450,
                Ui.DEFAULT_FRAME_Y, 900, 800);

        GenerateDocAssetsCommon.boot();
        GenerateDocAssetsCommon.showBdvTreeDisplay(true);

        Step.begin("Set BIOP BDV Style (2D)",
                "Before opening any viewers, we switch the default BDV supplier to the BIOP "
                        + "2D style — the brain-slides dataset is 2D, so the depth axis is disabled.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(BdvStyleBIOPSetCommand.class, 2));
        CommandExecutor.of(BdvStyleBIOPSetCommand.class)
                .arg("resetToDefault", false,
                        "Customising the supplier rather than resetting to defaults.")
                //.arg("width", 640)
                //.arg("height", 480)
                //.arg("screenscales", "1, 0.5, 0.25, 0.125")
                //.arg("numrenderingthreads", 3)
                //.arg("numsourcegroups", 10)
                //.arg("frametitle", "BigDataViewer")
                .arg("is2d", true,
                        "2D viewer — Slide_03 has no z dimension.")
                //.arg("interpolate", false)
                //.arg("numtimepoints", 1)
                //.arg("fontSize", 18)
                //.arg("font", "Courier")
                .sayOnDialog("Walking through the BDV BIOP style dialog.")
                .viaTreePath()
                .execute();
        Step.end();

        Step.begin("Open Brain-Slides Dataset",
                "We open Slide_03.vsi via Bio-Formats — a whole-slide image containing a "
                        + "low-resolution brightfield overview and several fluorescence regions acquired with a 10x objective.");
        Step.say("Right-clicking the Sources root opens the BDV Playground context menu — "
                + "we'll walk it to the Bio-Formats importer: "
                + CommandExecutor.menuPath(DatasetFromBioFormatsCreateCommand.class, 2));
        CommandExecutor.of(DatasetFromBioFormatsCreateCommand.class)
                .arg("datasetname", datasetName,
                        "Naming the dataset after the slide — keeps the source tree readable.")
                .arg("unit", "MICROMETER",
                        "Coordinates in microns.")
                .arg("files", new File[]{fileDataset},
                        "Browsing to the Slide_03.vsi file.")
                .arg("split_rgb_channels", false,
                        "We don't split RGB images into a 3-channel image.")
                .arg("auto_pyramidize", true,
                        "Auto-pyramidize on, even though the dataset is already pyramidal.")
                .arg("plane_origin_convention", "TOP LEFT",
                        "VSI uses the top-left origin convention (otherwise the overview will not match the fluo location.")
                .arg("disable_memo", false,
                        "We keep memoization — fast file opening on subsequent runs.")
                .sayOnDialog("Walking through the Bio-Formats importer dialog.")
                .viaTreePath()
                .execute();
        Ui.rawPause(2000);
        Step.say("The VSI opens lazily — every region appears under the dataset node.");
        Ui.rawPause(1000);
        Step.end();

        Step.begin("Reveal Dataset Structure",
                "We expand the Slide_03 node to see the children: a low-resolution "
                        + "brightfield overview and several 10x fluorescence tiles.");
        Step.say("Double-clicking the ImageName node — every child source becomes visible.");
        BdvPlayground.adjustViewOnSource("Slide_03>ImageName");
        Ui.rawPause(2000);
        Step.end();

        Step.begin("Show Overview Source",
                "We display the low-resolution brightfield overview in its first BDV window — "
                        + "this is the source we'll later resample. Title: 'BDV - Original Overview'.");
        Step.say("Double-clicking the overview node — pointing at the source we'll display.");
        BdvPlayground.adjustViewOnSource("Slide_03>ImageName>overview");
        Ui.rawPause(1000);

        Step.say("Right-clicking the overview node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast on so we start with a sensible display range.")
                .arg("adjust_view", true,
                        "Re-centering the view on the overview.")
                .arg("make_new_window", true,
                        "Open a fresh BDV window — leave any existing viewers untouched.")
                .arg("interpolate", false,
                        "No interpolation.")
                .sayOnDialog("Walking through the show-sources dialog.")
                .viaTreePath("Slide_03>ImageName>overview",
                        "We pick the brightfield overview.")
                .skipInScript()
                .execute();

        Frame bdvOverview = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        BdvHandle bdvOvHandle = SourceServices.getBdvDisplayService().getActiveBdv();
        Ui.pause(1500);
        bdvArea.place(bdvOverview, 1, 1, 0, 0);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Original Overview",
                        "Naming this window so the upcoming sync command can address it by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.end();

        Step.begin("Show Model Source",
                "We open the 10x_01 fluorescence tile in a second BDV window — its voxel "
                        + "grid will be the model for resampling. Title: 'BDV - 10x_01 Model'.");
        Bdv.setCardPanelExpanded(bdvOvHandle, false);
        bdvArea.place(bdvOverview, 2, 2, 0, 0);

        Step.say("Double-clicking the 10x_01 node — pointing at the model source.");
        BdvPlayground.adjustViewOnSource("Slide_03>ImageName>10x_01");

        Step.say("Right-clicking the 10x_01 node — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", true,
                        "Auto-contrast for the fluorescence channels.")
                .arg("adjust_view", true,
                        "Re-centering on the 10x_01 region.")
                .arg("make_new_window", true,
                        "New window so the overview viewer stays untouched next to it.")
                .arg("interpolate", false)
                .sayOnDialog("Walking through the show-sources dialog again.")
                .viaTreePath("Slide_03>ImageName>10x_01",
                        "We pick the 10x_01 fluorescence tile — the resampling model.")
                .skipInScript()
                .execute();

        Frame bdvModel = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        BdvHandle bdvModelHandle = SourceServices.getBdvDisplayService().getActiveBdv();
        Ui.pause(1500);
        Bdv.setCardPanelExpanded(bdvModelHandle, false);
        bdvArea.place(bdvModel, 2, 2, 0, 1);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - 10x_01 Model",
                        "Naming this one so the sync command can pair it with the others by title.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.end();

        Step.begin("Resample Overview Onto Model Grid",
                "Now the heart of the demo: we resample the overview onto the 10x_01 voxel grid. "
                        + "Same bounding box, same sampling, same orientation — but lazily backed by "
                        + "the overview's pixels.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(SourceResampleCommand.class, 2));
        CommandExecutor.of(SourceResampleCommand.class)
                .arg("sources", "Slide_03>ImageName>overview",
                        "The overview source is what we want to resample.")
                .arg("model", "Slide_03>ImageName>10x_01",
                        "The 10x_01 tile provides the target voxel grid.")
                .arg("reuse_mipmaps", true,
                        "Reuse the overview's multi-resolution pyramid — best zoom-out performance.")
                .arg("default_mipmap_level", 0,
                        "Highest-resolution level when mipmaps are not reused.")
                .arg("interpolate", true,
                        "Linear interpolation between overview voxels — smooth resampled output.")
                .arg("cache", true,
                        "Cache the resampled blocks so re-visited regions don't recompute.")
                .arg("name", resampledName,
                        "Output source name — lands under \"Other Sources\".")
                .sayOnDialog("Walking through the resample dialog — the parameters that shape the result.")
                .viaTreePath()
                .execute();
        Step.say("The command returns immediately — the resampled source is fully lazy.");
        Ui.rawPause(1500);
        Step.end();

        Step.begin("Show Resampled Source",
                "The resampled source lands under \"Other Sources\". We open it in a third BDV "
                        + "window placed beneath the two inputs, spanning the full width so the "
                        + "comparison is immediate. Title: 'BDV - Resampled Overview'.");
        Step.say("Double-clicking the Other Sources node so the new source is visible.");
        BdvPlayground.adjustViewOnSource("Other Sources");
        Ui.rawPause(1500);

        Step.say("Double-clicking the resampled source — pointing at what we'll display next.");
        BdvPlayground.adjustViewOnSource("Other Sources>" + resampledName);
        Ui.rawPause(1500);

        Step.say("Right-clicking the resampled source — walking to: "
                + CommandExecutor.menuPath(SingleBdvSourcesShowCommand.class, 2));
        CommandExecutor.of(SingleBdvSourcesShowCommand.class)
                .arg("auto_contrast", false,
                        "We carry over the source image display settings.")
                .arg("adjust_view", true,
                        "Re-centering on the resampled region.")
                .arg("make_new_window", true,
                        "Third window — the two input viewers stay untouched above.")
                .arg("interpolate", false)
                .sayOnDialog("Walking through the show-sources dialog one more time.")
                .viaTreePath("Other Sources>" + resampledName,
                        "We pick the just-created resampled source.")
                .skipInScript()
                .execute();

        Frame bdvResampled = Ui.waitForFrame("BigDataViewer", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.pause(1500);
        bdvArea.place(bdvResampled, 2, 2, 1, 0, 1, 2);

        CommandExecutor.of(BdvTitleSetCommand.class)
                .arg("title", "BDV - Resampled Overview",
                        "Naming this one too — three windows are now ready to be synchronised.")
                .sayOnDialog("Setting the window title.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.end();

        Step.begin("Synchronize All Three Viewers",
                "Finally, we link all three windows so pan and zoom propagate live across "
                        + "original, model and resampled — direct visual proof of voxel-perfect alignment.");
        Step.say("Right-clicking the Sources root — walking to: "
                + CommandExecutor.menuPath(ViewSynchronizeCommand.class, 2));
        CommandExecutor.of(ViewSynchronizeCommand.class)
                .arg("bdvhs",
                        new String[]{"BDV - Original Overview", "BDV - 10x_01 Model", "BDV - Resampled Overview"},
                        "Selecting the three BDV windows we just named — they will share pan / zoom.")
                .arg("bvvhs", null)
                .arg("synchronizetime", true,
                        "Synchronise timepoints too — irrelevant here (single timepoint) but harmless.")
                .sayOnDialog("Walking through the synchronize dialog.")
                .viaTreePath()
                .skipInScript()
                .execute();
        Step.say("Holding the final frame — moving in any window now moves the other two: "
                + "the resampled overview tracks the 10x_01 model pixel-for-pixel.");

        Frame syncWindow = Ui.waitForFrame("Close window to stop synchronization", 6000);
        Ui.resizeFrame(syncWindow, 450, 100);
        Ui.dragFrame(syncWindow, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y);

        Ui.rawPause(40000);
        Step.say("The resampled source can be stored (as OME-TIFF for instance) using the command "+CommandExecutor.menuPath(KheopsExportSourcesCommand.class));
        Step.end();

        // Outro / closing-card metadata. The command list is harvested
        // automatically from every Fiji command this demo invoked.
        Demo.outro("You've seen lazy source resampling end to end — pick an input and a model "
                        + "in the BDV-Playground tree, choose a few options, and get a third source "
                        + "that lives on the model's voxel grid. No eager pixel copy, fully lazy, "
                        + "ready for export or downstream alignment pipelines.",
                "Plugin & source: https://github.com/BIOP/ijp-imglib2bdvdemo-ij2",
                "BIOP — EPFL: https://www.epfl.ch/research/facilities/ptbiop/",
                "Sample brain-slides dataset: https://zenodo.org/records/6553641");

        Step.waitForUser("Video acquisition complete — clips, screenshots, timeline.json "
                + "and youtube-description.md are written. Click Continue to dismiss.");
    }
}