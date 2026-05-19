package ch.epfl.biop.docs.videos;

import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import ch.epfl.biop.docs.videos.widgets.Harvester;
import ch.epfl.biop.docs.videos.widgets.Popup;
import ch.epfl.biop.docs.videos.widgets.Tree;

import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.scijava.converter.StringToSourceArray;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.JTree;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Builder-style launcher for SciJava commands inside tutorial-video demos.
 *
 * Three execution modes:
 * <ol>
 *   <li>{@link Mode#PROGRAMMATIC} (default) — {@code CommandService.run(cmd, true, args)}.
 *       No Robot, no dialog, no visible motion. Use this while iterating on a demo script
 *       so you don't have to watch every cursor sweep.</li>
 *   <li>{@link Mode#SEARCH} — launch via Fiji's legacy search bar, then drive the harvester
 *       dialog with the Robot (delegates to {@link Harvester#runViaSearch}).</li>
 *   <li>{@link Mode#TREE} — right-click a BDV-Playground source-tree node, walk the popup
 *       to the command's menu path, then drive the harvester dialog.</li>
 * </ol>
 *
 * <p>Set {@link #FORCE_PROGRAMMATIC} to {@code true} at the top of a demo script to
 * override every executor's chosen mode and run everything programmatically — that's
 * the fast-iteration switch. Flip it back to {@code false} when recording.</p>
 *
 * <p>Output capture: only {@link Mode#PROGRAMMATIC} returns a usable
 * {@link Future Future&lt;CommandModule&gt;}. The search-bar and tree-popup launch paths
 * go through legacy plumbing that drops the {@code Future}. {@link #executeAndGet()}
 * therefore requires programmatic mode; {@link #execute()} works for all three.</p>
 *
 * <p>Typical use:</p>
 * <pre>
 * CommandExecutor.of(SingleBdvSourcesShowCommand.class)
 *     .args("adjust_view", true, "make_new_window", true)
 *     .viaTreePath("Sources&gt;Other Sources")
 *     .execute();
 * </pre>
 */
public class CommandExecutor {

    /**
     * Global override: when {@code true}, every {@link #execute()} /
     * {@link #executeAndGet()} call runs in {@link Mode#PROGRAMMATIC} regardless of
     * the per-builder mode selection. One-liner at the top of a demo script to skip
     * the Robot during iteration; set back to {@code false} for recording.
     */
    public static boolean FORCE_PROGRAMMATIC = false;

    public enum Mode { PROGRAMMATIC, SEARCH, TREE }

    /** Tree node clicked by {@link #viaTreePath()} when no path is supplied. */
    private static final String DEFAULT_TREE_ROOT = "";

    /** Parameter name that the BDV-Playground source-tree right-click conventionally feeds. */
    private static final String TREE_SOURCES_PARAM = "sources";

    private final Class<? extends Command> commandClass;
    private Object[] args = new Object[0];
    private Mode mode = Mode.PROGRAMMATIC;
    private String searchQuery;
    private String treePath;
    /**
     * Whether to inject {@code "sources" = treePath} into the command's args in
     * programmatic mode. Set by {@link #viaTreePath(String)} (true) and the
     * no-arg {@link #viaTreePath()} (false) — mirrors the Robot-mode behaviour
     * where right-clicking a specific source node pre-fills the command's
     * {@code sources} parameter, while right-clicking the root does not.
     */
    private boolean treeInjectsSources;
    /**
     * Narration text fired via {@link Step#say(String)} at the moment the
     * command's harvester dialog becomes active — see {@link #sayOnDialog}.
     */
    private String dialogSayText;

    private CommandExecutor(Class<? extends Command> commandClass) {
        this.commandClass = commandClass;
    }

    public static <C extends Command> CommandExecutor of(Class<C> commandClass) {
        return new CommandExecutor(commandClass);
    }

    /**
     * Returns the SciJava-registered menu path of {@code commandClass} as a
     * human-readable {@code " > "}-delimited string — handy for embedding the
     * actual menu route inside narration text without re-deriving it from a
     * {@link CommandService}:
     *
     * <pre>
     * Step.say("Right-clicking the Sources root — we'll walk to: "
     *         + CommandExecutor.menuPath(MyCommand.class));
     * </pre>
     *
     * <p>The full path is returned (including any {@code Plugins} prefix);
     * trim at the call site if a shorter form reads better in a subtitle.</p>
     */
    public static String menuPath(Class<? extends Command> commandClass) {
        return menuPath(commandClass, 0);
    }

    public static String menuPath(Class<? extends Command> commandClass, int iStart) {
        CommandService cs = Fiji.context().getService(CommandService.class);
        MenuPath path = cs.getCommand(commandClass).getMenuPath();
        StringBuilder sb = new StringBuilder();
        for (int i = iStart; i < path.size(); i++) {
            if (i > iStart) sb.append(" > ");
            sb.append(path.get(i).getName());
        }
        return sb.toString();
    }

    /**
     * Set the command inputs as alternating {@code "name", value} pairs — same shape
     * as {@code CommandService.run(...)} and {@link Harvester#run(Class, Object...)}.
     * Replaces any previously-set args.
     */
    public CommandExecutor args(Object... namedInputs) {
        validatePairs(namedInputs);
        this.args = namedInputs;
        return this;
    }

    /** Pure programmatic launch via {@link CommandService} — no Robot, no dialog. (Default.) */
    public CommandExecutor programmatic() {
        this.mode = Mode.PROGRAMMATIC;
        return this;
    }

    /**
     * Narrate the dialog-filling phase: a {@link Step#say(String)} fires the
     * moment the harvester dialog becomes active, so the resulting subtitle
     * spans the time the Robot spends typing into the dialog rather than
     * starting before the menu has even been clicked.
     *
     * <p>Timing per mode:</p>
     * <ul>
     *   <li>{@link Mode#TREE TREE} — fires after the popup-menu walk and the
     *       post-click pause, just as the dialog appears.</li>
     *   <li>{@link Mode#SEARCH SEARCH} — fires after Fiji's search-bar Enter,
     *       just as the dialog appears.</li>
     *   <li>{@link Mode#PROGRAMMATIC PROGRAMMATIC} — fires immediately at
     *       {@link #execute()} (no visible dialog to bracket), so the line
     *       still appears in {@code script.md}.</li>
     * </ul>
     */
    public CommandExecutor sayOnDialog(String text) {
        this.dialogSayText = text;
        return this;
    }

    /**
     * Launch by typing {@code searchQuery} into Fiji's legacy search bar and pressing Enter,
     * then drive the harvester dialog via Robot. {@code searchQuery} should be a short
     * unambiguous prefix of the command's display name (e.g. {@code "robot dem"}).
     */
    public CommandExecutor viaSearch(String searchQuery) {
        this.mode = Mode.SEARCH;
        this.searchQuery = searchQuery;
        return this;
    }

    /**
     * Launch by right-clicking the node at {@code sourceTreePath} in the BDV-Playground
     * "BDV Sources" tree, walking the resulting popup to the command's menu path, then
     * driving the harvester dialog. Path is {@code ">"}-delimited (e.g.
     * {@code "Sources>Other Sources"}); segments must match
     * {@code DefaultMutableTreeNode.toString()} exactly.
     *
     * <p>In Robot/TREE mode the right-click is what pre-fills the command's
     * {@code sources} parameter from the clicked node — by BDV-Playground
     * convention, that parameter is named {@code "sources"}. To preserve the
     * same behaviour in {@link Mode#PROGRAMMATIC PROGRAMMATIC} mode (e.g. under
     * {@link #FORCE_PROGRAMMATIC}), this variant injects
     * {@code "sources" = sourceTreePath} into the args before
     * {@link CommandService#run cs.run(...)}, unless the caller already supplied
     * {@code "sources"} via {@link #args(Object...)} (explicit args always win).
     * Use the no-arg {@link #viaTreePath()} variant for commands that don't
     * consume sources.</p>
     */
    public CommandExecutor viaTreePath(String sourceTreePath) {
        this.mode = Mode.TREE;
        this.treePath = sourceTreePath;
        this.treeInjectsSources = true;
        return this;
    }

    /**
     * Launch via a right-click on the source-tree root ({@code "Sources"}),
     * walking the resulting popup to the command's menu path. Use this for
     * commands that don't consume a {@code sources} parameter (e.g.
     * {@code ViewSynchronizeCommand}, {@code BdvTitleSetCommand}) — the tree
     * click is just a way to discover the menu item, and no arg is injected
     * in {@link Mode#PROGRAMMATIC} mode.
     */
    public CommandExecutor viaTreePath() {
        this.mode = Mode.TREE;
        this.treePath = DEFAULT_TREE_ROOT;
        this.treeInjectsSources = false;
        return this;
    }

    /**
     * Run the command and block until it finishes. Returns no result — use
     * {@link #executeAndGet()} when you need the {@link CommandModule} outputs
     * (only valid in programmatic mode).
     */
    public void execute() {
        Mode effective = effectiveMode();
        switch (effective) {
            case PROGRAMMATIC:
                // No visible dialog to bracket — fire any sayOnDialog text
                // immediately so it still lands in script.md in order.
                if (dialogSayText != null) Step.say(dialogSayText);
                awaitProgrammatic();
                break;
            case SEARCH:
                if (searchQuery == null) {
                    throw new IllegalStateException("viaSearch(...) was not configured");
                }
                Fiji.searchAndRun(searchQuery);
                fireDialogSayWhenReady();
                Harvester.runOpenDialog(commandClass, args);
                break;
            case TREE:
                if (treePath == null) {
                    throw new IllegalStateException("viaTreePath(...) was not configured");
                }
                runViaTreePopup();
                break;
        }
    }

    /**
     * Block until the harvester dialog is on screen, then emit the
     * {@link #sayOnDialog} narration. The subsequent
     * {@link Harvester#runOpenDialog} call's own
     * {@code waitForActiveDialog} returns immediately since the dialog is
     * already active. No-op when {@link #dialogSayText} was never set.
     */
    private void fireDialogSayWhenReady() {
        if (dialogSayText == null) return;
        Ui.waitForActiveDialog(Timings.FRAME_WAIT_FOR_DIALOG_MS);
        Step.say(dialogSayText);
    }

    /**
     * Right-click the source-tree node, walk the resulting popup to the command's
     * menu path, then drive the harvester dialog. Reaches the BDV-Playground source
     * tree via {@code SourceService.tree()} (the same JTree the harvester
     * widget-branch drives), then builds the popup path from the command's
     * {@link MenuPath} — dropping the top-level "Plugins" / "BDV Playground"
     * segments since they aren't present inside the source-tree's context menu.
     */
    private void runViaTreePopup() {
        SourceService bss = Fiji.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree jtree = sourceTree.getJTree();
        Tree.rightClickPath(jtree, treePath);

        CommandService cs = Fiji.context().getService(CommandService.class);
        MenuPath menuPath = cs.getCommand(commandClass).getMenuPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < menuPath.size() - 1; i++) {
            sb.append(menuPath.get(i).getName());
            sb.append(">");
        }
        sb.append(menuPath.getLeaf().getName());
        Popup.clickPath(sb.toString());

        // The command is now running — let it finish painting any UI it spawns
        // (a BDV viewer, in this case) before driving the dialog.
        Ui.pause(Timings.scaled(Timings.PAUSE_AFTER_CLICK_MS));

        fireDialogSayWhenReady();
        Harvester.runOpenDialog(commandClass, args);
    }

    /**
     * Run programmatically and return the {@link Future} from
     * {@link CommandService#run(Class, boolean, Object...)} — call {@code .get()} on it
     * to read outputs via {@link CommandModule#getOutput(String)}.
     *
     * <p>Throws when the effective mode isn't {@link Mode#PROGRAMMATIC}: outputs aren't
     * recoverable from the Robot-driven launch paths (search bar / tree popup). If you
     * need outputs but configured a Robot mode, either switch to {@link #programmatic()}
     * or set {@link #FORCE_PROGRAMMATIC} = true.</p>
     */
    public Future<CommandModule> executeAndGet() {
        Mode effective = effectiveMode();
        if (effective != Mode.PROGRAMMATIC) {
            throw new IllegalStateException(
                    "executeAndGet() requires programmatic mode — outputs are not recoverable "
                            + "when the command is launched via " + effective + ". "
                            + "Use execute(), or call .programmatic() / set FORCE_PROGRAMMATIC = true.");
        }
        return runProgrammatic();
    }

    private Mode effectiveMode() {
        return FORCE_PROGRAMMATIC ? Mode.PROGRAMMATIC : mode;
    }

    private Future<CommandModule> runProgrammatic() {
        CommandService cs = Fiji.context().service(CommandService.class);
        StringToSourceArray s;
        return cs.run(commandClass, true, effectiveArgs());
    }

    /**
     * Args passed to {@code cs.run(...)} in programmatic mode. If this executor
     * was configured via {@link #viaTreePath(String)}, prepend
     * {@code "sources" = treePath} so the command receives the same source
     * context it would get from a tree right-click — unless the caller already
     * supplied {@code "sources"} explicitly via {@link #args(Object...)}, in
     * which case their value is left untouched.
     */
    private Object[] effectiveArgs() {
        if (!treeInjectsSources || treePath == null) return args;
        if (hasNamedArg(TREE_SOURCES_PARAM)) return args;
        Object[] withSources = new Object[args.length + 2];
        withSources[0] = TREE_SOURCES_PARAM;
        withSources[1] = treePath; // Removes Sources
        System.arraycopy(args, 0, withSources, 2, args.length);
        return withSources;
    }

    private boolean hasNamedArg(String name) {
        for (int i = 0; i < args.length; i += 2) {
            if (name.equals(args[i])) return true;
        }
        return false;
    }

    private void awaitProgrammatic() {
        Future<CommandModule> future = runProgrammatic();
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running " + commandClass.getSimpleName(), e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to run " + commandClass.getSimpleName(), e.getCause());
        }
    }

    private static void validatePairs(Object[] namedInputs) {
        if (namedInputs.length % 2 != 0) {
            throw new IllegalArgumentException("args must be alternating name/value pairs");
        }
        // Catch typos early — names must be Strings.
        Map<String, Object> seen = new LinkedHashMap<>();
        for (int i = 0; i < namedInputs.length; i += 2) {
            if (!(namedInputs[i] instanceof String)) {
                throw new IllegalArgumentException("args[" + i + "] must be a String parameter name, got "
                        + (namedInputs[i] == null ? "null" : namedInputs[i].getClass().getName()));
            }
            seen.put((String) namedInputs[i], namedInputs[i + 1]);
        }
    }
}
