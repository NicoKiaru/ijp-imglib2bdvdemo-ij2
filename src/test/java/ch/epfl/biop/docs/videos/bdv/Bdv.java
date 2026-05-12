package ch.epfl.biop.docs.videos.bdv;

import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.widgets.Harvester;

import bdv.ui.BdvDefaultCards;
import bdv.ui.splitpanel.SplitPanel;
import bdv.ui.sourcetable.SourceTable;
import bdv.ui.sourcetable.SourceTableModel;
import bdv.util.BdvHandle;
import bdv.viewer.ViewerPanel;

import javax.swing.JFormattedTextField;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot-driven primitives for a BigDataViewer window. Mixes two location
 * strategies: Swing-component lookup via the {@link BdvHandle} when a real
 * component exists ({@link #setTimepoint setTimepoint},
 * {@link #selectSourceInCard selectSourceInCard},
 * {@link #setDisplayRange setDisplayRange}), and pixel-aim at the display
 * component's trigger region when the control is only overlay-painted
 * ({@link #setCardPanelExpanded setCardPanelExpanded}).
 */
public class Bdv {

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

        Ui.drag(currentX, y, targetX, y);
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
        Ui.moveTo(x, y);
        Ui.pause(Timings.scaled(CARD_ARROW_HOVER_PAUSE_MS));
        Ui.click();
    }

    /**
     * Visibly click a row in the BDV "Sources" card's {@link SourceTable} to
     * select that source. The row is matched by exact
     * {@link SourceTableModel#NAME_COLUMN} value (which echoes
     * {@code SourceModel.getName()} — for sources opened via bdv-img-bioformats
     * that's typically {@code "<dataset>-<series>-<channel>"}).
     *
     * <p>Preconditions guarded here: the {@link SplitPanel} must be expanded
     * (call {@link #setCardPanelExpanded setCardPanelExpanded} first — a loud
     * throw beats silently expanding the panel and hiding the gesture from the
     * recorded video), and the "Sources" card must be expanded. The card
     * defaults to expanded, but we ensure it programmatically via
     * {@code cardPanel.setCardExpanded(DEFAULT_SOURCES_CARD, true)} so a
     * previously-collapsed card doesn't throw off the click.</p>
     *
     * <p>The click lands in the centre of the {@link SourceTableModel#NAME_COLUMN}
     * cell — the only non-editable column ({@code COLOR_COLUMN} opens a colour
     * picker, {@code IS_ACTIVE_COLUMN} / {@code IS_CURRENT_COLUMN} toggle, so a
     * misaimed click would have side effects). {@link javax.swing.JTable#scrollRectToVisible}
     * is called programmatically before the click so the row sits inside the
     * scroll pane's viewport — visible mouse-wheel scrolling of the sources
     * card isn't supported yet, but tutorial datasets rarely have enough
     * sources to need it. Loud throw with the available names on a typo.</p>
     */
    public static void selectSourceInCard(BdvHandle bdv, String sourceName) {
        ensureSourcesCardReady(bdv);

        SourceTable table = findSourceTable(bdv);
        if (table == null) {
            throw new IllegalStateException(
                    "could not locate the Sources card's SourceTable inside the CardPanel");
        }

        int row = findRowByName(table, sourceName);
        if (row < 0) {
            throw new IllegalArgumentException(
                    "no source named '" + sourceName + "' in the Sources card — available: "
                            + listSourceNames(table));
        }

        Rectangle cell = table.getCellRect(row, SourceTableModel.NAME_COLUMN, true);
        table.scrollRectToVisible(cell);
        cell = table.getCellRect(row, SourceTableModel.NAME_COLUMN, true);

        Point tableLoc = table.getLocationOnScreen();
        int x = tableLoc.x + cell.x + cell.width / 2;
        int y = tableLoc.y + cell.y + cell.height / 2;

        Ui.moveTo(x, y);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        Ui.click();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Visibly drive the min/max spinners of the {@code BoundedRangePanel}
     * inside the "Sources" card to set the display range of the currently
     * selected source(s). Call {@link #selectSourceInCard selectSourceInCard}
     * first to choose what the range edit applies to.
     *
     * <p>Geometry: walks the unique {@link SourceTable} up three parents
     * ({@code viewport → scrollPane → tablePanel}) to reach the
     * {@code BorderLayout}-laid {@code tablePanel} from
     * {@code BdvDefaultCards.setup}, grabs the {@code BorderLayout.SOUTH} child
     * (the {@code ConverterSetupEditPanel}), and collects all {@link JSpinner}s
     * inside — exactly the min and max spinners of {@code BoundedRangePanel} in
     * document order. {@code BoundedRangePanel} itself is package-private in
     * {@code bdv.ui.convertersetupeditor}, so we can't import its class; this
     * structural walk avoids the visibility issue.</p>
     *
     * <p>Spinner-order note: {@code BoundedRange.withMin} and
     * {@code BoundedRange.withMax} both expand-to-accommodate (setting
     * {@code min > max} drags {@code max} up, and vice versa), so we can set
     * the two spinners in either order and the second one's value wins. We set
     * {@code min} first then {@code max} for readability on video.</p>
     *
     * <p>Each spinner is filled with the same primitive the harvester uses
     * ({@link Harvester#fillNumberField}): click the {@link JFormattedTextField}
     * inside the spinner editor, select-all, paste {@code String.valueOf(value)},
     * Enter. Paste (not type) for layout-independence; Enter is load-bearing
     * because a {@code JFormattedTextField} only commits on Enter or focus
     * loss.</p>
     */
    public static void setDisplayRange(BdvHandle bdv, double min, double max) {
        ensureSourcesCardReady(bdv);

        SourceTable table = findSourceTable(bdv);
        if (table == null) {
            throw new IllegalStateException(
                    "could not locate the Sources card's SourceTable inside the CardPanel");
        }

        // table -> viewport -> scrollPane -> tablePanel (BorderLayout from BdvDefaultCards)
        Container tablePanel = table.getParent().getParent().getParent();
        if (!(tablePanel.getLayout() instanceof BorderLayout)) {
            throw new IllegalStateException(
                    "unexpected Sources card structure — table-panel layout is "
                            + tablePanel.getLayout().getClass().getSimpleName()
                            + ", expected BorderLayout");
        }
        Component editPanel = ((BorderLayout) tablePanel.getLayout())
                .getLayoutComponent(BorderLayout.SOUTH);
        if (!(editPanel instanceof Container)) {
            throw new IllegalStateException(
                    "no ConverterSetupEditPanel at SOUTH of the Sources card's table panel");
        }

        List<JSpinner> spinners = new ArrayList<>();
        collectOfType((Container) editPanel, JSpinner.class, spinners);
        if (spinners.size() < 2) {
            throw new IllegalStateException(
                    "expected 2 JSpinners (min, max) in BoundedRangePanel, found "
                            + spinners.size());
        }
        fillSpinner(spinners.get(0), min);
        fillSpinner(spinners.get(1), max);
    }

    /**
     * Guard run before every "Sources" card interaction: the split panel must
     * already be expanded (loud throw if not — the caller should expand it
     * visibly via {@link #setCardPanelExpanded setCardPanelExpanded} as a
     * tutorial-video gesture), and the "Sources" card itself is ensured
     * programmatically (default-expanded, so usually a no-op).
     */
    private static void ensureSourcesCardReady(BdvHandle bdv) {
        if (bdv.getSplitPanel().isCollapsed()) {
            throw new IllegalStateException(
                    "BDV split panel is collapsed — call Bdv.setCardPanelExpanded(bdv, true) first");
        }
        bdv.getCardPanel().setCardExpanded(BdvDefaultCards.DEFAULT_SOURCES_CARD, true);
    }

    private static SourceTable findSourceTable(BdvHandle bdv) {
        Container root = (Container) bdv.getCardPanel().getComponent();
        return firstOfType(root, SourceTable.class);
    }

    private static int findRowByName(SourceTable table, String name) {
        for (int i = 0; i < table.getRowCount(); i++) {
            Object v = table.getValueAt(i, SourceTableModel.NAME_COLUMN);
            if (name.equals(String.valueOf(v))) return i;
        }
        return -1;
    }

    private static List<String> listSourceNames(SourceTable table) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            names.add(String.valueOf(table.getValueAt(i, SourceTableModel.NAME_COLUMN)));
        }
        return names;
    }

    private static void fillSpinner(JSpinner spinner, double value) {
        Container editor = (Container) spinner.getEditor();
        JFormattedTextField field = firstOfType(editor, JFormattedTextField.class);
        if (field == null) {
            throw new IllegalStateException(
                    "JSpinner editor has no JFormattedTextField — non-standard editor?");
        }
        Harvester.typeNumberField(field, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T firstOfType(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return (T) child;
            if (child instanceof Container) {
                T found = firstOfType((Container) child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> void collectOfType(Container root, Class<T> type, List<T> into) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) into.add((T) child);
            if (child instanceof Container) {
                collectOfType((Container) child, type, into);
            }
        }
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

    private Bdv() { /* static helpers only */ }
}