package ch.epfl.biop.docs.videos;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Walks the component tree of an AWT/Swing {@link Frame} and produces a flat,
 * order-preserving map of every component encountered.
 *
 * Path keys are hierarchical and human-readable, e.g.:
 *   {@code "ImageJ / [AwtMenu] Plugins / [AwtMenuItem] Macros"}
 *
 * Each entry records the component's local bounds and (when reachable) its
 * absolute screen location — the latter is what the {@link RobotHelper} needs.
 *
 * Caveats:
 *   - AWT {@code MenuBar}/{@code Menu}/{@code MenuItem} (used by IJ1) are
 *     {@code MenuComponent}s, not {@code Component}s, so they DO NOT expose
 *     bounds or screen position. We still record their labels & hierarchy so
 *     they can be referenced and clicked via keyboard/menu mnemonics later.
 *   - Swing {@code JMenuBar}/{@code JMenu}/{@code JMenuItem} ARE components,
 *     so their bounds/screen locations are populated when the menu is realized.
 */
public class ComponentInspector {

    /** Information about a single visited component. */
    public static class ComponentInfo {
        public final String path;
        public final int depth;
        public final Kind kind;
        public final String className;
        public final String label;
        /** Local bounds (relative to parent). {@code null} for AWT menu nodes. */
        public final Rectangle bounds;
        /** Absolute screen location of the top-left corner, or {@code null} if unavailable. */
        public final Point screenLocation;
        public final boolean visible;
        public final boolean enabled;

        public ComponentInfo(String path, int depth, Kind kind, String className,
                             String label, Rectangle bounds, Point screenLocation,
                             boolean visible, boolean enabled) {
            this.path = path;
            this.depth = depth;
            this.kind = kind;
            this.className = className;
            this.label = label;
            this.bounds = bounds;
            this.screenLocation = screenLocation;
            this.visible = visible;
            this.enabled = enabled;
        }

        /** Centre of {@link #screenLocation} + {@link #bounds} (handy for clicks). */
        public Point screenCenter() {
            if (screenLocation == null || bounds == null) return null;
            return new Point(screenLocation.x + bounds.width / 2,
                             screenLocation.y + bounds.height / 2);
        }
    }

    public enum Kind { COMPONENT, SWING_MENU, SWING_MENU_ITEM, AWT_MENU, AWT_MENU_ITEM }

    // ===== Entry point ============================================================

    public static Map<String, ComponentInfo> inspect(Frame frame) {
        Map<String, ComponentInfo> map = new LinkedHashMap<>();
        String rootName = describe(frame, 0);
        putComponent(map, "", rootName, 0, Kind.COMPONENT, frame);
        walkContainer(map, rootName, 1, frame);
        if (frame.getMenuBar() != null) {
            walkAwtMenuBar(map, rootName + " / [AwtMenuBar]", 1, frame.getMenuBar());
        }
        return map;
    }

    /**
     * Same as {@link #inspect(Frame)} but for any {@link Window} (e.g. a
     * {@link Dialog} / {@code JDialog} such as a SciJava command dialog).
     * Windows have no AWT {@code MenuBar}, so only the component tree is walked.
     */
    public static Map<String, ComponentInfo> inspect(Window window) {
        Map<String, ComponentInfo> map = new LinkedHashMap<>();
        String rootName = describe(window, 0);
        putComponent(map, "", rootName, 0, Kind.COMPONENT, window);
        walkContainer(map, rootName, 1, window);
        return map;
    }

    // ===== Walkers ================================================================

    private static void walkContainer(Map<String, ComponentInfo> map, String parentPath,
                                      int depth, Container parent) {
        Component[] children = parent.getComponents();
        for (int i = 0; i < children.length; i++) {
            Component c = children[i];
            String name = describe(c, i);
            String path = parentPath + " / " + name;
            putComponent(map, parentPath, name, depth, Kind.COMPONENT, c);

            if (c instanceof JMenuBar) {
                walkSwingMenuBar(map, path, depth + 1, (JMenuBar) c);
            } else if (c instanceof Container) {
                walkContainer(map, path, depth + 1, (Container) c);
            }
        }
    }

