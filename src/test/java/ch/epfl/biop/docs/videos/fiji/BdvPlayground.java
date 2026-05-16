package ch.epfl.biop.docs.videos.fiji;

import ch.epfl.biop.docs.videos.widgets.Tree;
import sc.fiji.bdvpg.scijava.service.SourceService;

import javax.swing.JTree;

/**
 * High-level BDV-Playground workflows on top of {@link Fiji} and the generic
 * {@code widgets/*} primitives. Lives in {@code fiji/} so it can depend on
 * SciJava and BDV-Playground services that the layered {@code widgets/} package
 * must not see.
 */
public class BdvPlayground {

    /**
     * Visibly double-clicks the BDV-Playground "BDV Sources" tree node at
     * {@code path}, which fires {@code SourceTree.adjustViewOnSelectedSources()}
     * and re-frames the currently active BDV window onto the source(s) under
     * {@code path}. Path semantics are the same as the other {@link Tree}
     * methods: {@code ">"}-delimited, exact-match, with an implicit leading
     * {@code "Sources>"} the caller may omit.
     *
     * <p>Aiming at a parent node (e.g. a dataset name) flattens to all
     * descendant sources, so one call re-frames the active BDV on every source
     * under that node. Requires {@code Fiji.startFiji()} to have been called.
     */
    public static void adjustViewOnSource(String path) {
        SourceService bss = Fiji.context().service(SourceService.class);
        JTree jtree = bss.tree().getJTree();
        Tree.doubleClickPath(jtree, path);
    }
}