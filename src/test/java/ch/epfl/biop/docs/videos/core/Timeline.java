package ch.epfl.biop.docs.videos.core;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Single per-session output file, {@code timeline.json}, replacing the old
 * {@code events.json} + {@code script.md} pair. It feeds the downstream
 * video pipeline everything it needs to place click highlights, cursor
 * overlays, SFX and subtitles onto the per-step {@code .mp4} clips.
 *
 * <p><b>Per-clip clock.</b> The old format used a session-global clock;
 * because that clock kept running through the un-recorded gaps between
 * clips, the mapping onto per-step clips drifted and the error accumulated
 * toward the end. In {@code timeline.json} every {@code tMs} / {@code endMs}
 * is milliseconds from the <em>first captured frame</em> of that step's own
 * clip (see {@link ScreenRecorder#currentClipFirstFrameWallMs()}). Each
 * step's timeline is independent, so there is nothing to accumulate.</p>
 *
 * <p><b>Shape.</b> One {@code steps[]} entry per recorded clip, in capture
 * order — including steps the pipeline later skips (e.g. {@code boot-fiji}).
 * {@code clip} is the exact mp4 filename so the consumer matches by name.
 * {@code description} is the chapter-title text; {@code comments[]} carries
 * the narration sub-steps (what {@code script.md} held). Event {@code type}
 * is namespaced ({@code mouse.*}) so other categories can be added later
 * without a format break — {@code version} is {@code 4} so a consumer can
 * detect the format. Drag events may carry an optional {@code path} array
 * of downsampled intermediate samples (see
 * {@link EventRecorder#CAPTURE_DRAG_PATH}).</p>
 *
 * <p><b>Intro / outro.</b> Optional top-level {@code intro} (title, tagline,
 * {@code highlights[]}) and {@code outro} (closing line, {@code commands[]}
 * harvested from the demo's actually-invoked Fiji commands, {@code links[]})
 * blocks let the downstream pipeline render a title card and a closing card.
 * Set via {@link #setIntro} / {@link #setOutro} — the demo-side authoring
 * facade is {@code videos/Demo}, which also emits a {@code youtube-description.md}.</p>
 *
 * <p><b>Embedded script (v4).</b> The headless programmatic equivalent of
 * the recorded demo lives structurally inside {@code timeline.json} rather
 * than in a sibling Groovy file: a top-level {@code script} block carries
 * {@code language} and a {@code preamble} string (hoisted {@code #@File}
 * params, the {@code #@CommandService cs} declaration, imports), and every
 * step carries a {@code script} field. Actionable steps emit
 * {@code "script": {"body": "..."}} with the step's executable snippet;
 * visualization-only steps (no headless equivalent) emit
 * {@code "script": {"body": null, "note": "visualization-only — observe the viewers"}}
 * so consumers can deliberately render the "no headless equivalent" state.
 * {@code script.groovy} is now a derived artifact written by
 * {@link GroovyScript#flush()} from the same in-memory model — see
 * {@link GroovyScript#preamble()} and {@link GroovyScript#bodyForSlug(String)}.</p>
 *
 * <p><b>Coordinates.</b> {@code x}/{@code y} (and {@code endX}/{@code endY}
 * for drags, and the {@code [x,y]} pairs inside a drag's optional
 * {@code path[][]}) are absolute screen coordinates in logical AWT pixels —
 * the space Java's {@code Robot} dispatches in. The top-level
 * {@code recordingBounds} block gives the logical bounds of the captured
 * area, so a click maps to recording-local coordinates by subtracting
 * {@code recordingBounds.x} / {@code .y}.</p>
 *
 * <p>The file is rewritten from the in-memory step list on every
 * {@link Step#end()} and from a JVM shutdown hook, so a crash mid-script
 * still leaves a parseable {@code timeline.json}.</p>
 */
public class Timeline {

    public static boolean ENABLED = true;

    public static String FILENAME = "timeline.json";

    public static final int VERSION = 4;

    /**
     * Label written to {@code script.note} when a step has no executable
     * Groovy equivalent — e.g. the operator opening a viewer, dragging a
     * window into place, or just looking at the result. Lets consumers
     * render "no headless equivalent for this step" deliberately rather
     * than inferring it from a missing field.
     */
    public static final String VISUALIZATION_ONLY_NOTE =
            "visualization-only — observe the viewers";

    private static final List<StepRecord> steps = new ArrayList<>();
    private static StepRecord current;
    private static PendingComment pending;
    private static Intro intro;
    private static Outro outro;
    private static boolean shutdownHookRegistered;

    /** One recorded clip: chapter metadata + its narration and mouse events. */
    private static final class StepRecord {
        final int index;
        final String slug;
        final String clip;
        final String description;
        final long startedAtWallMs;
        /** Wall clock of the clip's first captured frame; {@code 0} = unknown. */
        long firstFrameWallMs;
        final List<Comment> comments = new ArrayList<>();
        final List<Evt> events = new ArrayList<>();

        StepRecord(int index, String slug, String clip, String description, long startedAtWallMs) {
            this.index = index;
            this.slug = slug;
            this.clip = clip;
            this.description = description;
            this.startedAtWallMs = startedAtWallMs;
        }

        /** t=0 reference: the first frame when known, else the step's begin time. */
        long anchorMs() {
            return firstFrameWallMs != 0L ? firstFrameWallMs : startedAtWallMs;
        }
    }

    /** A narration sub-step; {@code endWallMs} is resolved when the next say / end fires. */
    private static final class Comment {
        final long startWallMs;
        long endWallMs;
        final String text;
        Comment(long startWallMs, String text) {
            this.startWallMs = startWallMs;
            this.endWallMs = startWallMs;
            this.text = text;
        }
    }

    /**
     * A single input gesture. {@code endX/endY} non-null for drags,
     * {@code notches} for the wheel, {@code points} for the count of
     * {@code MOUSE_DRAGGED} samples captured between press and release
     * (only set by {@link EventRecorder}, not by the Robot-driven path).
     * {@code path} is the optional downsampled polyline of those samples
     * — also only populated by {@link EventRecorder}, gated on
     * {@link EventRecorder#CAPTURE_DRAG_PATH}; endpoints are excluded
     * (they're already in {@code x}/{@code y} / {@code endX}/{@code endY}).
     * {@code modifiers} is the list of keyboard modifiers held at event
     * time — empty / {@code null} for the Robot-driven path, populated by
     * {@link EventRecorder} from {@link java.awt.event.InputEvent#getModifiersEx()}.
     * {@code key} carries the human-readable key name for {@code key.*}
     * events (e.g. {@code "Right"}, {@code "X"}, {@code "F2"}); null on
     * mouse events.
     */
    private static final class Evt {
        final String type;
        final long wallMs;
        final int x, y;
        final Integer endX, endY, notches, points;
        final List<String> modifiers;
        final String key;
        final List<int[]> path;
        Evt(String type, long wallMs, int x, int y,
            Integer endX, Integer endY, Integer notches, Integer points,
            List<String> modifiers, String key, List<int[]> path) {
            this.type = type;
            this.wallMs = wallMs;
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.endY = endY;
            this.notches = notches;
            this.points = points;
            this.modifiers = modifiers;
            this.key = key;
            this.path = path;
        }
    }

    private static final class PendingComment {
        final long startWallMs;
        final String text;
        PendingComment(long startWallMs, String text) {
            this.startWallMs = startWallMs;
            this.text = text;
        }
    }

    /** Optional title-card metadata, shown before the first step. */
    private static final class Intro {
        final String title;
        final String tagline;
        final List<String> highlights;
        Intro(String title, String tagline, List<String> highlights) {
            this.title = title;
            this.tagline = tagline;
            this.highlights = highlights;
        }
    }

    /** Optional closing-card metadata, shown after the last step. */
    private static final class Outro {
        final String closing;
        final List<CommandRef> commands;
        final List<String> links;
        Outro(String closing, List<CommandRef> commands, List<String> links) {
            this.closing = closing;
            this.commands = commands;
            this.links = links;
        }
    }

    /** Called by {@link Assets#session(String)} to drop any prior run's timeline. */
    static void onSessionStart() {
        steps.clear();
        current = null;
        pending = null;
        intro = null;
        outro = null;
        registerShutdownHook();
    }

    /**
     * Set the demo's title-card metadata. Called once by {@code videos/Demo}
     * at the start of a demo, before the first step. Triggers a {@link #flush()}
     * so the data is on disk even if the demo crashes before the first
     * {@link Step#end()}.
     */
    public static void setIntro(String title, String tagline, List<String> highlights) {
        intro = new Intro(title, tagline,
                highlights == null ? new ArrayList<>() : new ArrayList<>(highlights));
        flush();
    }

    /**
     * Set the demo's closing-card metadata. Called once by {@code videos/Demo}
     * at the end of a demo; {@code commands} is harvested from the Fiji
     * commands the demo actually invoked. Triggers a {@link #flush()} since the
     * last {@link Step#end()} has usually already run by the time this fires.
     */
    public static void setOutro(String closing, List<CommandRef> commands, List<String> links) {
        outro = new Outro(closing,
                commands == null ? new ArrayList<>() : new ArrayList<>(commands),
                links == null ? new ArrayList<>() : new ArrayList<>(links));
        flush();
    }

    /**
     * Open a new step record. Called from {@link Step#begin} <em>after</em>
     * {@link ScreenRecorder#start}, so the clip name / index are available;
     * the first-frame anchor resolves asynchronously and is collected at
     * {@link #endStep()}.
     */
    static void beginStep(String slug, String description) {
        flushPending(System.currentTimeMillis());
        current = new StepRecord(
                ScreenRecorder.currentClipIndex(),
                slug,
                ScreenRecorder.currentClipFileName(),
                description,
                System.currentTimeMillis());
        steps.add(current);
    }

    /** Record a narration sub-step; its end time is filled in by the next {@code comment} / {@code endStep}. */
    static void comment(String text) {
        long now = System.currentTimeMillis();
        flushPending(now);
        pending = new PendingComment(now, text);
    }

    /** Resolve the open narration line's end time — called before the recorder is torn down. */
    static void resolvePending(long endWallMs) {
        flushPending(endWallMs);
    }

    /** Finalise the open step: collect its clip's first-frame anchor, then rewrite {@code timeline.json}. */
    static void endStep() {
        flushPending(System.currentTimeMillis());
        if (current != null) {
            current.firstFrameWallMs = ScreenRecorder.currentClipFirstFrameWallMs();
        }
        current = null;
        flush();
    }

    static void mouseClick()       { addAtCursor("mouse.click"); }
    static void mouseDoubleClick() { addAtCursor("mouse.double_click"); }
    static void mouseRightClick()  { addAtCursor("mouse.right_click"); }

    static void mouseWheel(int notches) {
        if (!ENABLED || current == null) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        addEvent("mouse.wheel", p.x, p.y, null, null, notches, null, null);
    }

    static void mouseDrag(int startX, int startY, int endX, int endY) {
        addEvent("mouse.drag", startX, startY, endX, endY, null, null, null);
    }

    // --- EventRecorder-side variants: explicit screen coords + modifiers ---

    static void mouseClickAt(int x, int y, List<String> modifiers) {
        addEvent("mouse.click", x, y, null, null, null, null, modifiers);
    }

    static void mouseDoubleClickAt(int x, int y, List<String> modifiers) {
        addEvent("mouse.double_click", x, y, null, null, null, null, modifiers);
    }

    static void mouseRightClickAt(int x, int y, List<String> modifiers) {
        addEvent("mouse.right_click", x, y, null, null, null, null, modifiers);
    }

    static void mouseWheelAt(int x, int y, int notches, List<String> modifiers) {
        addEvent("mouse.wheel", x, y, null, null, notches, null, modifiers);
    }

    static void mouseDragAt(int startX, int startY, int endX, int endY,
                            int points, List<String> modifiers,
                            List<int[]> path) {
        addEvent("mouse.drag", startX, startY, endX, endY, null, points,
                modifiers, null, path);
    }

    /**
     * Record a key press captured by {@link EventRecorder}. {@code key} is
     * the human-readable name (e.g. {@code "Right"}, {@code "X"},
     * {@code "F2"}); the cursor position at press time is used for
     * {@code x}/{@code y} so an overlay can place a key badge near the
     * cursor.
     */
    static void keyPressAt(String key, List<String> modifiers) {
        if (!ENABLED || current == null) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        addEvent("key.press", p.x, p.y, null, null, null, null, modifiers, key);
    }

    private static void addAtCursor(String type) {
        if (!ENABLED || current == null) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        addEvent(type, p.x, p.y, null, null, null, null, null);
    }

    private static void addEvent(String type, int x, int y,
                                 Integer endX, Integer endY, Integer notches,
                                 Integer points, List<String> modifiers) {
        addEvent(type, x, y, endX, endY, notches, points, modifiers, null, null);
    }

    private static void addEvent(String type, int x, int y,
                                 Integer endX, Integer endY, Integer notches,
                                 Integer points, List<String> modifiers, String key) {
        addEvent(type, x, y, endX, endY, notches, points, modifiers, key, null);
    }

    private static void addEvent(String type, int x, int y,
                                 Integer endX, Integer endY, Integer notches,
                                 Integer points, List<String> modifiers, String key,
                                 List<int[]> path) {
        if (!ENABLED || current == null) return;
        current.events.add(new Evt(type, System.currentTimeMillis(),
                x, y, endX, endY, notches, points, modifiers, key, path));
    }

    private static void flushPending(long endWallMs) {
        if (pending == null) return;
        if (current != null && pending.text != null && !pending.text.isEmpty()) {
            Comment c = new Comment(pending.startWallMs, pending.text);
            c.endWallMs = Math.max(pending.startWallMs, endWallMs);
            current.comments.add(c);
        }
        pending = null;
    }

    /**
     * Rewrite {@code timeline.json} from the current in-memory step list.
     * Called from {@link #endStep()} and the JVM shutdown hook.
     */
    public static void flush() {
        if (!ENABLED) return;
        File f = new File(Assets.dir(), FILENAME);
        Rectangle b = Ui.recordingBoundsLogical();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"session\": ").append(jsonStr(Assets.demoName())).append(",\n");
        appendScript(sb);
        if (intro != null) appendIntro(sb);
        sb.append("  \"recordingBounds\": {\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"w\":").append(b.width)
                .append(",\"h\":").append(b.height).append("},\n");
        sb.append("  \"steps\": [\n");
        for (int i = 0; i < steps.size(); i++) {
            appendStep(sb, steps.get(i));
            sb.append(i < steps.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]");
        if (outro != null) {
            sb.append(",\n");
            appendOutro(sb);
        } else {
            sb.append("\n");
        }
        sb.append("}\n");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + f, e);
        }
    }

    private static void appendStep(StringBuilder sb, StepRecord s) {
        long anchor = s.anchorMs();
        sb.append("    {\n");
        sb.append("      \"index\": ").append(s.index).append(",\n");
        sb.append("      \"slug\": ").append(jsonStr(s.slug)).append(",\n");
        sb.append("      \"clip\": ").append(jsonStr(s.clip)).append(",\n");
        sb.append("      \"description\": ").append(jsonStr(s.description)).append(",\n");
        appendStepScript(sb, s.slug);
        sb.append("      \"startedAt\": ")
                .append(jsonStr(Instant.ofEpochMilli(s.startedAtWallMs).toString())).append(",\n");

        sb.append("      \"comments\": [");
        for (int i = 0; i < s.comments.size(); i++) {
            Comment c = s.comments.get(i);
            long t = Math.max(0L, c.startWallMs - anchor);
            long e = Math.max(t, c.endWallMs - anchor);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("        {\"tMs\":").append(t)
                    .append(",\"endMs\":").append(e)
                    .append(",\"text\":").append(jsonStr(c.text)).append("}");
        }
        sb.append(s.comments.isEmpty() ? "],\n" : "\n      ],\n");

        sb.append("      \"events\": [");
        for (int i = 0; i < s.events.size(); i++) {
            Evt ev = s.events.get(i);
            long t = Math.max(0L, ev.wallMs - anchor);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("        {\"type\":").append(jsonStr(ev.type))
                    .append(",\"tMs\":").append(t)
                    .append(",\"x\":").append(ev.x)
                    .append(",\"y\":").append(ev.y);
            if (ev.endX != null) {
                sb.append(",\"endX\":").append(ev.endX).append(",\"endY\":").append(ev.endY);
            }
            if (ev.notches != null) {
                sb.append(",\"notches\":").append(ev.notches);
            }
            if (ev.points != null) {
                sb.append(",\"points\":").append(ev.points);
            }
            if (ev.path != null && !ev.path.isEmpty()) {
                sb.append(",\"path\":[");
                for (int p = 0; p < ev.path.size(); p++) {
                    int[] pt = ev.path.get(p);
                    if (p > 0) sb.append(",");
                    sb.append("[").append(pt[0]).append(",").append(pt[1]).append("]");
                }
                sb.append("]");
            }
            if (ev.key != null) {
                sb.append(",\"key\":").append(jsonStr(ev.key));
            }
            if (ev.modifiers != null && !ev.modifiers.isEmpty()) {
                sb.append(",\"modifiers\":[");
                for (int m = 0; m < ev.modifiers.size(); m++) {
                    if (m > 0) sb.append(",");
                    sb.append(jsonStr(ev.modifiers.get(m)));
                }
                sb.append("]");
            }
            sb.append("}");
        }
        sb.append(s.events.isEmpty() ? "]\n" : "\n      ]\n");
        sb.append("    }");
    }

    /**
     * Emit the top-level {@code script} block (language + preamble). Always
     * emitted in v4 so consumers can parse on shape rather than presence,
     * even when the demo registered no {@code #@File} params and no
     * imports — in that minimal case the preamble is just
     * {@code "#@CommandService cs"}.
     */
    private static void appendScript(StringBuilder sb) {
        sb.append("  \"script\": {\n");
        sb.append("    \"language\": \"groovy\",\n");
        sb.append("    \"preamble\": ").append(jsonStr(GroovyScript.preamble())).append("\n");
        sb.append("  },\n");
    }

    /**
     * Emit the per-step {@code script} field — either {@code {"body": "..."}}
     * for an actionable step, or
     * {@code {"body": null, "note": "visualization-only — observe the viewers"}}
     * when the step has no headless equivalent.
     */
    private static void appendStepScript(StringBuilder sb, String slug) {
        String body = GroovyScript.bodyForSlug(slug);
        sb.append("      \"script\": ");
        if (body == null) {
            sb.append("{\"body\": null, \"note\": ")
                    .append(jsonStr(VISUALIZATION_ONLY_NOTE)).append("},\n");
        } else {
            sb.append("{\"body\": ").append(jsonStr(body)).append("},\n");
        }
    }

    private static void appendIntro(StringBuilder sb) {
        sb.append("  \"intro\": {\n");
        sb.append("    \"title\": ").append(jsonStr(intro.title)).append(",\n");
        sb.append("    \"tagline\": ").append(jsonStr(intro.tagline)).append(",\n");
        sb.append("    \"highlights\": ");
        appendStrArray(sb, "    ", intro.highlights);
        sb.append("\n  },\n");
    }

    private static void appendOutro(StringBuilder sb) {
        sb.append("  \"outro\": {\n");
        sb.append("    \"closing\": ").append(jsonStr(outro.closing)).append(",\n");
        sb.append("    \"commands\": [");
        for (int i = 0; i < outro.commands.size(); i++) {
            CommandRef c = outro.commands.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("      {\"name\":").append(jsonStr(c.name))
                    .append(",\"version\":").append(jsonStr(c.version))
                    .append(",\"sourceUrl\":").append(jsonStr(c.sourceUrl)).append("}");
        }
        sb.append(outro.commands.isEmpty() ? "],\n" : "\n    ],\n");
        sb.append("    \"links\": ");
        appendStrArray(sb, "    ", outro.links);
        sb.append("\n  }\n");
    }

    /** Append a JSON array of strings, one item per line, indented under {@code indent}. */
    private static void appendStrArray(StringBuilder sb, String indent, List<String> items) {
        if (items == null || items.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append(indent).append("  ").append(jsonStr(items.get(i)));
            sb.append(i < items.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append("]");
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) return;
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { flush(); } catch (RuntimeException ignored) { /* best-effort on shutdown */ }
        }, "Timeline-shutdown-flush"));
    }
}
