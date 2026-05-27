package ch.epfl.biop.docs.videos;

import ch.epfl.biop.docs.videos.core.GroovyScript;
import ch.epfl.biop.docs.videos.core.Step;
import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import ch.epfl.biop.docs.videos.widgets.Harvester;
import ch.epfl.biop.docs.videos.widgets.Popup;
import ch.epfl.biop.docs.videos.widgets.Tree;

import java.io.File;

import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.scijava.converter.StringToSourceArray;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.JTree;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 *     .arg("adjust_view", true, "Re-centering the view on the loaded sources.")
 *     .arg("make_new_window", true, "Open a fresh BDV window rather than reusing the active one.")
 *     .viaTreePath("Sources&gt;Other Sources")
 *     .execute();
 * </pre>
 *
 * <p>Each {@link #arg(String, Object, String)} carries an optional narration:
 * {@link Harvester} fires {@link Step#say} with that text just before the
 * matching field is driven, so the subtitle's {@code tMs} in
 * {@code timeline.json} lines up with the moment the widget is filled. Use
 * the 2-arg {@link #arg(String, Object)} for boilerplate parameters where
 * a subtitle would just add noise.</p>
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
    private final List<ArgEntry> argList = new ArrayList<>();
    private Mode mode = Mode.PROGRAMMATIC;
    private String searchQuery;
    private String treePath;
    /**
     * Multi-path variant set by {@link #viaTreePaths(String[], String)}.
     * Mutually exclusive with {@link #treePath} — whichever was set last wins.
     * In {@link Mode#TREE TREE} mode the Robot does a Ctrl+click select of every
     * path before the right-click; in {@link Mode#PROGRAMMATIC PROGRAMMATIC} the
     * whole array is injected as {@code "sources" = treePaths} and resolved by
     * {@code StringArrayToSourceArray} on the bdvpg side.
     */
    private String[] treePaths;
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
    /**
     * Narration text fired via {@link Step#say(String)} just before the
     * source-tree right-click in {@link Mode#TREE TREE} mode — see
     * {@link #viaTreePath(String, String)}.
     */
    private String treeClickNarration;

    /**
     * Set to {@code true} via {@link #skipInScript()} to suppress this
     * executor's auto-emitted line in the generated {@code script.groovy}.
     * Use it for visualization-only commands (showing sources in BDV, naming
     * a viewer, view-sync) that don't belong in the headless transcript.
     */
    private boolean skipInScript;

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
     * Append one command input with no narration. Use this for boilerplate
     * parameters where a per-field subtitle would be noise (e.g. flags the
     * tutorial doesn't talk about). Order matters: the {@link Harvester}
     * fills parameters in insertion order, so {@code arg(...)} calls
     * determine the on-screen filling order.
     */
    public CommandExecutor arg(String name, Object value) {
        return arg(name, value, null);
    }

    /**
     * Append one command input with a per-field narration. The narration is
     * fired via {@link Step#say} from inside {@link Harvester#runOpenDialog}
     * just before this field is driven — so the resulting {@code comments[]}
     * entry in {@code timeline.json} has a {@code tMs} that lines up with
     * the moment the corresponding widget is filled, letting the downstream
     * video pipeline pace explanation vs. action precisely.
     */
    public CommandExecutor arg(String name, Object value, String narration) {
        if (name == null) {
            throw new IllegalArgumentException("arg name must not be null");
        }
        argList.add(new ArgEntry(name, value, narration));
        return this;
    }

    /**
     * Bulk form of {@link #arg(String, Object)} taking alternating
     * {@code "name", value} pairs — same shape as {@code CommandService.run(...)}.
     * Replaces any previously-set args and never attaches narration. Prefer
     * the per-arg {@link #arg(String, Object, String)} form so each field can
     * carry its own subtitle.
     *
     * @deprecated use {@link #arg(String, Object)} / {@link #arg(String, Object, String)}.
     */
    @Deprecated
    public CommandExecutor args(Object... namedInputs) {
        validatePairs(namedInputs);
        argList.clear();
        for (int i = 0; i < namedInputs.length; i += 2) {
            argList.add(new ArgEntry((String) namedInputs[i], namedInputs[i + 1], null));
        }
        return this;
    }

    /** Pure programmatic launch via {@link CommandService} — no Robot, no dialog. (Default.) */
    public CommandExecutor programmatic() {
        this.mode = Mode.PROGRAMMATIC;
        return this;
    }

    /**
     * Suppress this command's auto-emitted line in the session-level
     * {@code script.groovy} transcript. Use it for visualization-only
     * commands (showing sources in BDV, naming a viewer, view-sync) that
     * belong in the recorded video but not in the headless script a viewer
     * runs after watching it.
     */
    public CommandExecutor skipInScript() {
        this.skipInScript = true;
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
     *       still appears in {@code timeline.json}.</li>
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
     * {@code "sources"} via {@link #arg(String, Object)} (explicit args always win).
     * The injection is for programmatic mode only — in TREE mode the dialog has
     * no {@code sources} widget, so passing it to {@link Harvester} would throw
     * "no input container found for parameter 'sources'".
     * Use the no-arg {@link #viaTreePath()} variant for commands that don't
     * consume sources.</p>
     */
    public CommandExecutor viaTreePath(String sourceTreePath) {
        return viaTreePath(sourceTreePath, null);
    }

    /**
     * Same as {@link #viaTreePath(String)} but with a narration line fired via
     * {@link Step#say(String)} just before the right-click — so the subtitle's
     * {@code tMs} in {@code timeline.json} lines up with the source-selection
     * gesture. The right-click on a specific source node <em>is</em> the
     * "sources" parameter assignment in TREE mode, so this is the
     * {@code arg(name, value, narration)} equivalent for that implicit
     * parameter.
     *
     * <p>Pass {@code null} for {@code narration} to skip the {@code Step.say}
     * call — equivalent to {@link #viaTreePath(String)}.</p>
     */
    public CommandExecutor viaTreePath(String sourceTreePath, String narration) {
        this.mode = Mode.TREE;
        this.treePath = sourceTreePath;
        this.treePaths = null;
        this.treeInjectsSources = true;
        this.treeClickNarration = narration;
        return this;
    }

    /**
     * Multi-path variant of {@link #viaTreePath(String)}: select several source
     * nodes with a Ctrl+click pattern and then right-click in place to open the
     * command's menu against the full selection. The visible gesture is
     * "click first → Ctrl+click rest → right-click" on the BDV-Playground
     * source tree — see {@link Tree#rightClickPaths} for the details.
     *
     * <p>In {@link Mode#PROGRAMMATIC PROGRAMMATIC} mode (e.g. under
     * {@link #FORCE_PROGRAMMATIC}) this injects {@code "sources" = paths} (a
     * {@code String[]}) into the args, relying on the bdvpg
     * {@code StringArrayToSourceArray} converter to resolve each path against
     * the {@code SourceService} tree and flatten the result. The injection is
     * skipped when the caller already supplied {@code "sources"} via
     * {@link #arg(String, Object)} — explicit args always win.</p>
     */
    public CommandExecutor viaTreePaths(String[] paths) {
        return viaTreePaths(paths, null);
    }

    /**
     * Same as {@link #viaTreePaths(String[])} but with a narration line fired
     * via {@link Step#say(String)} just before the first left-click — so the
     * subtitle's {@code tMs} in {@code timeline.json} lines up with the
     * source-selection gesture. Pass {@code null} for {@code narration} to
     * skip the {@code Step.say} call.
     */
    public CommandExecutor viaTreePaths(String[] paths, String narration) {
        if (paths == null || paths.length == 0) {
            throw new IllegalArgumentException("viaTreePaths requires at least one path");
        }
        this.mode = Mode.TREE;
        this.treePaths = paths;
        this.treePath = null;
        this.treeInjectsSources = true;
        this.treeClickNarration = narration;
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
        this.treePaths = null;
        this.treeInjectsSources = false;
        return this;
    }

    /**
     * Run the command and block until it finishes. Returns no result — use
     * {@link #executeAndGet()} when you need the {@link CommandModule} outputs
     * (only valid in programmatic mode).
     */
    public void execute() {
        emitGroovy();
        Mode effective = effectiveMode();
        switch (effective) {
            case PROGRAMMATIC:
                // No visible dialog to bracket — fire any sayOnDialog text
                // immediately so it still lands in timeline.json in order.
                if (dialogSayText != null) Step.say(dialogSayText);
                awaitProgrammatic();
                break;
            case SEARCH:
                if (searchQuery == null) {
                    throw new IllegalStateException("viaSearch(...) was not configured");
                }
                Fiji.searchAndRun(searchQuery);
                fireDialogSayWhenReady();
                Harvester.runOpenDialog(commandClass, narrationsMap(), harvesterArgs());
                break;
            case TREE:
                if (treePath == null && treePaths == null) {
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
        // Fire the source-selection narration before the right-click, so its
        // tMs in timeline.json lines up with the gesture itself. The right-
        // click on a specific node is the "sources" parameter assignment in
        // TREE mode — there is no harvester widget to attach narration to.
        if (treeClickNarration != null) Step.say(treeClickNarration);
        if (treePaths != null) {
            Tree.rightClickPaths(jtree, treePaths);
        } else {
            Tree.rightClickPath(jtree, treePath);
        }

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
        Harvester.runOpenDialog(commandClass, narrationsMap(), harvesterArgs());
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
        emitGroovy();
        return runProgrammatic();
    }

    /**
     * Emit this command's {@code cs.run(...)} equivalent into the session
     * {@link GroovyScript} transcript without launching the command. Same
     * effect the auto-emitter applies inside {@link #execute()}, but exposed
     * so a dry-run pass can build a {@code script.groovy} without spinning
     * up Fiji — useful for tests and CI lint of the generated script.
     */
    public void previewToScript() {
        emitGroovy();
    }

    /**
     * Emit this command's {@code cs.run(...)} equivalent into the session
     * {@link GroovyScript} transcript. No-op when {@link #skipInScript} is
     * set or when {@link GroovyScript#ENABLED} is off. The same set of
     * inputs that {@link #programmaticArgs()} passes to
     * {@link CommandService#run(Class, boolean, Object...)} is rendered, so
     * the script preserves the tree-injected {@code "sources"} arg.
     */
    private void emitGroovy() {
        if (skipInScript || !GroovyScript.ENABLED) return;
        GroovyScript.addImport(commandClass.getName());
        Object[] effective = programmaticArgs();
        boolean needsFileImport = false;
        int argCount = effective.length / 2;
        StringBuilder sb = new StringBuilder();
        sb.append("cs.run(").append(commandClass.getSimpleName()).append(".class, true");
        if (argCount > 0) sb.append(",\n");
        for (int i = 0; i < effective.length; i += 2) {
            int idx = i / 2;
            boolean isLast = (idx == argCount - 1);
            String name = (String) effective[i];
            Object value = effective[i + 1];
            if (value instanceof File || value instanceof File[]) needsFileImport = true;
            String narration = narrationFor(name);
            // Order matters: comma must come before any `//` so it isn't
            // swallowed by Groovy's end-of-line comment.
            sb.append("    \"").append(name).append("\", ").append(renderGroovyLiteral(value));
            if (!isLast) sb.append(",");
            if (narration != null) sb.append("  // ").append(oneLine(narration));
            sb.append("\n");
        }
        sb.append(").get()");
        if (needsFileImport) GroovyScript.addImport("java.io.File");
        GroovyScript.addLine(sb.toString());
    }

    /** Lookup the per-arg narration (if any) for emitting an inline {@code // comment}. */
    private String narrationFor(String name) {
        for (ArgEntry e : argList) {
            if (name.equals(e.key)) return e.narration;
        }
        return null;
    }

    /** Collapse multi-line narration to a single line for inline comments. */
    private static String oneLine(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /**
     * Render {@code value} as a Groovy literal suitable for splicing into a
     * {@code cs.run(...)} call. Files are hoisted to top-level
     * {@code #@File} params via {@link GroovyScript#fileParamFor(String)} so
     * the same physical file becomes one editable parameter no matter how
     * many commands reference it.
     */
    private static String renderGroovyLiteral(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escape((String) value) + "\"";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Integer) return value.toString();
        if (value instanceof Long) return value + "L";
        if (value instanceof Float) return value + "f";
        if (value instanceof Double) return value + "d";
        if (value instanceof File) {
            return GroovyScript.fileParamFor(((File) value).getAbsolutePath());
        }
        if (value instanceof File[]) {
            File[] arr = (File[]) value;
            StringBuilder sb = new StringBuilder("new File[]{");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(GroovyScript.fileParamFor(arr[i].getAbsolutePath()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof String[]) {
            String[] arr = (String[]) value;
            StringBuilder sb = new StringBuilder("new String[]{");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escape(arr[i])).append("\"");
            }
            sb.append("}");
            return sb.toString();
        }
        // Fallback: toString() with a TODO so a reader knows to revisit.
        return value + " /* TODO: review unsupported type "
                + value.getClass().getSimpleName() + " */";
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    private Mode effectiveMode() {
        return FORCE_PROGRAMMATIC ? Mode.PROGRAMMATIC : mode;
    }

    private Future<CommandModule> runProgrammatic() {
        CommandService cs = Fiji.context().service(CommandService.class);
        return cs.run(commandClass, true, programmaticArgs());
    }

    /**
     * Args as a flat {@code [name, value, name, value, …]} array in
     * {@link #arg(String, Object) arg(...)} insertion order — exactly the
     * args the caller supplied. Used to drive the {@link Harvester} dialog
     * in {@link Mode#SEARCH SEARCH} / {@link Mode#TREE TREE} modes, where
     * each entry must map to a real widget in the dialog.
     *
     * <p>In TREE mode the right-click on the source-tree node already
     * pre-fills the command's {@code "sources"} parameter — and the dialog
     * is rendered <em>without</em> a {@code sources} widget — so injecting
     * a {@code "sources"} entry here would make {@link Harvester} throw
     * "no input container found for parameter 'sources'". The injection
     * is therefore confined to {@link #programmaticArgs()}.</p>
     */
    private Object[] harvesterArgs() {
        Object[] out = new Object[argList.size() * 2];
        int i = 0;
        for (ArgEntry e : argList) {
            out[i++] = e.key;
            out[i++] = e.value;
        }
        return out;
    }

    /**
     * Args passed to {@code cs.run(...)} in {@link Mode#PROGRAMMATIC} mode.
     * Same as {@link #harvesterArgs()}, but if this executor was configured
     * via {@link #viaTreePath(String)} the {@code "sources" = treePath} pair
     * is prepended so the command receives the same source context it would
     * get from a tree right-click. Skipped when the caller already supplied
     * {@code "sources"} explicitly (their value wins).
     */
    private Object[] programmaticArgs() {
        boolean injectSources = treeInjectsSources
                && (treePath != null || treePaths != null)
                && !hasNamedArg(TREE_SOURCES_PARAM);
        if (!injectSources) return harvesterArgs();
        // String[] for the multi-path case (resolved by bdvpg's
        // StringArrayToSourceArray converter), String for the single-path case
        // (resolved by StringToSourceArray).
        Object sourcesValue = treePaths != null ? treePaths : treePath;
        int n = argList.size() * 2 + 2;
        Object[] out = new Object[n];
        out[0] = TREE_SOURCES_PARAM;
        out[1] = sourcesValue;
        int i = 2;
        for (ArgEntry e : argList) {
            out[i++] = e.key;
            out[i++] = e.value;
        }
        return out;
    }

    /**
     * Per-parameter narrations collected from {@link #arg(String, Object, String)
     * arg(name, value, narration)} calls, keyed by parameter name. Entries with
     * a {@code null} narration are filtered out so {@link Harvester} can use a
     * simple {@code map.get(name) != null} check. Iteration order matches the
     * {@code arg(...)} call order ({@link LinkedHashMap}), purely cosmetic since
     * the harvester loop reads by key.
     */
    private Map<String, String> narrationsMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ArgEntry e : argList) {
            if (e.narration != null) m.put(e.key, e.narration);
        }
        return m;
    }

    private boolean hasNamedArg(String name) {
        for (ArgEntry e : argList) {
            if (name.equals(e.key)) return true;
        }
        return false;
    }

    /** One {@link #arg(String, Object, String) arg(...)} call: the parameter
     * name, its value, and an optional per-field narration ({@code null} = no
     * subtitle for this field). */
    private static final class ArgEntry {
        final String key;
        final Object value;
        final String narration;
        ArgEntry(String key, Object value, String narration) {
            this.key = key;
            this.value = value;
            this.narration = narration;
        }
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
