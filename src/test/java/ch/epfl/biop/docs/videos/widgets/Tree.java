package ch.epfl.biop.docs.videos.widgets;

import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot-driven primitives for navigating and interacting with a {@link JTree}.
 *
 * <p>Both the SciJava harvester JTree widgets ({@code SwingSourceWidget},
 * {@code SwingSourceListWidget}) and the standalone BigDataViewer-Playground
 * "BDV Sources" frame ({@code SourceTree}) expose exactly the same
 * {@code DefaultMutableTreeNode}-based model — so the same {@code ">"}-delimited
 * path API drives both. {@link Harvester#fillWidget} delegates here for the
 * widget case; a test driver can call directly here to drive the standalone
 * frame.</p>
 *
 * <p>Path semantics (shared with {@link Harvester}):</p>
 * <ul>
 *   <li>Path segments must match {@link DefaultMutableTreeNode#toString()}
 *       <em>exactly</em>, including the root. A loud {@link IllegalArgumentException}
 *       with the available siblings is preferred over silently selecting the
 *       wrong node.</li>
 *   <li>Intermediate nodes are expanded by visible double-click (skipping
 *       nodes already expanded — otherwise a double-click would
 *       <em>collapse</em> them). The final node is single-clicked.</li>
 *   <li>Aiming at a non-leaf is fine: in BDV-Playground tree widgets the
 *       widget recursively pulls all descendant sources from the selection,
 *       so a parent-node click delivers everything beneath it.</li>
 * </ul>
 */
public class Tree {

    /**
     * Navigate {@code tree} along {@code path}, double-clicking each ancestor
     * to expand it (skipping already-expanded ancestors), then single-clicking
     * the final node to select it.
     */
    public static void selectPath(JTree tree, String path) {
        TreePath target = navigateAndExpand(tree, "Sources>"+path);
        clickRow(tree, target, false);
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    /**
     * Navigate to {@code path} (expanding ancestors as needed), left-click to
     * select the final node, then right-click in place so any
     * {@link java.awt.event.MouseListener} on the tree (e.g. the BDV Sources
     * popup) fires against that selection.
     *
     * <p>The right-click does <em>not</em> move the cursor between the select
     * and the right-click — no row layout changes are triggered by a single
     * left-click, so the row is still under the cursor and the visible "click,
     * pause, right-click" reads naturally on video.</p>
     *
     * <p>Caller is responsible for any post-popup gestures (waiting for the
     * popup to be visible, navigating its menu items, dismissing with
     * {@link Ui#escape()}). This method returns immediately after
     * the right-click is dispatched.</p>
     */
    public static void rightClickPath(JTree tree, String path) {
        TreePath target = navigateAndExpand(tree, "Sources>"+path);
        clickRow(tree, target, false);
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
        Ui.rightClick();
        Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
    }

    // ===== Path navigation =======================================================

    /**
     * Walk the tree model along {@code path}, expand every non-leaf ancestor,
     * and return the {@link TreePath} of the final segment.
     *
     * <p>The expansion loop skips ancestors that are already expanded —
     * otherwise a double-click on an open node would <em>collapse</em> it.
     * The final segment is left untouched (single-click is the caller's job).</p>
     */
    static TreePath navigateAndExpand(JTree tree, String path) {
        if (!path.startsWith("Sources>")) {
            path = "Sources>"+path;
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Empty tree path");
        }
        String[] parts = path.split(">");

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        if (root == null) {
            throw new IllegalStateException("Tree has no root");
        }
        if (!parts[0].equals(root.toString())) {
            throw new IllegalArgumentException("Tree root mismatch: expected '"
                    + parts[0] + "', got '" + root.toString() + "'");
        }

        DefaultMutableTreeNode[] nodes = new DefaultMutableTreeNode[parts.length];
        nodes[0] = root;
        for (int i = 1; i < parts.length; i++) {
            DefaultMutableTreeNode child = findChild(nodes[i - 1], parts[i]);
            if (child == null) {
                throw new IllegalArgumentException("Tree path mismatch at segment '"
                        + parts[i] + "': children of '" + nodes[i - 1].toString()
                        + "' are " + childNames(nodes[i - 1]));
            }
            nodes[i] = child;
        }

        TreePath[] ancestorPaths = new TreePath[parts.length];
        ancestorPaths[0] = new TreePath(nodes[0]);
        for (int i = 1; i < parts.length; i++) {
            ancestorPaths[i] = ancestorPaths[i - 1].pathByAddingChild(nodes[i]);
        }

        for (int i = 0; i < parts.length - 1; i++) {
            TreePath ap = ancestorPaths[i];
            if (tree.isExpanded(ap)) continue;
            clickRow(tree, ap, true);
            Ui.pause(Timings.PAUSE_AFTER_TREE_EXPAND_MS);
            if (!tree.isExpanded(ap)) {
                throw new IllegalStateException("Tree path did not expand after double-click: " + ap);
            }
        }

        return ancestorPaths[parts.length - 1];
    }

    private static DefaultMutableTreeNode findChild(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode c = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (name.equals(c.toString())) return c;
        }
        return null;
    }

    private static List<String> childNames(DefaultMutableTreeNode parent) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            names.add(parent.getChildAt(i).toString());
        }
        return names;
    }

    // ===== Row click (visible) ===================================================

    /**
     * Visibly move to and click on the given tree row. {@code doubleClick = true}
     * for expansion gestures, {@code false} for selection.
     *
     * <p>Coords are recomputed every call: an expansion above this row will
     * have shifted its position, and {@link JTree#scrollPathToVisible} may
     * have scrolled the viewport. Both reads happen after the EDT-bound scroll
     * has completed.</p>
     */
    static void clickRow(JTree tree, TreePath path, boolean doubleClick) {
        Point center = rowScreenCenter(tree, path);
        Ui.moveTo(center.x, center.y);
        Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
        if (doubleClick) {
            Ui.doubleClick();
        } else {
            Ui.click();
        }
    }

    /**
     * Absolute screen-center of the row at {@code path}. Scrolls the row into
     * view first (EDT-bound) so {@link JTree#getRowForPath} / {@link JTree#getRowBounds}
     * return non-null values, then converts to absolute coordinates via
     * {@link JTree#getLocationOnScreen}. Used by {@link #clickRow} and by the
     * sorted-list drag-source flow in {@link Harvester}.
     */
    public static Point rowScreenCenter(JTree tree, TreePath path) {
        try {
            SwingUtilities.invokeAndWait(() -> tree.scrollPathToVisible(path));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!tree.isShowing()) {
            throw new IllegalStateException("JTree is not on screen");
        }
        int row = tree.getRowForPath(path);
        if (row < 0) {
            throw new IllegalStateException("Path not visible in tree (row=-1): " + path);
        }
        Rectangle b = tree.getRowBounds(row);
        if (b == null) {
            throw new IllegalStateException("getRowBounds returned null for row " + row);
        }
        Point loc = tree.getLocationOnScreen();
        return new Point(loc.x + b.x + b.width / 2, loc.y + b.y + b.height / 2);
    }
}
