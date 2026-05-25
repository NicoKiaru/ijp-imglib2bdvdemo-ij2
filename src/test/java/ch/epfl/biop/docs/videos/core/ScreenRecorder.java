package ch.epfl.biop.docs.videos.core;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>First-frame anchor.</b> {@code timeline.json} timestamps must be
 * relative to the first <em>captured frame</em> of each clip, not to the
 * moment the ffmpeg process was spawned — there is a startup latency of
 * a few hundred ms between {@code Process.start()} and gdigrab grabbing
 * its first frame. To measure it, ffmpeg is launched with
 * {@code -progress pipe:1}: a reader thread parses the first progress
 * block, reads its {@code out_time_us} (the output-stream time of the
 * last muxed frame, which is {@code 0} at the first frame), and computes
 * {@code firstFrameWallMs = now - out_time}. Both clocks are the Unix
 * epoch ({@code System.currentTimeMillis()} and gdigrab's {@code av_gettime()}),
 * so the difference is the wall-clock time of the clip's first frame.
 * {@link #currentClipFirstFrameWallMs()} exposes it once resolved.</p>
 *
 * <p>Toggle off via {@link #ENABLED} for fast iteration. When disabled,
 * no ffmpeg process is spawned, but the clip name / index counter still
 * advance so {@code timeline.json} stays consistent across modes.</p>
 */
public class ScreenRecorder {

    public static boolean ENABLED = true;

    /** Path / executable name for ffmpeg. Override if not on PATH. */
    public static String FFMPEG_PATH = "ffmpeg";

    /**
     * Capture / output frame rate. Used on both the gdigrab input
     * ({@code -framerate}) and the encoder output ({@code -r}), and the
     * stream is forced to this exact rate via {@code -vsync cfr} so every
     * clip is genuine constant-frame-rate — frame N maps to wall-clock time
     * N/FRAMERATE, which {@code timeline.json} consumers rely on.
     */
    public static int FRAMERATE = 30;

    /** {@code libx264} preset. {@code ultrafast} keeps CPU low; {@code veryfast} = smaller files. */
    public static String VIDEO_PRESET = "ultrafast";

    /**
     * ffmpeg input buffer size. The default is small and full-screen 1080p
     * at 30 fps tends to drop frames on a busy machine; bump if logs warn
     * about "real-time buffer too full".
     */
    public static String RTBUFSIZE = "200M";

    /**
     * ffmpeg {@code -stats_period} (seconds, as a string) — how often the
     * {@code -progress} stream emits a block. A short value resolves the
     * first-frame anchor sooner. Set to {@code null}/empty to omit the flag
     * (ffmpeg then uses its ~0.5 s default; {@code -stats_period} needs
     * ffmpeg ≥ 4.4).
     */
    public static String STATS_PERIOD = "0.2";

    /** Brief wait after launching ffmpeg so the first action is actually captured. */
    public static long STARTUP_PAUSE_MS = 800;

    /**
     * How long {@link #stop()} waits for the first-frame anchor to be
     * resolved from the {@code -progress} stream before tearing ffmpeg
     * down. Normally the anchor resolves within the {@link #STARTUP_PAUSE_MS}
     * window, long before {@code stop()} — this is only a safety net for an
     * unusually short clip.
     */
    public static long FIRST_FRAME_WAIT_MS = 1500;

    private static int counter = 0;
    private static Process process;
    private static File currentFile;
    private static String currentClip;
    private static int currentIndex;
    private static volatile long firstFrameWallMs;

    /**
     * Start recording the target screen into a new clip — uses
     * {@link Ui#recordingBoundsPhysical()}, so the bottom
     * {@link Ui#RECORDING_BOTTOM_INSET_PX} strip (reserved for the
     * "Continue when done" popup and OS taskbar) is excluded.
     */
    public static void start(String clipName) {
        start(clipName, Ui.recordingBoundsPhysical());
    }

    /**
     * Start recording a screen-absolute rectangle. The rectangle is
     * interpreted in <em>physical</em> pixels (gdigrab convention) — pass
     * {@link Ui#targetScreenPhysicalBounds()} or pre-scale logical bounds
     * yourself.
     */
    public static synchronized void start(String clipName, Rectangle region) {
        if (process != null) {
            throw new IllegalStateException("Already recording '" + currentClip
                    + "' — call stop() before starting a new clip.");
        }
        // Auto-arm the human-gesture capture. Idempotent — installs the
        // global AWT listener exactly once, regardless of how many clips
        // get recorded; the listener itself gates on Step.currentName()
        // so it only emits inside an open step.
        if (EventRecorder.ENABLED) EventRecorder.enable();
        String safe = clipName.replaceAll("[^A-Za-z0-9._-]+", "_");
        currentIndex = ++counter;
        String fileName = String.format("%03d-%s.mp4", currentIndex, safe);
        currentFile = new File(Assets.dir(), fileName);
        currentClip = clipName;
        firstFrameWallMs = 0L;

        // Even with recording disabled the clip name / index still advance,
        // so timeline.json carries the same `clip` / `index` it would in a
        // real recording pass.
        if (!ENABLED) return;

        File logFile = new File(Assets.dir(), fileName + ".log");
        List<String> cmd = new ArrayList<>();
        cmd.add(FFMPEG_PATH);
        cmd.add("-y");
        cmd.add("-rtbufsize"); cmd.add(RTBUFSIZE);
        cmd.add("-f"); cmd.add("gdigrab");
        cmd.add("-framerate"); cmd.add(String.valueOf(FRAMERATE));
        cmd.add("-offset_x"); cmd.add(String.valueOf(region.x));
        cmd.add("-offset_y"); cmd.add(String.valueOf(region.y));
        cmd.add("-video_size"); cmd.add(region.width + "x" + region.height);
        // A roomy thread queue keeps gdigrab from stalling the capture pipe
        // ("Thread message queue blocking" warnings in the .log) on a busy box.
        cmd.add("-thread_queue_size"); cmd.add("512");
        cmd.add("-i"); cmd.add("desktop");
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add(VIDEO_PRESET);
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        // Force genuine constant-frame-rate output. gdigrab is variable-rate
        // (it drops frames unevenly under load), which makes timeline.json's
        // wall-clock timestamps drift when mapped onto frames at a fixed fps.
        // `-vsync cfr` makes the muxer conform the stream to exactly FRAMERATE
        // fps by duplicating/dropping frames, so output frame N always shows
        // whatever was last on screen at wall-clock time N/FRAMERATE — even
        // when the machine can't encode fast enough. `-r` pins that rate.
        // `-vsync` works on every ffmpeg version; `-fps_mode cfr` is the
        // equivalent >= 5.1 spelling — switch if a recent ffmpeg is pinned.
        cmd.add("-r"); cmd.add(String.valueOf(FRAMERATE));
        cmd.add("-vsync"); cmd.add("cfr");
        if (STATS_PERIOD != null && !STATS_PERIOD.isEmpty()) {
            cmd.add("-stats_period"); cmd.add(STATS_PERIOD);
        }
        // Machine-readable progress on stdout; ffmpeg's own diagnostics
        // (banner, warnings, final summary) go to stderr -> the .log file.
        cmd.add("-progress"); cmd.add("pipe:1");
        cmd.add(currentFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(logFile);
        try {
            process = pb.start();
        } catch (IOException e) {
            process = null;
            throw new RuntimeException("Could not start ffmpeg — is '" + FFMPEG_PATH
                    + "' on PATH?", e);
        }
        startProgressReader(process);
        Ui.rawPause(STARTUP_PAUSE_MS);
    }

    /**
     * Drain ffmpeg's {@code -progress} stdout on a daemon thread. The first
     * block carrying a positive {@code out_time} resolves
     * {@link #firstFrameWallMs}; the thread keeps reading afterwards so the
     * pipe never fills (which would stall ffmpeg).
     */
    private static void startProgressReader(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.US_ASCII))) {
                String line;
                long blockOutTimeUs = -1L;
                while ((line = r.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    // ffmpeg emits both out_time_us and out_time_ms; both
                    // carry microseconds (a long-standing quirk kept for
                    // backward compatibility).
                    if ("out_time_us".equals(key) || "out_time_ms".equals(key)) {
                        try {
                            long us = Long.parseLong(val);
                            if (us > blockOutTimeUs) blockOutTimeUs = us;
                        } catch (NumberFormatException ignored) {
                            // "N/A" before the first frame is muxed
                        }
                    } else if ("progress".equals(key)) {
                        if (firstFrameWallMs == 0L && blockOutTimeUs > 0L) {
                            firstFrameWallMs = System.currentTimeMillis()
                                    - blockOutTimeUs / 1000L;
                        }
                        blockOutTimeUs = -1L;
                    }
                }
            } catch (IOException ignored) {
                // pipe closed when ffmpeg exits — expected
            }
        }, "ffmpeg-progress-reader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Stop the current recording. Writes {@code 'q'} to ffmpeg's stdin
     * (graceful exit — cleanly flushes the moov atom, which destroying the
     * process does not), then waits up to 10 s before forcibly killing.
     * Returns the resulting {@link File}, or {@code null} if not recording.
     *
     * <p>The clip's name / index / first-frame anchor are deliberately kept
     * after {@code stop()} so {@link Timeline} can read them when finalising
     * the step; they are overwritten by the next {@link #start}.</p>
     */
    public static synchronized File stop() {
        if (process == null) return currentFile;
        // Safety net: make sure the first-frame anchor has been resolved
        // from the -progress stream before ffmpeg is torn down.
        long deadline = System.currentTimeMillis() + FIRST_FRAME_WAIT_MS;
        while (firstFrameWallMs == 0L && process.isAlive()
                && System.currentTimeMillis() < deadline) {
            Ui.rawPause(50);
        }
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
        return file;
    }

    public static boolean isRecording() { return process != null; }

    /** File name of the current / most-recent clip, e.g. {@code "002-open-bdv-tree.mp4"}. */
    public static String currentClipFileName() {
        return currentFile == null ? null : currentFile.getName();
    }

    /** 1-based index of the current / most-recent clip (matches its {@code NNN-} prefix). */
    public static int currentClipIndex() { return currentIndex; }

    /**
     * Wall-clock time (epoch ms) of the first captured frame of the
     * current / most-recent clip, or {@code 0} if not resolved (recording
     * disabled, or ffmpeg emitted no usable progress block).
     */
    public static long currentClipFirstFrameWallMs() { return firstFrameWallMs; }

    static void resetCounter() { counter = 0; }
}