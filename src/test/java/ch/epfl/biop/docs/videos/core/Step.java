package ch.epfl.biop.docs.videos.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * One narrated phase of a tutorial demo. Pair {@link #begin(String, String)}
 * and {@link #end()} around the actions that belong to that phase:
 *
 * <pre>
 * Step.begin("open-bdv-tree", "From the search bar we open the BDV Playground tree.");
 * CommandExecutor.of(SourceServiceShowCommand.class).viaSearch("show bdv play").execute();
 * Step.end();
 * </pre>
 *
 * Each pair:
 * <ul>
 *   <li>appends one line to {@code script.md} in {@link Assets#dir()},
 *       timestamped from the first {@code begin} of the session — so the
 *       same source-of-truth file feeds both the YouTube voice-over and a
 *       written tutorial;</li>
 *   <li>brackets a screen-recorded clip via {@link ScreenRecorder} (named
 *       to match the step), so re-running the demo regenerates the
 *       per-step clip set ready for stitching with ffmpeg.</li>
 * </ul>
 *
 * <p>Toggles for fast iteration:</p>
 * <ul>
 *   <li>{@link ScreenRecorder#ENABLED} = false → no video clips</li>
 *   <li>{@link Screenshotter#ENABLED} = false → no screenshots</li>
 *   <li>{@link #WRITE_SCRIPT} = false → no script file</li>
 * </ul>
 *
 * <p>A JVM shutdown hook ensures any in-flight ffmpeg recording is
 * gracefully stopped if a script exits before {@code end()} runs (uncaught
 * exception, Ctrl+C, …) — otherwise the clip would be left truncated.</p>
 */
public class Step {

    public static boolean WRITE_SCRIPT = true;

    /**
     * Held at the end of every {@link #end()}: the final frame stays on
     * screen long enough for the recording to read as a clean stop rather
     * than a hard cut. Skipped when no recording is active.
     */
    public static long END_PAUSE_MS = 1500;

    /**
     * When {@code true} (default), {@link #end()} takes one screenshot
     * tagged with the step name — so each step automatically yields a
     * "final state" PNG without an explicit {@link #snap(String)} call.
     * Mid-step screenshots can still be taken via {@link #snap(String)}.
     */
    public static boolean AUTO_SNAP_END = true;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (ScreenRecorder.isRecording()) ScreenRecorder.stop();
        }, "Step-shutdown-stop-recorder"));
    }

    private static long sessionStartMs;
    private static String currentName;

    public static void begin(String name, String narration) {
        if (currentName != null) {
            throw new IllegalStateException("Step '" + currentName
                    + "' is still open — call Step.end() first.");
        }
        if (sessionStartMs == 0) sessionStartMs = System.currentTimeMillis();
        currentName = name;
        appendScript(System.currentTimeMillis(), name, narration);
        ScreenRecorder.start(name);
    }

    public static void end() {
        if (currentName == null) return;
        if (ScreenRecorder.isRecording()) Ui.rawPause(END_PAUSE_MS);
        if (AUTO_SNAP_END) Screenshotter.ofTargetScreen(currentName);
        ScreenRecorder.stop();
        currentName = null;
    }

    /** Take an extra screenshot mid-step, named {@code "<step>-<suffix>".png} (or just {@code suffix} outside a step). */
    public static File snap(String suffix) {
        String name = currentName == null ? suffix : currentName + "-" + suffix;
        return Screenshotter.ofTargetScreen(name);
    }

    public static String currentName() { return currentName; }

    private static void appendScript(long t, String name, String narration) {
        if (!WRITE_SCRIPT) return;
        long elapsed = t - sessionStartMs;
        long s = elapsed / 1000;
        String line = String.format("[%02d:%02d] **%s** — %s%n", s / 60, s % 60, name, narration);
        File f = new File(Assets.dir(), "script.md");
        try {
            Files.write(f.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Could not append to " + f, e);
        }
    }
}
