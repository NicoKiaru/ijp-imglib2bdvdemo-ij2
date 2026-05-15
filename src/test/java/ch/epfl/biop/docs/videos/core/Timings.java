package ch.epfl.biop.docs.videos.core;

/**
 * Central timing knobs for tutorial-video automation.
 *
 * Set {@link #GLOBAL_SPEED} once at the top of a script to speed up or slow
 * down every visible motion / pause:
 *   GLOBAL_SPEED = 2.0 → twice as fast
 *   GLOBAL_SPEED = 0.5 → twice as slow
 *
 * Anything passed through {@link #scaled(long)} (or {@link Ui#pause(long)}
 * / {@link Ui#moveTo(int, int, long, int)}) responds to GLOBAL_SPEED.
 * Mechanical timings (click hold, key hold) and polling intervals are kept raw
 * so input stays reliable and polls stay responsive regardless of speed.
 */
public class Timings {

    /** Multiplier on every {@link #scaled(long)} call. >1 = faster, <1 = slower. */
    public static double GLOBAL_SPEED = 0.7;

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

    /**
     * Optional synchronous delay between {@code Robot.mouseWheel} and the
     * subsequent {@code Robot.waitForIdle()} inside
     * {@link Ui#scrollIntoView}. Defaults to {@code 0} —
     * {@code waitForIdle} already calls {@code SunToolkit.flushPendingEvents}
     * which pulls pending native events into AWT, so on Windows this delay
     * is normally unnecessary. Bump it (5–20 ms) only if a slow / loaded
     * machine shows the post-tick {@code viewport.getViewRect()} read
     * racing the EDT (false "scrollbar at limit" exits).
     */
    public static int WHEEL_OS_DISPATCH_MS = 0;

    /**
     * Notches per call to {@code Robot.mouseWheel} inside
     * {@link Ui#scrollIntoView}. Default {@code 5}. A single call
     * sending {@code N} notches still triggers {@code N} individual
     * {@code MouseWheelEvent}s on the EDT (so the scroll still reads as
     * {@code N} small steps to the viewer), but the loop only pays one
     * {@code waitForIdle} round-trip per iteration — at default Swing
     * unit-increment ≈ 3 px / notch, this is the difference between
     * ~13 s and ~250 ms for a 700 px scroll. Tradeoff: max overshoot
     * grows to {@code bundle × pixels-per-notch} (~15 px at default
     * settings) when the target is {@code widgetTop} (it cannot
     * overshoot when the target is clamped to {@code maxScroll}).
     */
    public static int WHEEL_NOTCHES_PER_STEP = 5;

    /**
     * Number of consecutive {@code now.y == prev.y} reads
     * {@link Ui#scrollIntoView} tolerates before treating the
     * scrollbar as saturated and bailing. A larger value is more
     * forgiving of EDT races, at the cost of extra wheel ticks at the
     * actual scroll limit.
     */
    public static int WHEEL_STAGNATION_LIMIT = 3;
    /**
     * Per-click hold inside {@link Ui#doubleClick()}. Must be short
     * enough that press → release → gap → press fits inside the OS double-click
     * threshold (~500 ms on Windows by default), otherwise the OS tags both
     * events as {@code clickCount=1} and Swing never sees a double-click.
     * Stays separate from {@link #CLICK_HOLD_MS} so the leisurely single-click
     * pacing isn't disturbed.
     */
    public static int DOUBLE_CLICK_HOLD_MS = 50;
    public static int DOUBLE_CLICK_GAP_MS = 60;
    public static int KEY_HOLD_MS = 40;
    public static int TYPE_KEY_HOLD_MS = 30;

    /** Pause between mouse-press and drag-start, and between drag-end and release. */
    public static long DRAG_GRAB_DELAY_MS = 120;

    // ===== Typing rhythm (scaled — perceptual) ====================================

    /** Pause between successive characters in {@link Ui#type(String)}. */
    public static long TYPE_INTER_CHAR_MS = 40;

    // ===== Standard pauses around interactions (scaled) ===========================

    public static long PAUSE_AFTER_MOVE_MS = 200;
    public static long PAUSE_AFTER_CLICK_MS = 300;
    public static long PAUSE_AFTER_TYPING_MS = 800;
    public static long PAUSE_AFTER_FRAME_PLACEMENT_MS = 300;

    /** Wait after clicking Browse, to let the file chooser open and gain focus. */
    public static long PAUSE_AFTER_FILE_CHOOSER_OPEN_MS = 800;
    /** Wait after pressing Enter on the folder path inside the file chooser. */
    public static long PAUSE_AFTER_FOLDER_NAV_MS = 500;
    /** Wait after a tree node is double-clicked, to let it expand and rows re-lay-out before re-fetching coords. */
    public static long PAUSE_AFTER_TREE_EXPAND_MS = 350;

    /** Pause between successive mouse-wheel notches when scrolling a widget into view. */
    public static long WHEEL_TICK_PAUSE_MS = 2;

    /**
     * Beat at the end of {@link Ui#scrollIntoView}, between the last
     * wheel notch and whatever gesture follows (typically a click on the
     * just-scrolled widget). Keeps the scroll and the click readable as two
     * distinct steps on tutorial videos rather than blurring into one motion.
     */
    public static long PAUSE_AFTER_SCROLL_MS = 500;

    // ===== Polling / timeouts (NOT scaled) ========================================

    /** Safety cap on the number of wheel notches issued by {@code scrollIntoView}. */
    public static int MAX_WHEEL_TICKS = 800;


    public static long FRAME_POLL_INTERVAL_MS = 100;
    public static long FRAME_WAIT_TIMEOUT_MS  = 7000;
    public static final long FRAME_WAIT_FOR_DIALOG_MS = 2000;

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