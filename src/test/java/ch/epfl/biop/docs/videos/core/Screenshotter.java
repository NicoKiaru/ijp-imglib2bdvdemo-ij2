package ch.epfl.biop.docs.videos.core;

import javax.imageio.ImageIO;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Java-side screenshot capture for tutorial assets, built on
 * {@link java.awt.Robot#createScreenCapture(Rectangle)}.
 *
 * <p>PNGs land in {@link Assets#dir()} as {@code "NNN-name.png"} where NNN
 * is an auto-incrementing counter — so the alphabetical sort matches
 * capture order.</p>
 *
 * <p>Toggle off via {@link #ENABLED} for fast iteration. When disabled,
 * every method is a no-op that returns {@code null}.</p>
 */
public class Screenshotter {

    public static boolean ENABLED = true;

    private static int counter = 0;

    /** Capture the bounds of the given frame (includes its native title bar). */
    public static File ofFrame(Frame frame, String name) {
        return ofRegion(frame.getBounds(), name);
    }

    /** Capture a screen-absolute rectangle. */
    public static File ofRegion(Rectangle region, String name) {
        if (!ENABLED) return null;
        BufferedImage img = Ui.robot().createScreenCapture(region);
        return write(img, name);
    }

    /** Capture the whole target screen (per {@link Ui#targetScreenBounds()}). */
    public static File ofTargetScreen(String name) {
        return ofRegion(Ui.targetScreenBounds(), name);
    }

    private static File write(BufferedImage img, String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        String fileName = String.format("%03d-%s.png", ++counter, safe);
        File out = new File(Assets.dir(), fileName);
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new RuntimeException("Could not write screenshot " + out, e);
        }
        return out;
    }

    static void resetCounter() { counter = 0; }
}
