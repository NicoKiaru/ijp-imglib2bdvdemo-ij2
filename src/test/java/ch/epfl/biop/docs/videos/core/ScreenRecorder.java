package ch.epfl.biop.docs.videos.core;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an ffmpeg subprocess for screen recording. One clip at a time —
 * {@link #start(String)} begins a clip, {@link #stop()} ends it. Clips land
 * in {@link Assets#dir()} as {@code "NNN-name.mp4"} (alphabetical = recording
 * order), with the corresponding ffmpeg log next to each one.
 *
 * <p>On Windows we use {@code -f gdigrab -i desktop} to capture an offset
 * region of the desktop. Default region is {@link Ui#targetScreenBounds()}
 * (so a multi-monitor setup records only the target screen).</p>
 *
 * <p>Toggle off via {@link #ENABLED} for fast iteration. When disabled,
 * every method is a no-op.</p>
 */
public class ScreenRecorder {

    public static boolean ENABLED = true;

    /** Path / executable name for ffmpeg. Override if not on PATH. */
    public static String FFMPEG_PATH = "ffmpeg";

    public static int FRAMERATE = 30;

    /** {@code libx264} preset. {@code ultrafast} keeps CPU low; {@code veryfast} = smaller files. */
    public static String VIDEO_PRESET = "ultrafast";

    /**
     * ffmpeg input buffer size. The default is small and full-screen 1080p
     * at 30 fps tends to drop frames on a busy machine; bump if logs warn
     * about "real-time buffer too full".
     */
    public static String RTBUFSIZE = "200M";

    /** Brief wait after launching ffmpeg so the first action is actually captured. */
    public static long STARTUP_PAUSE_MS = 800;

    private static int counter = 0;
    private static Process process;
    private static File currentFile;
    private static String currentClip;

    /**
     * Start recording the full target screen into a new clip. Uses
     * {@link Ui#targetScreenPhysicalBounds()} so the capture matches the
     * actual physical pixels that gdigrab samples — see that method's note
     * about HiDPI / DPI-scaling.
     */
    public static void start(String clipName) {
        start(clipName, Ui.targetScreenPhysicalBounds());
    }

    /**
     * Start recording a screen-absolute rectangle. The rectangle is
     * interpreted in <em>physical</em> pixels (gdigrab convention) — pass
     * {@link Ui#targetScreenPhysicalBounds()} or pre-scale logical bounds
     * yourself.
     */
    public static synchronized void start(String clipName, Rectangle region) {
        if (!ENABLED) return;
        if (process != null) {
            throw new IllegalStateException("Already recording '" + currentClip
                    + "' — call stop() before starting a new clip.");
        }
        String safe = clipName.replaceAll("[^A-Za-z0-9._-]+", "_");
        String fileName = String.format("%03d-%s.mp4", ++counter, safe);
        currentFile = new File(Assets.dir(), fileName);
        currentClip = clipName;
        File logFile = new File(Assets.dir(), fileName + ".log");

        ProcessBuilder pb = new ProcessBuilder(
                FFMPEG_PATH,
                "-y",
                "-rtbufsize", RTBUFSIZE,
                "-f", "gdigrab",
                "-framerate", String.valueOf(FRAMERATE),
                "-offset_x", String.valueOf(region.x),
                "-offset_y", String.valueOf(region.y),
                "-video_size", region.width + "x" + region.height,
                "-i", "desktop",
                "-c:v", "libx264",
                "-preset", VIDEO_PRESET,
                "-pix_fmt", "yuv420p",
                currentFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile);
        try {
            process = pb.start();
        } catch (IOException e) {
            currentFile = null;
            currentClip = null;
            throw new RuntimeException("Could not start ffmpeg — is '" + FFMPEG_PATH
                    + "' on PATH?", e);
        }
        Ui.rawPause(STARTUP_PAUSE_MS);
    }

    /**
     * Stop the current recording. Writes {@code 'q'} to ffmpeg's stdin
     * (graceful exit — cleanly flushes the moov atom, which destroying the
     * process does not), then waits up to 10 s before forcibly killing.
     * Returns the resulting {@link File}, or {@code null} if not recording.
     */
    public static synchronized File stop() {
        if (!ENABLED || process == null) return null;
        try {
            OutputStream stdin = process.getOutputStream();
            try {
                stdin.write('q');
                stdin.flush();
                stdin.close();
            } catch (IOException ignored) {
                // ffmpeg already exited (e.g. crashed) — fall through to wait
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
        }
        File file = currentFile;
        process = null;
        currentFile = null;
        currentClip = null;
        return file;
    }

    public static boolean isRecording() { return process != null; }

    static void resetCounter() { counter = 0; }
}
