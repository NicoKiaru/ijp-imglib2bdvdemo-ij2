package ch.epfl.biop.docs.videos.core;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

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

    /**
     * When {@code true}, {@link #waitForUser(String)} returns immediately
     * instead of blocking on the Continue button — so a script with manual
     * phases can be raced through during fast iteration. Note that demos
     * whose downstream automation depends on the manual work being done
     * (e.g. a saved classifier file) will fail later when the file is
     * missing — skip whole step ranges if needed.
     */
    public static boolean SKIP_MANUAL = false;

    /**
     * Brief settle pause after the user closes the {@link #waitForUser}
     * dialog, so the OS has time to repaint the area underneath before the
     * next gesture (or the auto-snap at {@link #end()}) reads the screen.
     */
    public static long POST_DIALOG_SETTLE_MS = 300;

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

    /**
     * Show an instruction popup, then start the step once the user clicks
     * OK — the popup is gone before the recording begins. Use this when
     * the recorder needs to read what to do for a manual phase but you
     * don't want the popup itself to appear in the recorded clip.
     *
     * <p>Popup is placed off the target screen if multi-monitor is
     * available (so even if briefly visible it would be outside the
     * recording area). On a single screen it lands top-right of the
     * target screen.</p>
     *
     * <p>{@link #SKIP_MANUAL} = true skips the popup and starts the
     * recording immediately, matching the 2-arg {@link #begin(String, String)}.</p>
     */
    public static void begin(String name, String narration, String preInstructions) {
        if (currentName != null) {
            throw new IllegalStateException("Step '" + currentName
                    + "' is still open — call Step.end() first.");
        }
        if (!SKIP_MANUAL && preInstructions != null && !preInstructions.isEmpty()) {
            showBlockingPopup("Tutorial — " + name, preInstructions, "OK — start recording");
        }
        begin(name, narration);
    }

    public static void end() {
        if (currentName == null) return;
        if (ScreenRecorder.isRecording()) Ui.rawPause(END_PAUSE_MS);
        if (AUTO_SNAP_END) Screenshotter.ofRecordingArea(currentName);
        ScreenRecorder.stop();
        currentName = null;
    }

    /** Take an extra screenshot mid-step, named {@code "<step>-<suffix>".png} (or just {@code suffix} outside a step). */
    public static File snap(String suffix) {
        String name = currentName == null ? suffix : currentName + "-" + suffix;
        return Screenshotter.ofRecordingArea(name);
    }

    /**
     * Block the demo thread on a small floating dialog with on-screen
     * instructions and a Continue button. Use this to bracket steps that
     * can't (or shouldn't) be automated — drawing in Labkit, training a
     * classifier, navigating BDV to a hand-picked region — while keeping
     * the recording rolling so the manual actions are captured on video.
     *
     * <p>The dialog is placed on a screen that is <em>not</em> the
     * {@link Ui#targetScreen() target screen} when one exists, so it does
     * not appear in the recording. On a single-screen setup it lands in
     * the top-right of the target screen and the user can drag it
     * elsewhere.</p>
     *
     * <p>Returns when the user clicks Continue. {@link #SKIP_MANUAL} = true
     * makes this a no-op for fast iteration.</p>
     */
    public static void waitForUser(String instructions) {
        if (SKIP_MANUAL) return;
        showDonePopup(instructions);
    }

    private enum Placement { OFF_TARGET_OR_TOP_RIGHT, BOTTOM_RIGHT_INSET }

    /**
     * Small undecorated "Continue when done" popup, anchored bottom-right
     * of the target screen above the OS taskbar — inside the reserved
     * {@link Ui#RECORDING_BOTTOM_INSET_PX} strip, so dismissal does not
     * appear in the recorded clip.
     */
    private static void showDonePopup(String instructions) {
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("done");
            frame.setUndecorated(true);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            JLabel label = new JLabel("<html><div style='text-align:center;'>"
                    + instructions.replace("\n", "<br>") + "</div></html>");
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

            JButton btn = new JButton("Continue");
            btn.addActionListener(e -> {
                frame.dispose();
                latch.countDown();
            });

            JPanel root = new JPanel(new BorderLayout());
            root.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            root.add(label, BorderLayout.CENTER);
            JPanel south = new JPanel();
            south.add(btn);
            root.add(south, BorderLayout.SOUTH);
            frame.setContentPane(root);

            Dimension size = new Dimension(320, 80);
            frame.setSize(size);
            Rectangle place = popupBounds(size, Placement.BOTTOM_RIGHT_INSET);
            frame.setLocation(place.x, place.y);
            frame.setVisible(true);
            frame.toFront();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Ui.rawPause(POST_DIALOG_SETTLE_MS);
    }

    /**
     * Internal popup helper used by the 3-arg {@link #begin(String, String, String)}.
     * Blocks the demo thread until the user clicks the button labelled
     * {@code buttonLabel}. Placed off the target screen if multi-monitor
     * is available, top-right of the target screen otherwise — visible at
     * call time but recording isn't active yet, so it's safe.
     */
    private static void showBlockingPopup(String title, String instructions, String buttonLabel) {
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(title);
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            JTextArea text = new JTextArea(instructions);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            text.setFont(text.getFont().deriveFont(13f));

            JButton btn = new JButton(buttonLabel);
            btn.addActionListener(e -> {
                frame.dispose();
                latch.countDown();
            });

            JPanel south = new JPanel();
            south.add(btn);

            frame.getContentPane().setLayout(new BorderLayout());
            frame.add(new JScrollPane(text), BorderLayout.CENTER);
            frame.add(south, BorderLayout.SOUTH);
            Dimension size = new Dimension(440, 260);
            frame.setSize(size);
            Rectangle place = popupBounds(size, Placement.OFF_TARGET_OR_TOP_RIGHT);
            frame.setLocation(place.x, place.y);
            frame.setVisible(true);
            frame.toFront();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Ui.rawPause(POST_DIALOG_SETTLE_MS);
    }

    /** Resolve a popup placement strategy to a concrete screen-absolute rectangle. */
    private static Rectangle popupBounds(Dimension size, Placement placement) {
        Rectangle target = Ui.targetScreenBounds();
        switch (placement) {
            case BOTTOM_RIGHT_INSET: {
                Insets os = Toolkit.getDefaultToolkit().getScreenInsets(
                        Ui.targetScreen().getDefaultConfiguration());
                return new Rectangle(
                        target.x + target.width - size.width - 10,
                        target.y + target.height - os.bottom - size.height - 8,
                        size.width, size.height);
            }
            case OFF_TARGET_OR_TOP_RIGHT:
            default: {
                for (GraphicsDevice gd : Ui.screens()) {
                    Rectangle b = gd.getDefaultConfiguration().getBounds();
                    if (!b.equals(target)) {
                        return new Rectangle(b.x + 40, b.y + 40, size.width, size.height);
                    }
                }
                return new Rectangle(
                        target.x + target.width - size.width - 40,
                        target.y + 40,
                        size.width, size.height);
            }
        }
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
