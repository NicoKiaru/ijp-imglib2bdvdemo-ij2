package ch.epfl.biop.docs.videos;

import ch.epfl.biop.demos.DemoHelper;
import ij.IJ;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;

import java.awt.Frame;
import java.awt.Point;
import java.util.Map;

/**
 * High-level Fiji UI actions built on {@link RobotHelper} and {@link ComponentInspector}.
 */
public class FijiCommands {

    static { LegacyInjector.preinit(); }

    /**
     * Starts Fiji, waits for the legacy IJ main frame, places it on the
     * target screen via {@link RobotHelper#placeFrame(Frame)}, and returns it.
     */
    public static Frame startFiji() {
        ImageJ ij = new ImageJ();
        DemoHelper.startFiji(ij);
        Frame frame = waitForIJFrame(Timings.FRAME_WAIT_TIMEOUT_MS);
        if (frame == null) throw new IllegalStateException("ImageJ main frame not available after "
                + Timings.FRAME_WAIT_TIMEOUT_MS + "ms");
        RobotHelper.placeFrame(frame);
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);
        return frame;
    }

    /**
     * Runs a Fiji command via the legacy search bar in the IJ main frame:
     * focus → type query → Enter (runs top match).
     *
     * @param query e.g. {@code "show bdv play"}
     */
    public static void searchAndRun(String query) {
        Frame ij = IJ.getInstance();
        if (ij == null) throw new IllegalStateException("IJ main frame not available");

        Point center = findSearchBar(ij).screenCenter();
        if (center == null) {
            throw new IllegalStateException("LegacySearchBar has no screen position — "
                    + "is the IJ frame realized and on screen?");
        }
        RobotHelper.moveTo(center);
        RobotHelper.pause(Timings.PAUSE_AFTER_MOVE_MS);
        RobotHelper.click();
        RobotHelper.pause(Timings.PAUSE_AFTER_CLICK_MS);
        RobotHelper.type(query);
        RobotHelper.pause(Timings.PAUSE_AFTER_TYPING_MS);
        RobotHelper.enter();
    }

    private static ComponentInspector.ComponentInfo findSearchBar(Frame frame) {
        Map<String, ComponentInspector.ComponentInfo> tree = ComponentInspector.inspect(frame);
        for (ComponentInspector.ComponentInfo info : tree.values()) {
            if (info.className != null && info.className.contains("LegacySearchBar")) {
                return info;
            }
        }
        throw new IllegalStateException("LegacySearchBar not found in IJ main frame");
    }

    private static Frame waitForIJFrame(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ij.ImageJ ij1 = IJ.getInstance();
            if (ij1 != null && ij1.isShowing()) return ij1;
            RobotHelper.rawPause(Timings.FRAME_POLL_INTERVAL_MS);
        }
        return null;
    }
}