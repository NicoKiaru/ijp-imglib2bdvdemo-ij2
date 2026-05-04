package ch.epfl.biop.docs.videos;

import ij.IJ;

import java.awt.Frame;

/**
 * Drives Fiji's legacy search bar via Robot to run "show bdv play" —
 * opens the BigDataViewer-Playground "BDV Sources" tree view, then
 * inspects that window.
 */
public class SearchBarTest {

    public static void main(String[] args) {
        FijiCommands.startFiji();
        FijiCommands.searchAndRun("show bdv play");

        Frame bdvSources = RobotHelper.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        bdvSources.setSize(450, 600);

        // Place BDV Sources on the target screen, just below the IJ main frame.
        RobotHelper.placeFrame(bdvSources, RobotHelper.DEFAULT_FRAME_X, RobotHelper.DEFAULT_FRAME_Y);

        // Demo: visibly minimize Fiji's main frame by clicking its title-bar minimize button.
        RobotHelper.pause(500);
        RobotHelper.minimizeWithMouse(IJ.getInstance());
    }
}