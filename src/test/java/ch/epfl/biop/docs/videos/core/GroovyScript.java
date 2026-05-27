package ch.epfl.biop.docs.videos.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates a programmatic-equivalent Groovy transcript of a tutorial demo
 * — one section per {@link Step}, with auto-collected imports and SciJava
 * {@code #@File} params hoisted at the top so a viewer of the recorded video
 * can re-run the same workflow headless in Fiji's script editor.
 *
 * <p>As of {@code timeline.json} v4 this class is no longer the source of
 * truth — the script is embedded structurally inside {@code timeline.json}
 * (top-level {@code script.preamble} + per-step {@code script.body}), and
 * {@code script.groovy} is rendered <em>from</em> that in-memory model as a
 * derived artifact. {@link #preamble()} and {@link #bodyForSlug(String)}
 * expose the same model to {@link Timeline}, which calls them at flush
 * time.</p>
 *
 * <p>Output: {@code script.groovy} in {@link Assets#dir()}, rewritten on
 * every {@link #flush()} (per {@link Step#end()} and via a JVM shutdown
 * hook).</p>
 *
 * <p>Two ways to feed the script:</p>
 * <ul>
 *   <li>Auto — every {@link ch.epfl.biop.docs.videos.CommandExecutor#execute()}
 *       call appends its {@code cs.run(...)} equivalent, unless the executor
 *       opts out via {@code .skipInScript()}.</li>
 *   <li>Manual — {@link Step#groovy(String)} adds a free-form line for
 *       operations that don't go through {@code CommandExecutor} (a custom
 *       loop, a display tweak the demo decided to surface, …).</li>
 * </ul>
 */
public class GroovyScript {

    public static boolean ENABLED = true;

    public static String FILENAME = "script.groovy";

    private static final Set<String> imports = new LinkedHashSet<>();
    private static final List<Section> sections = new ArrayList<>();
    private static Section current;
    /** Maps absolute path → hoisted {@link FileParam}. Dedupes identical paths so the same physical file becomes one {@code #@File}. */
    private static final Map<String, FileParam> fileParams = new LinkedHashMap<>();
    private static int fileParamCounter = 0;
    private static boolean shutdownHookRegistered;

    static final class Section {
        final String slug;
        final String description;
        final List<String> lines = new ArrayList<>();
        Section(String slug, String description) {
            this.slug = slug;
            this.description = description;
        }
    }

    static final class FileParam {
        final String name;
        final String path;
        final String label;
        FileParam(String name, String path, String label) {
            this.name = name;
            this.path = path;
            this.label = label != null ? label : name;
        }
    }

    /**
     * Pre-registered preferred names, keyed by absolute path. Looked up by
     * {@link #fileParamFor(String)} before falling back to {@code fileN}.
     * Demos populate it via {@link #nameFile(File, String, String)} right
     * after {@link Assets#session(String)} so the generated script reads as
     * {@code datasetFile} / {@code psfFile} / {@code exportFile} instead of
     * the anonymous {@code file1} / {@code file2} / {@code file3}.
     */
    private static final Map<String, FileParam> preferredNames = new LinkedHashMap<>();

    /** Called by {@link Assets#session(String)} so each demo run starts fresh. */
    public static void onSessionStart() {
        imports.clear();
        sections.clear();
        current = null;
        fileParams.clear();
        fileParamCounter = 0;
        preferredNames.clear();
        registerShutdownHook();
    }

    /** Open a section corresponding to the current {@link Step}. Empty sections are dropped at flush time. */
    public static void beginSection(String slug, String description) {
        current = new Section(slug, description);
        sections.add(current);
    }

    public static void endSection() {
        current = null;
        flush();
    }

    /** Add an import; duplicates are dropped. The {@code java.io.File} import is added automatically when File args appear. */
    public static void addImport(String fqn) {
        if (!ENABLED || fqn == null) return;
        // Skip java.lang.* — they're implicit in Groovy.
        if (fqn.startsWith("java.lang.")) return;
        imports.add(fqn);
    }

    /** Append a line to the current section (or to an implicit "prelude" section if no step is open). */
    public static void addLine(String line) {
        if (!ENABLED || line == null) return;
        if (current == null) {
            current = new Section("prelude", null);
            sections.add(current);
        }
        current.lines.add(line);
    }

    /**
     * Get or allocate a {@code #@File} param name for {@code path}. Same path
     * reused across multiple commands yields the same param — so the user
     * picks the dataset once and every downstream command references it.
     * Returns the {@link #nameFile(File, String) preferred} name if one was
     * registered for {@code path}, else allocates {@code fileN}.
     */
    public static String fileParamFor(String path) {
        FileParam p = fileParams.get(path);
        if (p == null) {
            FileParam preferred = preferredNames.get(path);
            p = preferred != null ? preferred
                    : new FileParam("file" + (++fileParamCounter), path, null);
            fileParams.put(path, p);
        }
        return p.name;
    }

    /**
     * Register a human-readable parameter name and label for {@code file} so
     * the generated {@code script.groovy} reads as
     * {@code #@File datasetFile} instead of the anonymous {@code file1}.
     * Call right after {@link Assets#session(String)} (and before the first
     * command that references the file) at the top of a demo:
     *
     * <pre>
     * Assets.session("DeconvolutionVideoDemo");
     * GroovyScript.nameFile(fileDataset, "datasetFile", "Input dataset");
     * GroovyScript.nameFile(filePSF,     "psfFile",     "PSF");
     * </pre>
     *
     * <p>Pick a <em>role-shaped</em> label ({@code "Input dataset"},
     * {@code "PSF"}, {@code "Output OME-TIFF"}) rather than one that names
     * the specific physical file ({@code "HeLa LLS7 dataset (CZI)"}) —
     * the label is what a viewer of the recording sees when re-running the
     * script on their own machine, where they will almost certainly point
     * the parameter at a different file.</p>
     *
     * <p>Labels are emitted inline inside the SciJava {@code #@File}
     * directive ({@code #@File (label="…") foo}), which is a single-line
     * declaration — newlines in {@code label} are stripped at registration
     * time so they cannot break the directive.</p>
     *
     * <p>Registering the same path twice replaces the prior entry. Renaming
     * a file that was already auto-allocated is allowed — the entry is
     * updated in place so downstream references and the hoisted
     * {@code #@File} declaration use the new name.</p>
     *
     * @param file      the {@link File} whose absolute path keys the entry
     * @param paramName the Groovy variable name (must be a valid identifier)
     * @param label     the SciJava UI label (may include spaces); {@code null} reuses {@code paramName}
     */
    public static void nameFile(File file, String paramName, String label) {
        if (!ENABLED || file == null) return;
        String path = file.getAbsolutePath();
        FileParam p = new FileParam(paramName, path, sanitizeLabel(label));
        preferredNames.put(path, p);
        if (fileParams.containsKey(path)) {
            fileParams.put(path, p);
        }
    }

    /**
     * Strip newlines and surrounding whitespace from a label so it always
     * fits on a single line — a multi-line label would break the
     * {@code #@File} directive and make the script unparseable. Collapses
     * any internal CR/LF runs to a single space.
     */
    private static String sanitizeLabel(String label) {
        if (label == null) return null;
        return label.replaceAll("[\\r\\n]+", " ").trim();
    }

    /** Shortcut: same as {@link #nameFile(File, String, String)} with label = paramName. */
    public static void nameFile(File file, String paramName) {
        nameFile(file, paramName, null);
    }

    /**
     * The script's preamble: hoisted {@code #@File} params, the
     * {@code #@CommandService cs} declaration, and the sorted {@code import}
     * lines — i.e. setup that doesn't belong to any single step. Embedded
     * verbatim as {@code script.preamble} in {@code timeline.json} v4, and
     * prepended to {@code script.groovy} on every {@link #flush()}. No
     * trailing newline so callers control their own separator.
     */
    public static String preamble() {
        StringBuilder sb = new StringBuilder();
        for (FileParam p : fileParams.values()) {
            sb.append("#@File (label=\"").append(escapeAnnotation(p.label))
                    .append("\", value=\"").append(escapePath(p.path)).append("\") ")
                    .append(p.name).append("\n");
        }
        sb.append("#@CommandService cs");
        if (!imports.isEmpty()) {
            sb.append("\n\n");
            List<String> sortedImports = new ArrayList<>(imports);
            Collections.sort(sortedImports);
            for (int i = 0; i < sortedImports.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append("import ").append(sortedImports.get(i));
            }
        }
        return sb.toString();
    }

    /**
     * Body of the step's script — its accumulated {@link #addLine lines}
     * joined by {@code "\n"}, with no leading / trailing blank lines, and no
     * {@code // === slug ===} header. Returns {@code null} when the step has
     * no executable lines (visualization-only). Used by {@link Timeline} to
     * fill the per-step {@code script.body} in {@code timeline.json} v4.
     */
    public static String bodyForSlug(String slug) {
        if (slug == null) return null;
        for (Section s : sections) {
            if (slug.equals(s.slug)) {
                if (s.lines.isEmpty()) return null;
                return String.join("\n", s.lines);
            }
        }
        return null;
    }

    /**
     * Rewrite {@code script.groovy} from the current in-memory state. As of
     * {@code timeline.json} v4 this is a derived artifact: the same model
     * that feeds {@link Timeline}'s {@code script.preamble} and per-step
     * {@code script.body} is rendered back to Groovy form, with a
     * {@code // === <slug> ===} header and the step's description as a
     * comment block before each body.
     */
    public static void flush() {
        if (!ENABLED) return;
        File out = new File(Assets.dir(), FILENAME);
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated from ").append(Assets.demoName()).append("\n");
        sb.append("// Headless programmatic equivalent of the recorded demo.\n");
        sb.append("// Derived from timeline.json — visualization-only steps are omitted.\n\n");

        sb.append(preamble()).append("\n\n");

        for (Section s : sections) {
            if (s.lines.isEmpty()) continue;
            sb.append("// === ").append(s.slug).append(" ===\n");
            if (s.description != null && !s.description.isEmpty()) {
                for (String line : s.description.split("\n")) {
                    sb.append("// ").append(line).append("\n");
                }
            }
            for (String line : s.lines) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
        }

        try {
            Files.write(out.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + out, e);
        }
    }

    private static String escapePath(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Escape a free-form string for inclusion inside a SciJava {@code label="…"} annotation value. */
    private static String escapeAnnotation(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) return;
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { flush(); } catch (RuntimeException ignored) { }
        }, "GroovyScript-shutdown-flush"));
    }
}
