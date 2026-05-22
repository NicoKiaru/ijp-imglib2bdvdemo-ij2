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
 * without a format break — {@code version} is bumped to {@code 2} so a
 * consumer can detect the format.</p>
 *
 * <p><b>Coordinates.</b> {@code x}/{@code y} (and {@code endX}/{@code endY}
 * for drags) are absolute screen coordinates in logical AWT pixels — the
 * space Java's {@code Robot} dispatches in. The top-level
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

    public static final int VERSION = 2;

    private static final List<StepRecord> steps = new ArrayList<>();
    private static StepRecord current;
    private static PendingComment pending;
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

    /** A single mouse gesture. {@code endX/endY} non-null for drags, {@code notches} for the wheel. */
    private static final class Evt {
        final String type;
        final long wallMs;
        final int x, y;
        final Integer endX, endY, notches;
        Evt(String type, long wallMs, int x, int y, Integer endX, Integer endY, Integer notches) {
            this.type = type;
            this.wallMs = wallMs;
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.endY = endY;
            this.notches = notches;
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

    /** Called by {@link Assets#session(String)} to drop any prior run's timeline. */
    static void onSessionStart() {
        steps.clear();
        current = null;
        pending = null;
        registerShutdownHook();
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
        current.events.add(new Evt("mouse.wheel", System.currentTimeMillis(),
                p.x, p.y, null, null, notches));
    }

    static void mouseDrag(int startX, int startY, int endX, int endY) {
        if (!ENABLED || current == null) return;
        current.events.add(new Evt("mouse.drag", System.currentTimeMillis(),
                startX, startY, endX, endY, null));
    }

    private static void addAtCursor(String type) {
        if (!ENABLED || current == null) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        current.events.add(new Evt(type, System.currentTimeMillis(),
                p.x, p.y, null, null, null));
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
        sb.append("  \"recordingBounds\": {\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"w\":").append(b.width)
                .append(",\"h\":").append(b.height).append("},\n");
        sb.append("  \"steps\": [\n");
        for (int i = 0; i < steps.size(); i++) {
            appendStep(sb, steps.get(i));
            sb.append(i < steps.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
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
            sb.append("}");
        }
        sb.append(s.events.isEmpty() ? "]\n" : "\n      ]\n");
        sb.append("    }");
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
