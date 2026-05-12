package ch.epfl.biop.docs.videos.widgets;

import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot-driven navigation of an open Swing {@link JPopupMenu}.
 *
 * <p>Popups don't appear in their parent {@code Window}'s component tree —
 * Swing hosts them in their own (typically heavyweight) {@code Window} and
 * tracks the currently-open menu trail via {@link MenuSelectionManager}.
 * That's how this class finds the active popup after a right-click /
 * menu-bar click: poll {@code MenuSelectionManager.defaultManager().getSelectedPath()}
 * until a visible {@link JPopupMenu} shows up, then walk the
 * {@code ">"}-delimited path through nested submenus.</p>
 *
 * <p>Path semantics (consistent with {@link Tree}):</p>
 * <ul>
 *   <li>Segments must match {@link JMenuItem#getText()} <em>exactly</em>;
 *       loud throw with the list of available items at that level on mismatch.</li>
 *   <li>Intermediate segments must resolve to a {@link JMenu}. The cursor
 *       hovers onto each one — Swing's {@code MenuSelectionManager} auto-opens
 *       the submenu on mouse-enter. We then wait for the sub-popup to actually
 *       become visible before continuing. (Hover, not click — a click on a
 *       {@code JMenu} can <em>toggle</em> the submenu open/closed depending
 *       on selection state, while hover only ever opens.)</li>
 *   <li>The final segment is left-clicked, which fires its action.</li>
 * </ul>
 */
public class Popup {

    /** Max time to wait for the active popup or any submenu to become visible. */
    public static long POPUP_WAIT_TIMEOUT_MS = 3000;

    /** Polling interval while waiting for a popup to show. NOT scaled. */
    public static long POPUP_POLL_INTERVAL_MS = 50;

    /**
     * Find the popup that's currently open (right after a right-click or
     * menu-bar click), then walk {@code path} through it: hover to open each
     * intermediate submenu, click the final menu item to invoke its action.
     *
     * @param path {@code ">"}-delimited menu path, e.g.
     *             {@code "Viewer>BDV>BDV - Show Sources"}
     */
    public static void clickPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Empty popup path");
        }
        String[] parts = path.split(">");

        JPopupMenu popup = waitForActivePopup(POPUP_WAIT_TIMEOUT_MS);
        if (popup == null) {
            throw new IllegalStateException("No active JPopupMenu after "
                    + POPUP_WAIT_TIMEOUT_MS + " ms — has the right-click been issued?");
        }

        for (int i = 0; i < parts.length; i++) {
            JMenuItem item = findMenuItem(popup, parts[i]);
            if (item == null) {
                throw new IllegalArgumentException("Popup path mismatch at segment '"
                        + parts[i] + "': items at this level are " + itemNames(popup));
            }
            moveToItem(item);
            boolean isLast = (i == parts.length - 1);
            if (isLast) {
                Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
                Ui.click();
                Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
            } else {
                if (!(item instanceof JMenu)) {
                    throw new IllegalArgumentException("Popup path segment '"
                            + parts[i] + "' is not a submenu (JMenu) — only the"
                            + " final segment may be a plain JMenuItem");
                }
                JMenu jmenu = (JMenu) item;
                // Hover dwell: Swing auto-opens the submenu on mouse-enter.
                Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
                JPopupMenu sub = jmenu.getPopupMenu();
                if (!waitForPopupShowing(sub, POPUP_WAIT_TIMEOUT_MS)) {
                    throw new IllegalStateException("Submenu '" + parts[i]
                            + "' did not open within " + POPUP_WAIT_TIMEOUT_MS
                            + " ms after hover");
                }
                popup = sub;
            }
        }
    }

    // ===== Lookup ================================================================

    private static JMenuItem findMenuItem(JPopupMenu popup, String text) {
        for (MenuElement me : popup.getSubElements()) {
            if (me instanceof JMenuItem) {
                JMenuItem mi = (JMenuItem) me;
                if (text.equals(mi.getText())) return mi;
            }
        }
        return null;
    }

    private static List<String> itemNames(JPopupMenu popup) {
        List<String> names = new ArrayList<>();
        for (MenuElement me : popup.getSubElements()) {
            if (me instanceof JMenuItem) {
                names.add(((JMenuItem) me).getText());
            }
        }
        return names;
    }

    // ===== Cursor / clicks =======================================================

    /**
     * Smooth-move the cursor to the centre of the menu item. Waits briefly
     * for the item to become on-screen — popups can lag a tick after their
     * parent submenu opens.
     */
    private static void moveToItem(JMenuItem item) {
        long deadline = System.currentTimeMillis() + POPUP_WAIT_TIMEOUT_MS;
        while (!item.isShowing() && System.currentTimeMillis() < deadline) {
            Ui.rawPause(POPUP_POLL_INTERVAL_MS);
        }
        if (!item.isShowing()) {
            throw new IllegalStateException("JMenuItem '" + item.getText()
                    + "' is not on screen after " + POPUP_WAIT_TIMEOUT_MS + " ms");
        }
        Point loc = item.getLocationOnScreen();
        int x = loc.x + item.getWidth() / 2;
        int y = loc.y + item.getHeight() / 2;
        Ui.moveTo(x, y);
    }

    // ===== Popup discovery =======================================================

    /**
     * Poll {@link MenuSelectionManager} for a visible {@link JPopupMenu} on
     * the currently-selected menu path. The first such popup is the root of
     * any open menu trail — exactly what we want right after a right-click.
     */
    private static JPopupMenu waitForActivePopup(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MenuElement[] sel = MenuSelectionManager.defaultManager().getSelectedPath();
            for (MenuElement me : sel) {
                if (me instanceof JPopupMenu && ((JPopupMenu) me).isShowing()) {
                    return (JPopupMenu) me;
                }
            }
            Ui.rawPause(POPUP_POLL_INTERVAL_MS);
        }
        return null;
    }

    private static boolean waitForPopupShowing(JPopupMenu popup, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (popup.isShowing()) return true;
            Ui.rawPause(POPUP_POLL_INTERVAL_MS);
        }
        return false;
    }
}
