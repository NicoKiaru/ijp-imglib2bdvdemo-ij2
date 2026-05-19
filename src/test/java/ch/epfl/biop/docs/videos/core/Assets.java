package ch.epfl.biop.docs.videos.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Per-demo output session. Pick a name once at the top of a demo's
 * {@code main} via {@link #session(String)}; {@link Screenshotter},
 * {@link ScreenRecorder} and {@link Step} all write inside the
 * resulting directory.
 *
 * <p>Default root is {@code target/video-assets/} (under the Maven build
 * dir, so {@code mvn clean} wipes it — which is what we want during
 * iteration). Override {@link #ROOT} before {@link #session(String)} to
 * write somewhere persistent.</p>
 */
public class Assets {

    public static File ROOT = new File("target/video-assets");

    private static File currentDir;
    private static String currentDemo;

    /**
     * Start (or reset) a session for {@code demoName}. Creates the directory
     * and clears any prior {@code script.md} so re-running the demo gives a
     * fresh transcript. Does not delete existing PNGs / MP4s — those are
     * overwritten as the counters rewrite the same NN-name.* slots.
     */
    public static File session(String demoName) {
        currentDemo = demoName;
        currentDir = new File(ROOT, demoName);
        if (!currentDir.exists() && !currentDir.mkdirs()) {
            throw new RuntimeException("Could not create assets dir " + currentDir);
        }
        File script = new File(currentDir, "script.md");
        if (script.exists()) {
            try {
                Files.delete(script.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Could not clear " + script, e);
            }
        }
        Screenshotter.resetCounter();
        ScreenRecorder.resetCounter();
        Events.onSessionStart();
        return currentDir;
    }

    /** Current session directory; auto-creates a {@code "default"} session if none was opened. */
    public static File dir() {
        if (currentDir == null) session("default");
        return currentDir;
    }

    public static String demoName() { return currentDemo; }
}
