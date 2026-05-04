package ch.epfl.biop.docs.videos;

import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Reusable helpers for tutorial-video automation built on top of {@link Robot}.
 *
 * Conventions:
 *   - All public coordinates are screen-absolute (already include the target
 *     screen's origin). Use {@link #framePos(int, int)} to express a point as
 *     "(dx, dy) from the default frame origin on the target screen".
 *   - Defaults are mutable {@code public static} fields so a video script can
 *     tweak them at the top of {@code main} without touching the helper.
 */
public class RobotHelper {

    // ===== Tweakable defaults =====================================================

    /** Index into {@link GraphicsEnvironment#getScreenDevices()}. 0 = primary. */
    public static int DEFAULT_SCREEN = 0;

    /** Top-left position of the GUI frame, RELATIVE to the target screen's origin. */
    public static int DEFAULT_FRAME_X = 100;
    public static int DEFAULT_FRAME_Y = 100;

    // Timings (durations, holds, polling) live in {@link Timings}.

    // ===== Robot singleton ========================================================

    private static Robot robot;

    public static Robot robot() {
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(0);
            } catch (AWTException e) {
                throw new RuntimeException("Cannot create Robot", e);
            }
        }
        return robot;
    }

    // ===== Screen handling ========================================================

    /** All screens, in the order returned by the local graphics environment. */
    public static GraphicsDevice[] screens() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    }

    /** Target screen ({@link #DEFAULT_SCREEN}, clamped to what's available). */
    public static GraphicsDevice targetScreen() {
        GraphicsDevice[] all = screens();
        int idx = Math.max(0, Math.min(DEFAULT_SCREEN, all.length - 1));
        return all[idx];
    }

    /** Bounds of the target screen in absolute virtual-desktop coordinates. */
    public static Rectangle targetScreenBounds() {
        return targetScreen().getDefaultConfiguration().getBounds();
    }

    /** Logs the detected screens — useful to confirm DEFAULT_SCREEN is correct. */
    public static void logScreens() {
        GraphicsDevice[] all = screens();
        System.out.println("[RobotHelper] " + all.length + " screen(s) detected:");
        for (int i = 0; i < all.length; i++) {
            Rectangle b = all[i].getDefaultConfiguration().getBounds();
            String marker = (i == Math.min(DEFAULT_SCREEN, all.length - 1)) ? "  <-- TARGET" : "";
            System.out.println("  [" + i + "] " + all[i].getIDstring()
                    + " bounds=" + b + marker);
        }
    }

    // ===== Frame placement ========================================================

    /** Default frame origin in absolute screen coordinates (target screen + offset). */
    public static Point defaultFrameOrigin() {
        Rectangle b = targetScreenBounds();
        return new Point(b.x + DEFAULT_FRAME_X, b.y + DEFAULT_FRAME_Y);
    }

    /** Resolves a frame-relative point ({@code dx, dy}) to absolute screen coords. */
    public static Point framePos(int dx, int dy) {
        Point o = defaultFrameOrigin();
        return new Point(o.x + dx, o.y + dy);
    }

    /**
     * Returns the first visible {@link Frame} whose title contains
     * {@code titleSubstring} (case-insensitive), or {@code null}.
     */
    public static Frame findFrame(String titleSubstring) {
        String needle = titleSubstring.toLowerCase();
        for (Frame f : Frame.getFrames()) {
            String title = f.getTitle();
            if (f.isVisible() && title != null && title.toLowerCase().contains(needle)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Polls {@link #findFrame(String)} for up to {@code timeoutMs} ms.
     * Returns the matching frame as soon as it appears, or {@code null} on timeout.
     */
    public static Frame waitForFrame(String titleSubstring, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Frame f = findFrame(titleSubstring);
            if (f != null) return f;
            rawPause(Timings.FRAME_POLL_INTERVAL_MS);
        }
        return null;
    }

    /** Move a frame to {@link #defaultFrameOrigin()} and bring it to front. */
    public static void placeFrame(Frame frame) {
        placeFrame(frame, DEFAULT_FRAME_X, DEFAULT_FRAME_Y);
    }

    /**
     * Visibly moves the cursor to the minimize button of the given frame's
     * title bar (Windows-style: leftmost of the three top-right buttons) and
     * clicks it.
     *
     * The position is estimated from {@link Frame#getInsets()}: button width
     * is taken as 1.5 × title-bar height, which matches Windows 10/11 default
     * theming. Will not work for borderless frames or non-native LAFs that
     * draw their own title bar.
     */
    public static void minimizeWithMouse(Frame frame) {
        Insets insets = frame.getInsets();
        int titleBarHeight = Math.max(insets.top, 24);
        int buttonWidth = (int) (1.5 * titleBarHeight);
        int x = frame.getX() + frame.getWidth() - (int) (1.5 * buttonWidth);
        int y = frame.getY() + titleBarHeight / 2;

        moveTo(x, y);
        pause(Timings.PAUSE_AFTER_MOVE_MS);
        click();
    }

    /**
     * Move a frame to {@code (dxOnTargetScreen, dyOnTargetScreen)} relative to
     * the target screen's top-left corner, and bring it to front.
     */
    public static void placeFrame(Frame frame, int dxOnTargetScreen, int dyOnTargetScreen) {
        Rectangle b = targetScreenBounds();
        int absX = b.x + dxOnTargetScreen;
        int absY = b.y + dyOnTargetScreen;
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setLocation(absX, absY);
                frame.toFront();
                frame.requestFocus();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Mouse motion ===========================================================

    /**
     * Smoothly moves the cursor to {@code (x, y)} using a sine ease-in / ease-out
     * profile (slow at both ends, fast in the middle). The duration is scaled
     * by {@link Timings#GLOBAL_SPEED}.
     *
     * @param x absolute screen X
     * @param y absolute screen Y
     * @param durationMs total motion duration (before GLOBAL_SPEED scaling)
     * @param frames number of intermediate steps (NOT scaled)
     */
    public static void moveTo(int x, int y, long durationMs, int frames) {
        long scaledMs = Timings.scaled(durationMs);
        Point start = MouseInfo.getPointerInfo().getLocation();
        int perFrame = (int) Math.max(1, scaledMs / Math.max(1, frames));
        for (int i = 1; i <= frames; i++) {
            double t = (double) i / frames;
            double eased = 0.5 - 0.5 * Math.cos(Math.PI * t); // sin ease-in-out
            int xi = start.x + (int) Math.round((x - start.x) * eased);
            int yi = start.y + (int) Math.round((y - start.y) * eased);
            robot().mouseMove(xi, yi);
            robot().delay(perFrame);
        }
    }

    /** Convenience: smooth move using {@link Timings#MOUSE_MOVE_MS} / {@link Timings#MOUSE_MOVE_FRAMES}. */
    public static void moveTo(int x, int y) {
        moveTo(x, y, Timings.MOUSE_MOVE_MS, Timings.MOUSE_MOVE_FRAMES);
    }

    /** Convenience: smooth move to a {@link Point}. */
    public static void moveTo(Point p) {
        moveTo(p.x, p.y);
    }

    /** Teleport the cursor (no animation). */
    public static void jumpTo(int x, int y) {
        robot().mouseMove(x, y);
    }

    // ===== Clicks / keys ==========================================================

    public static void click() {
        robot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot().delay(Timings.CLICK_HOLD_MS);
        robot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void doubleClick() {
        click();
        robot().delay(Timings.DOUBLE_CLICK_GAP_MS);
        click();
    }

    public static void rightClick() {
        robot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot().delay(Timings.CLICK_HOLD_MS);
        robot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public static void key(int keyCode) {
        robot().keyPress(keyCode);
        robot().delay(Timings.KEY_HOLD_MS);
        robot().keyRelease(keyCode);
    }

    public static void escape() {
        key(KeyEvent.VK_ESCAPE);
    }

    public static void enter() {
        key(KeyEvent.VK_ENTER);
    }

    /**
     * Types an ASCII string. Handles letters (with shift for uppercase),
     * digits, space, and a small set of common symbols. Throws if a character
     * has no mapping — extend {@link #keyCodeFor(char)} as needed.
     */
    public static void type(String s) {
        Robot r = robot();
        long interChar = Timings.scaled(Timings.TYPE_INTER_CHAR_MS);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int code = keyCodeFor(c);
            boolean shift = needsShift(c);
            if (shift) r.keyPress(KeyEvent.VK_SHIFT);
            r.keyPress(code);
            r.delay(Timings.TYPE_KEY_HOLD_MS);
            r.keyRelease(code);
            if (shift) r.keyRelease(KeyEvent.VK_SHIFT);
            r.delay((int) interChar);
        }
    }

    private static boolean needsShift(char c) {
        if (Character.isUpperCase(c)) return true;
        switch (c) {
            case '!': case '@': case '#': case '$': case '%':
            case '^': case '&': case '*': case '(': case ')':
            case '_': case '+': case '{': case '}': case ':':
            case '"': case '<': case '>': case '?': case '~':
                return true;
            default:
                return false;
        }
    }

    private static int keyCodeFor(char c) {
        if (c >= 'a' && c <= 'z') return KeyEvent.VK_A + (c - 'a');
        if (c >= 'A' && c <= 'Z') return KeyEvent.VK_A + (c - 'A');
        if (c >= '0' && c <= '9') return KeyEvent.VK_0 + (c - '0');
        switch (c) {
            case ' ':  return KeyEvent.VK_SPACE;
            case '.':  return KeyEvent.VK_PERIOD;
            case ',':  return KeyEvent.VK_COMMA;
            case '-':  return KeyEvent.VK_MINUS;
            case '_':  return KeyEvent.VK_MINUS;     // shift handled separately
            case '=':  return KeyEvent.VK_EQUALS;
            case '+':  return KeyEvent.VK_EQUALS;    // shift
            case '/':  return KeyEvent.VK_SLASH;
            case '?':  return KeyEvent.VK_SLASH;     // shift
            case ';':  return KeyEvent.VK_SEMICOLON;
            case ':':  return KeyEvent.VK_SEMICOLON; // shift
            case '\'': return KeyEvent.VK_QUOTE;
            case '"':  return KeyEvent.VK_QUOTE;     // shift
            case '\t': return KeyEvent.VK_TAB;
            case '\n': return KeyEvent.VK_ENTER;
        }
        throw new IllegalArgumentException("No key mapping for character: '" + c + "' (0x"
                + Integer.toHexString(c) + ")");
    }

    // ===== Pause ==================================================================

    /**
     * Blocks the calling thread for {@code ms} milliseconds, scaled by
     * {@link Timings#GLOBAL_SPEED}. Use {@link #rawPause(long)} for waits
     * that must NOT be affected by speed (e.g. polling intervals).
     */
    public static void pause(long ms) {
        rawPause(Timings.scaled(ms));
    }

    /** Blocks the calling thread for {@code ms} milliseconds — never scaled. */
    public static void rawPause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}