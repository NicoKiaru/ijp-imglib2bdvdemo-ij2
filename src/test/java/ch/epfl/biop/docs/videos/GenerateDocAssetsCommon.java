package ch.epfl.biop.docs.videos;

import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;

import java.awt.*;

public class GenerateDocAssetsCommon {

    public static void boot() {

        Step.begin("boot-fiji",
                "We start Fiji and place its main window on the secondary monitor.");
        Step.say("Booting a fresh Fiji instance with the BIOP update site enabled — "
                + "this brings in BDV Playground and the deconvolution command.");
        Fiji.startFiji();
        Step.end();
    }

    public static void showBdvTreeDisplay() {

        Step.begin("open-bdv-tree",
                "From Fiji's search bar we open the BDV Playground Sources tree, "
                        + "where every loaded source will land.");
        Step.say("Using Fiji's search bar, we type 'show bdv play' to find BigDataViewer Playground tree display.");
        CommandExecutor.of(SourceServiceShowCommand.class)
                .viaSearch("show bdv play")
                .execute();
        Frame bdvSources = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvSources, 450, 600);
        Ui.dragFrame(bdvSources, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y + 150);
        Step.end();
    }
}
