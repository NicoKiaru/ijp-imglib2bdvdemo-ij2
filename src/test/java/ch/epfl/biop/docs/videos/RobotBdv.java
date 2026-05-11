package ch.epfl.biop.docs.videos;

import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvHandle;
import bdv.viewer.ViewerPanel;

import javax.swing.JSlider;
import java.awt.Component;
import java.awt.Point;

/**
 * Robot-driven primitives for a BigDataViewer window. Reaches live Swing
 * components through the {@link BdvHandle} rather than aiming at painted
 * regions of {@link ViewerPanel}, so the helpers stay robust across BDV
 * cosmetic changes.
 *
 * <p>Currently exposes a single primitive — {@link #setTimepoint(BdvHandle, int)}
 * — that visibly drags the bottom timepoint slider. More gestures (card-panel
 * toggle, mouse-wheel zoom, …) will land here as they are needed.</p>
 */
public class RobotBdv {

    /**
     * Half the visible thumb width, used to inset the usable track region.
     * 8 px matches the default Metal / FlatLaf horizontal-slider thumb on
     * Windows; tweak if a future LAF paints a wider or narrower thumb.
     */
    private static final int THUMB_HALF_PX = 8;

    /**
     * X-inset (from the right edge of the viewer's display component) where
     * we aim the card-panel arrow click. The trigger region in
     * {@code SplitPaneOneTouchExpandAnimator} is roughly the rightmost
     * {@code arrow-icon-width + 10 px} strip ({@code borderWidth}) at UI
     * scale 1, so any inset {@code < ~20 px} sits safely inside it.
     */
    private static final int CARD_ARROW_RIGHT_INSET_PX = 5;

    /**
     * Pause after the cursor enters the arrow trigger zone, so the
     * {@code SHOW_EXPAND} / {@code SHOW_COLLAPSE} overlay animation can play
     * out and the arrow reads on video before the click lands. Scaled by
     * {@link Timings#GLOBAL_SPEED}.
     */
    private static final int CARD_ARROW_HOVER_PAUSE_MS = 500;

    /**
     * Visibly drag the bottom timepoint slider of {@code bdv} to
     * {@code targetTimepoint}.
     *
     * <p>{@link ViewerPanel} adds its private {@code JSlider sliderTime} at
     * {@code BorderLayout.SOUTH} only when the data has more than one
     * timepoint — calling this on a single-timepoint dataset throws an
     * {@link IllegalStateException}.</p>
     *
     * <p>Current and target thumb x positions come from the slider's screen
     * bounds via simple linear interpolation between {@code minimum} and
     * {@code maximum}, with a small {@link #THUMB_HALF_PX} inset for the
     * usable track region. A pixel or two of error at the ends is fine: the
     * click only needs to land on the thumb, and Swing's drag pipeline
     * finishes the move precisely from the released x.</p>
     */
    public static void setTimepoint(BdvHandle bdv, int targetTimepoint) {
        ViewerPanel vp = bdv.getViewerPanel();
        JSlider slider = findTimepointSlider(vp);
        if (slider == null) {
            throw new IllegalStateException(
                    "BDV viewer has no timepoint slider — single-timepoint dataset?");
        }
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        if (targetTimepoint < min || targetTimepoint > max) {
            throw new IllegalArgumentException(
                    "target timepoint " + targetTimepoint + " is outside ["
                            + min + ", " + max + "]");
        }
        int current = slider.getValue();
        if (current == targetTimepoint) return;

        Point loc = slider.getLocationOnScreen();
        int w = slider.getWidth();
        int h = slider.getHeight();
        int usable = Math.max(1, w - 2 * THUMB_HALF_PX);
        int range = Math.max(1, max - min);
        int currentX = loc.x + THUMB_HALF_PX
                + (int) Math.round((current - min) * (double) usable / range);
        int targetX = loc.x + THUMB_HALF_PX
                + (int) Math.round((targetTimepoint - min) * (double) usable / range);
        int y = loc.y + h / 2;

        RobotHelper.drag(currentX, y, targetX, y);
    }

    /**
     * Visibly expand or collapse the BDV {@link SplitPanel} (the right-hand
     * card panel) by reproducing the natural user gesture: move the cursor
     * over the right edge of the viewer's display component, wait for the
     * one-touch overlay arrow to paint in, and click it.
     *
     * <p>The arrow itself is painted by {@code SplitPaneOneTouchExpandAnimator}
     * — it's not a Swing component, so there's nothing to look up. Instead we
     * aim at the trigger region defined by the animator: the rightmost
     * {@code borderWidth} px strip of the display component, vertically
     * centered with height {@code triggerHeight}. {@code borderWidth} is
     * roughly {@code arrowIconWidth + 10} (≈ 26 px at UI scale 1), so a
     * {@link #CARD_ARROW_RIGHT_INSET_PX} of 5 sits safely inside.</p>
     *
     * <p>Idempotent: if the panel is already in the desired state (per
     * {@link SplitPanel#isCollapsed()}), this is a no-op — no cursor motion,
     * no click. Clicking an already-expanded panel collapses it, so a guard
     * is essential before video drivers run this unconditionally.</p>
     */
    public static void setCardPanelExpanded(BdvHandle bdv, boolean expanded) {
        SplitPanel splitPanel = bdv.getSplitPanel();
        if (splitPanel.isCollapsed() == !expanded) return;

        Component display = bdv.getViewerPanel().getDisplayComponent();
        Point loc = display.getLocationOnScreen();
        int x = loc.x + display.getWidth() - CARD_ARROW_RIGHT_INSET_PX;
        int y = loc.y + display.getHeight() / 2;

        // Move into the trigger zone so the arrow paints in, pause for the
        // show animation, then click — the trigger's mouseClicked handler
        // forwards to splitPanel.setCollapsed(!isCollapsed()).
        RobotHelper.moveTo(x, y);
        RobotHelper.pause(Timings.scaled(CARD_ARROW_HOVER_PAUSE_MS));
        RobotHelper.click();
    }

    /**
     * Find the timepoint {@link JSlider} added by {@link ViewerPanel} at
     * {@code BorderLayout.SOUTH}. Returns {@code null} if the slider is not
     * currently a child of the viewer panel (single-timepoint dataset).
     */
    private static JSlider findTimepointSlider(ViewerPanel vp) {
        for (Component child : vp.getComponents()) {
            if (child instanceof JSlider) return (JSlider) child;
        }
        return null;
    }

    private RobotBdv() { /* static helpers only */ }
}