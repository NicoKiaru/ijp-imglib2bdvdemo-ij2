package ch.epfl.biop.docs.videos;

/**
 * Central timing knobs for tutorial-video automation.
 *
 * Set {@link #GLOBAL_SPEED} once at the top of a script to speed up or slow
 * down every visible motion / pause:
 *   GLOBAL_SPEED = 2.0 → twice as fast
 *   GLOBAL_SPEED = 0.5 → twice as slow
 *
 * Anything passed through {@link #scaled(long)} (or {@link RobotHelper#pause(long)}
 * / {@link RobotHelper#moveTo(int, int, long, int)}) responds to GLOBAL_SPEED.
 * Mechanical timings (click hold, key hold) and polling intervals are kept raw
 * so input stays reliable and polls stay responsive regardless of speed.
 */
public class Timings {

    /** Multiplier on every {@link #scaled(long)} call. >1 = faster, <1 = slower. */
    public static double GLOBAL_SPEED = 0.33;

    // ===== Mouse motion (scaled) ==================================================

    /**
     * Cursor speed for the no-duration {@code moveTo} overloads, in pixels
     * per millisecond. The duration is derived as {@code distance / speed},
     * then clamped to [{@link #MOUSE_MOVE_MIN_MS}, {@link #MOUSE_MOVE_MAX_MS}],
     * then scaled by {@link #GLOBAL_SPEED}.
     */
    public static double MOUSE_SPEED_PX_PER_MS = 1.5;

    /** Lower / upper clamp on the displacement-derived mouse-move duration (pre-scaling). */
    public static long MOUSE_MOVE_MIN_MS = 150;
    public static long MOUSE_MOVE_MAX_MS = 1500;

    /** Default number of intermediate frames in a smooth {@code moveTo}. */
    public static int  MOUSE_MOVE_FRAMES = 60;

    // ===== Clicks / keys (NOT scaled — mechanical) ================================

    public static int CLICK_HOLD_MS = 600;
    public static int DOUBLE_CLICK_GAP_MS = 60;
    public static int KEY_HOLD_MS = 40;
    public static int TYPE_KEY_HOLD_MS = 30;

    // ===== Typing rhythm (scaled — perceptual) ====================================

    /** Pause between successive characters in {@link RobotHelper#type(String)}. */
    public static long TYPE_INTER_CHAR_MS = 40;

    // ===== Standard pauses around interactions (scaled) ===========================

    public static long PAUSE_AFTER_MOVE_MS = 200;
    public static long PAUSE_AFTER_CLICK_MS = 300;
    public static long PAUSE_AFTER_TYPING_MS = 800;
    public static long PAUSE_AFTER_FRAME_PLACEMENT_MS = 800;

    // ===== Polling / timeouts (NOT scaled) ========================================

    public static long FRAME_POLL_INTERVAL_MS = 100;
    public static long FRAME_WAIT_TIMEOUT_MS  = 5000;

    /**
     * Scale a duration by {@link #GLOBAL_SPEED}. Non-positive inputs return 0;
     * non-positive {@code GLOBAL_SPEED} falls back to the raw value.
     */
    public static long scaled(long ms) {
        if (ms <= 0) return 0;
        if (GLOBAL_SPEED <= 0) return ms;
        return Math.max(0, (long) (ms / GLOBAL_SPEED));
    }

    public static int scaled(int ms) {
        return (int) scaled((long) ms);
    }
}