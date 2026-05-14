package ch.epfl.biop.docs.videos;

import ch.epfl.biop.docs.videos.fiji.BdvPlayground;
import ch.epfl.biop.docs.videos.fiji.Fiji;
import ch.epfl.biop.docs.videos.widgets.Harvester;

import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;

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
 *       to the command's menu path, then drive the harvester dialog
 *       (delegates to {@link BdvPlayground#runFromBDVPlaygroundTree}).</li>
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

    private final Class<? extends Command> commandClass;
    private Object[] args = new Object[0];
    private Mode mode = Mode.PROGRAMMATIC;
    private String searchQuery;
    private String treePath;

    private CommandExecutor(Class<? extends Command> commandClass) {
        this.commandClass = commandClass;
    }

    public static <C extends Command> CommandExecutor of(Class<C> commandClass) {
        return new CommandExecutor(commandClass);
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
     * {@code "Sources>Other Sources"}); see {@link BdvPlayground#runFromBDVPlaygroundTree}.
     */
    public CommandExecutor viaTreePath(String sourceTreePath) {
        this.mode = Mode.TREE;
        this.treePath = sourceTreePath;
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
                awaitProgrammatic();
                break;
            case SEARCH:
                if (searchQuery == null) {
                    throw new IllegalStateException("viaSearch(...) was not configured");
                }
                Harvester.runViaSearch(commandClass, searchQuery, args);
                break;
            case TREE:
                if (treePath == null) {
                    throw new IllegalStateException("viaTreePath(...) was not configured");
                }
                BdvPlayground.runFromBDVPlaygroundTree(treePath, commandClass, args);
                break;
        }
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
        return cs.run(commandClass, true, args);
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
