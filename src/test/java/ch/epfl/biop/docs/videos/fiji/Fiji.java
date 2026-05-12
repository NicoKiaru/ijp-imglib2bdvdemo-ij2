package ch.epfl.biop.docs.videos.fiji;

import ch.epfl.biop.docs.videos.core.Inspector;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;

import ch.epfl.biop.demos.DemoHelper;
import ij.IJ;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.Context;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.util.Map;

/**
 * High-level Fiji UI actions built on {@link Ui} and {@link Inspector}.
 */
public class Fiji {

    static { LegacyInjector.preinit(); }

    /** The SciJava {@link ImageJ} instance booted by {@link #startFiji()} (set on first call). */
    public static ImageJ IJ_INSTANCE;

    /** Convenience: the SciJava {@link Context} from {@link #IJ_INSTANCE}. */
    public static Context context() {
        if (IJ_INSTANCE == null) {
            throw new IllegalStateException("Fiji not started yet — call Fiji.startFiji() first");
        }
        return IJ_INSTANCE.getContext();
    }

    /**
     * Starts Fiji, waits for the legacy IJ main frame, places it on the
     * target screen via {@link Ui#placeFrame(Frame)}, and returns it.
     */
    public static Frame startFiji() {
        ImageJ ij = new ImageJ();
        IJ_INSTANCE = ij;
        DemoHelper.startFiji(ij);
        Frame frame = waitForIJFrame(Timings.FRAME_WAIT_TIMEOUT_MS);
        if (frame == null) throw new IllegalStateException("ImageJ main frame not available after "
                + Timings.FRAME_WAIT_TIMEOUT_MS + "ms");
        Ui.placeFrame(frame);
        Ui.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);
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
        SwingUtilities.invokeLater(() -> { ij.toFront(); ij.requestFocus(); });
        Ui.rawPause(150);
        Ui.moveTo(center);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        Ui.type(query);
        Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
        Ui.enter();
    }

    private static Inspector.ComponentInfo findSearchBar(Frame frame) {
        Map<String, Inspector.ComponentInfo> tree = Inspector.inspect(frame);
        for (Inspector.ComponentInfo info : tree.values()) {
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
            Ui.rawPause(Timings.FRAME_POLL_INTERVAL_MS);
        }
        return null;
    }
}