    private static void walkSwingMenuBar(Map<String, ComponentInfo> map, String parentPath,
                                         int depth, JMenuBar bar) {
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null) walkSwingMenu(map, parentPath, depth, menu, i);
        }
    }

    private static void walkSwingMenu(Map<String, ComponentInfo> map, String parentPath,
                                      int depth, JMenu menu, int siblingIndex) {
        String name = "[Menu] " + safe(menu.getText(), siblingIndex);
        String path = parentPath + " / " + name;
        putComponent(map, parentPath, name, depth, Kind.SWING_MENU, menu);
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            Component child = menu.getMenuComponent(i);
            if (child instanceof JMenu) {
                walkSwingMenu(map, path, depth + 1, (JMenu) child, i);
            } else {
                String itemLabel = (child instanceof JMenuItem)
                        ? safe(((JMenuItem) child).getText(), i)
                        : child.getClass().getSimpleName() + "[" + i + "]";
                String itemName = "[Item] " + itemLabel;
                putComponent(map, path, itemName, depth + 1, Kind.SWING_MENU_ITEM, child);
            }
        }
    }

    private static void walkAwtMenuBar(Map<String, ComponentInfo> map, String parentPath,
                                       int depth, MenuBar bar) {
        // Record the bar itself as a placeholder (no bounds available).
        map.put(parentPath, new ComponentInfo(parentPath, depth, Kind.AWT_MENU,
                bar.getClass().getName(), "MenuBar", null, null, true, true));
        for (int i = 0; i < bar.getMenuCount(); i++) {
            walkAwtMenu(map, parentPath, depth + 1, bar.getMenu(i), i);
        }
    }

    private static void walkAwtMenu(Map<String, ComponentInfo> map, String parentPath,
                                    int depth, Menu menu, int siblingIndex) {
        String name = "[AwtMenu] " + safe(menu.getLabel(), siblingIndex);
        String path = parentPath + " / " + name;
        map.put(path, new ComponentInfo(path, depth, Kind.AWT_MENU,
                menu.getClass().getName(), menu.getLabel(),
                null, null, true, menu.isEnabled()));
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem mi = menu.getItem(i);
            if (mi instanceof Menu) {
                walkAwtMenu(map, path, depth + 1, (Menu) mi, i);
            } else {
                String itemPath = path + " / [AwtMenuItem] " + safe(mi.getLabel(), i);
                map.put(itemPath, new ComponentInfo(itemPath, depth + 1, Kind.AWT_MENU_ITEM,
                        mi.getClass().getName(), mi.getLabel(),
                        null, null, true, mi.isEnabled()));
            }
        }
    }

    // ===== Helpers ================================================================

    private static void putComponent(Map<String, ComponentInfo> map, String parentPath,
                                     String name, int depth, Kind kind, Component c) {
        String path = parentPath.isEmpty() ? name : parentPath + " / " + name;
        Rectangle bounds = c.getBounds();
        Point screenLoc = null;
        if (c.isShowing()) {
            try { screenLoc = c.getLocationOnScreen(); } catch (Exception ignored) { /* not on screen */ }
        }
        map.put(path, new ComponentInfo(path, depth, kind,
                c.getClass().getName(), extractLabel(c),
                bounds, screenLoc, c.isVisible(), c.isEnabled()));
    }

    private static String describe(Component c, int siblingIndex) {
        String simple = c.getClass().getSimpleName();
        if (simple.isEmpty()) simple = c.getClass().getName();
        String label = extractLabel(c);
        if (label != null && !label.isEmpty() && label.length() <= 40) {
            return simple + "[" + siblingIndex + "] '" + label + "'";
        }
        return simple + "[" + siblingIndex + "]";
    }

    private static String extractLabel(Component c) {
        if (c instanceof Frame) return ((Frame) c).getTitle();
        if (c instanceof Dialog) return ((Dialog) c).getTitle();
        if (c instanceof AbstractButton) return ((AbstractButton) c).getText();
        if (c instanceof JLabel) return ((JLabel) c).getText();
        if (c.getName() != null) return c.getName();
        return null;
    }

    private static String safe(String s, int siblingIndex) {
        return (s == null || s.isEmpty()) ? "<#" + siblingIndex + ">" : s;
    }

    // ===== Pretty printing ========================================================

    /**
     * Renders the map as an indented tree, one component per line, with class,
     * local bounds and (if available) screen position.
     */
    public static String prettyPrint(Map<String, ComponentInfo> map) {
        StringBuilder sb = new StringBuilder();
        for (ComponentInfo info : map.values()) {
            for (int i = 0; i < info.depth; i++) sb.append("  ");
            sb.append(lastSegment(info.path));
            sb.append("  | ").append(simpleName(info.className));
            if (info.bounds != null) {
                sb.append(" bounds=(").append(info.bounds.x).append(",").append(info.bounds.y)
                        .append(" ").append(info.bounds.width).append("x").append(info.bounds.height)
                        .append(")");
            }
            if (info.screenLocation != null) {
                sb.append(" screen=(").append(info.screenLocation.x).append(",")
                        .append(info.screenLocation.y).append(")");
            }
            if (!info.visible) sb.append(" [HIDDEN]");
            if (!info.enabled) sb.append(" [DISABLED]");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String lastSegment(String path) {
        int idx = path.lastIndexOf(" / ");
        return idx >= 0 ? path.substring(idx + 3) : path;
    }

    private static String simpleName(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }
}