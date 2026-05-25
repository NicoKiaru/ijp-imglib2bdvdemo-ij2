package ch.epfl.biop.docs.videos.core;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures the human operator's real mouse activity via an AWT global
 * listener and forwards each gesture into {@link Timeline} in the same
 * shape as the existing {@link Ui#click()} / {@link Ui#drag(int,int,int,int)}
 * emissions, with modifier-key state (Shift / Ctrl / Alt / Meta) recorded
 * per event.
 *
 * <p><b>Why.</b> Robot-driven steps already emit one {@code mouse.*} entry
 * per gesture into {@link Timeline}. Manual phases ({@link Step#waitForUser}
 * blocks — e.g. Labkit scribbling and training in
 * {@code GenerateLabkitDocAssets}) emit nothing, so the recorded video has
 * minutes of footage with no corresponding events to overlay. EventRecorder
 * closes that gap: with it on, every clip's {@code events[]} reflects every
 * visible gesture, automated or manual.</p>
 *
 * <p><b>Scope.</b> AWT only — every event delivered to any AWT/Swing window
 * in this JVM (Fiji, BDV, Labkit, dialogs). No JNativeHook; we don't need
 * to capture clicks outside the JVM (browser, OS dialog, native file
 * picker on platforms that bypass AWT).</p>
 *
 * <p><b>Robot vs human.</b> Robot-synthesized events are indistinguishable
 * from real ones at the AWT layer. To avoid double-counting every automated
 * click, each Robot-event-emitting primitive in {@link Ui} brackets its
 * native call with {@link #suppress(boolean)}; the listener early-returns
 * while the depth is non-zero. The {@link Ui} wrappers call
 * {@link java.awt.Robot#waitForIdle()} inside the {@code try} block so the
 * synthetic events have all been dispatched (and skipped) before the flag
 * drops.</p>
 *
 * <p><b>Click dispatch.</b> Click events come from {@link MouseEvent#MOUSE_CLICKED}
 * — which only fires when there is no drag between press and release, so
 * it cleanly separates from the drag branch. A fast double-click yields
 * two CLICKED events ({@code clickCount=1} then {@code 2}); the
 * corresponding output is one {@code mouse.click} followed by one
 * {@code mouse.double_click} (same screen position, within the OS
 * multi-click interval), which the downstream pipeline coalesces if it
 * needs a single marker per double-click gesture. The press / release
 * stream is used only for drag detection, never to emit click events —
 * one event per gesture, never two.</p>
 *
 * <p><b>Drag handling.</b> {@code MOUSE_PRESSED → MOUSE_DRAGGED* →
 * MOUSE_RELEASED} is collapsed into one {@code mouse.drag} event with
 * {@code startX}/{@code startY}, {@code endX}/{@code endY} and a
 * {@code points} count of intermediate samples — matching
 * {@link Ui#drag(int,int,int,int)}'s emission shape but with the extra
 * point count so the consumer can tell a click-with-jitter
 * (~5 points) from a Labkit scribble (~thousands). A drag is only
 * promoted from a click-with-jitter when the cumulative distance from the
 * press exceeds {@link #DRAG_THRESHOLD_PX} on either axis. Per-pixel
 * polyline capture is gated behind {@link #CAPTURE_DRAG_PATH} for a
 * future overlay feature; on by default would swamp {@code timeline.json}
 * (a Labkit scribble = thousands of points).</p>
 *
 * <p><b>Lifecycle.</b> {@link #enable()} installs the listener exactly
 * once and is idempotent. {@link ScreenRecorder#start} auto-arms it when
 * {@link #ENABLED} is true. The listener gates on {@link Step#currentName()}
 * so it only emits inside an open step — outside a step (e.g. while a
 * {@link Step#waitForUser} popup is still showing pre-step, or during
 * teardown between clips) gestures are silently dropped.</p>
 */
public class EventRecorder {

    /**
     * Master switch. {@link #enable()} is a no-op when off. Flip to
     * {@code false} during fast iteration to avoid the global listener
     * overhead — typically only relevant if a script is being profiled,
     * the listener itself is cheap.
     */
    public static boolean ENABLED = true;

    /**
     * Future flag for per-pixel drag-path capture. When true, the listener
     * would store every intermediate {@code MOUSE_DRAGGED} point so the
     * downstream pipeline could render a polyline overlay (handy for
     * Labkit scribble visualisation). Off by default — a single Labkit
     * scribble can be thousands of points, which would swamp
     * {@code timeline.json}. Currently only the {@code points} count is
     * carried in the event payload; the per-pixel path is reserved for a
     * future {@code timeline.json} schema bump.
     */
    public static boolean CAPTURE_DRAG_PATH = false;

    /**
     * Minimum cumulative motion from the press position (on either axis,
     * in screen pixels) before a press → release is promoted from
     * {@code mouse.click} to {@code mouse.drag}. Pure clicks frequently
     * include one or two stray {@code MOUSE_DRAGGED} events that move a
     * handful of pixels; without a threshold those would all be tagged
     * as drags. {@code MOUSE_CLICKED} doesn't fire when this threshold is
     * crossed (Swing's own click-vs-drag classifier kicks in around the
     * same magnitude), so the two paths stay mutually exclusive.
     */
    public static int DRAG_THRESHOLD_PX = 5;

    // Counter (not boolean) so nested Robot sequences don't prematurely
    // re-enable capture if one calls into another. Volatile because the
    // EDT-side listener reads it while the demo thread writes it.
    //
    // The spec called for a ThreadLocal<Boolean> here, but Robot-synthetic
    // events are dispatched on the EDT — a ThreadLocal set on the demo
    // thread is invisible to the EDT and could not actually suppress
    // anything. A plain JVM-wide counter combined with Robot.waitForIdle()
    // in the Ui wrappers (which drains the synthetic events before
    // suppress(false) runs) is the only thing that's correct here.
    private static volatile int suppressDepth;

    private static boolean installed;
    private static AWTEventListener listener;

    // Drag tracking — only touched on the EDT, so no synchronisation
    // needed beyond AWT's own single-threaded event dispatch.
    private static boolean pressActive;
    private static int pressX, pressY;
    private static boolean dragging;
    private static int dragPoints;

    /**
     * Install the global AWT listener. Idempotent — safe to call from
     * every {@link ScreenRecorder#start}. No-op when {@link #ENABLED} is
     * {@code false} at call time.
     */
    public static synchronized void enable() {
        if (installed || !ENABLED) return;
        listener = new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (!ENABLED || suppressDepth > 0) return;
                if (Step.currentName() == null) return;
                try {
                    // MouseWheelEvent extends MouseEvent — check it first.
                    if (event instanceof MouseWheelEvent) {
                        handleWheel((MouseWheelEvent) event);
                    } else if (event instanceof MouseEvent) {
                        handleMouse((MouseEvent) event);
                    }
                    // KeyEvents are listened to (mask requested) but not
                    // processed — modifier state at gesture time is already
                    // carried by each MouseEvent's getModifiersEx().
                } catch (RuntimeException ignored) {
                    // A global AWT listener must never throw — would
                    // poison every subsequent event dispatch in the JVM.
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                | AWTEvent.KEY_EVENT_MASK);
        installed = true;
    }

    /**
     * Bracket a Robot-driven primitive so the listener skips the
     * synthetic events it generates. Counts (nestable) so an inner call
     * doesn't re-enable capture for the outer one. Pair every
     * {@code suppress(true)} with a {@code suppress(false)} in a
     * try/finally; call {@code Robot.waitForIdle()} inside the try
     * before {@code suppress(false)} so the synthetic events have all
     * been dispatched (and skipped) before the flag drops.
     */
    public static void suppress(boolean on) {
        if (on) suppressDepth++;
        else if (suppressDepth > 0) suppressDepth--;
    }

    private static void handleMouse(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                pressActive = true;
                pressX = e.getXOnScreen();
                pressY = e.getYOnScreen();
                dragging = false;
                dragPoints = 0;
                break;
            case MouseEvent.MOUSE_DRAGGED:
                if (pressActive) {
                    int dx = Math.abs(e.getXOnScreen() - pressX);
                    int dy = Math.abs(e.getYOnScreen() - pressY);
                    if (dx >= DRAG_THRESHOLD_PX || dy >= DRAG_THRESHOLD_PX) {
                        dragging = true;
                    }
                    dragPoints++;
                }
                break;
            case MouseEvent.MOUSE_RELEASED:
                if (pressActive && dragging) {
                    Timeline.mouseDragAt(pressX, pressY,
                            e.getXOnScreen(), e.getYOnScreen(),
                            dragPoints, decodeModifiers(e.getModifiersEx()));
                }
                pressActive = false;
                dragging = false;
                dragPoints = 0;
                break;
            case MouseEvent.MOUSE_CLICKED:
                emitClick(e);
                break;
            default:
                // MOUSE_MOVED / MOUSE_ENTERED / MOUSE_EXITED ignored —
                // too noisy for the timeline.
                break;
        }
    }

    private static void emitClick(MouseEvent e) {
        int x = e.getXOnScreen();
        int y = e.getYOnScreen();
        List<String> mods = decodeModifiers(e.getModifiersEx());
        int cc = e.getClickCount();
        int btn = e.getButton();
        if (btn == MouseEvent.BUTTON3) {
            // Right-click: emit once per CLICKED. There is no
            // double-right-click in our event vocabulary.
            if (cc == 1) Timeline.mouseRightClickAt(x, y, mods);
            return;
        }
        if (btn != MouseEvent.BUTTON1) return;
        if (cc == 1) Timeline.mouseClickAt(x, y, mods);
        else if (cc == 2) Timeline.mouseDoubleClickAt(x, y, mods);
        // cc >= 3 ignored — no triple-click in our vocabulary.
    }

    private static void handleWheel(MouseWheelEvent e) {
        Timeline.mouseWheelAt(e.getXOnScreen(), e.getYOnScreen(),
                e.getWheelRotation(), decodeModifiers(e.getModifiersEx()));
    }

    /**
     * Decode {@link InputEvent#getModifiersEx()} into a list of lowercase
     * names — {@code "shift"}, {@code "ctrl"}, {@code "alt"},
     * {@code "meta"}. Empty list when no modifier is held. Uses the modern
     * {@code *_DOWN_MASK} constants (not the deprecated pre-1.4
     * {@code SHIFT_MASK} etc.) since {@code getModifiersEx} only sets the
     * down-mask bits.
     */
    private static List<String> decodeModifiers(int modifiersEx) {
        if (modifiersEx == 0) return Collections.emptyList();
        List<String> mods = null;
        if ((modifiersEx & InputEvent.SHIFT_DOWN_MASK) != 0) {
            (mods = ensure(mods)).add("shift");
        }
        if ((modifiersEx & InputEvent.CTRL_DOWN_MASK) != 0) {
            (mods = ensure(mods)).add("ctrl");
        }
        if ((modifiersEx & InputEvent.ALT_DOWN_MASK) != 0) {
            (mods = ensure(mods)).add("alt");
        }
        if ((modifiersEx & InputEvent.META_DOWN_MASK) != 0) {
            (mods = ensure(mods)).add("meta");
        }
        return mods == null ? Collections.emptyList() : mods;
    }

    private static List<String> ensure(List<String> mods) {
        return mods == null ? new ArrayList<>(4) : mods;
    }
}
