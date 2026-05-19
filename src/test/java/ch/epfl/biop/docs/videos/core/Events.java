package ch.epfl.biop.docs.videos.core;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured event stream emitted alongside {@code script.md}. Lets a
 * downstream video-rendering pipeline add click highlights, cursor
 * overlays, click / transition SFX or any other post-effect that needs
 * to know <em>when</em> and <em>where</em> a visible gesture happened on
 * the recording.
 *
 * <p>Currently captures mouse gestures dispatched through {@link Ui}:
 * {@code mouse.click}, {@code mouse.double_click}, {@code mouse.right_click},
 * {@code mouse.drag} and {@code mouse.wheel}. The {@code type} field
 * carries a dotted namespace so other event categories (key presses,
 * step boundaries, screenshots, ...) can be added later without a format
 * break.</p>
 *
 * <p><b>Timing.</b> The {@code tMs} of each event is elapsed milliseconds
 * since the first {@link Step#begin(String, String)} of the session — the
 * same anchor {@code script.md} uses for its {@code [mm:ss → mm:ss]}
 * stamps, so an event row and a script line are directly comparable.</p>
 *
 * <p><b>Scope.</b> Only events fired while a {@link Step} is open are
 * recorded, mirroring {@code script.md} and the recording itself — so
 * Fiji-boot gestures don't pollute the timeline.</p>
 *
 * <p><b>Coordinates.</b> {@code x}/{@code y} (and {@code endX}/{@code endY}
 * for drags) are absolute screen coordinates in logical AWT pixels — the
 * same space Java's {@code Robot} dispatches in. The top-level
 * {@code recordingBounds} block gives the logical bounds of the captured
 * area, so the pipeline can translate a click into recording-local
 * coordinates by subtracting {@code recordingBounds.x} / {@code .y}.</p>
 *
 * <p>The file is rewritten from the in-memory event list on every
 * {@link Step#end()} and from a JVM shutdown hook, so a crash mid-script
 * still leaves a parseable {@code events.json}.</p>
 */
public class Events {

    public static boolean ENABLED = true;

    public static String FILENAME = "events.json";

    private static final List<String> rows = new ArrayList<>();
    private static boolean shutdownHookRegistered = false;

    /** Called by {@link Assets#session(String)} to clear any prior run's events. */
    static void onSessionStart() {
        rows.clear();
        registerShutdownHook();
    }

    static void mouseClick() { addAtCursor("mouse.click"); }
    static void mouseDoubleClick() { addAtCursor("mouse.double_click"); }
    static void mouseRightClick() { addAtCursor("mouse.right_click"); }

    static void mouseDrag(int startX, int startY, int endX, int endY) {
        if (!ENABLED || !inStep()) return;
        long t = Step.elapsedMs();
        rows.add(String.format(
                "    {\"type\":\"mouse.drag\",\"tMs\":%d,\"x\":%d,\"y\":%d,\"endX\":%d,\"endY\":%d,\"step\":%s}",
                t, startX, startY, endX, endY, jsonStr(Step.currentName())));
    }

    static void mouseWheel(int notches) {
        if (!ENABLED || !inStep()) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        long t = Step.elapsedMs();
        rows.add(String.format(
                "    {\"type\":\"mouse.wheel\",\"tMs\":%d,\"x\":%d,\"y\":%d,\"notches\":%d,\"step\":%s}",
                t, p.x, p.y, notches, jsonStr(Step.currentName())));
    }

    private static void addAtCursor(String type) {
        if (!ENABLED || !inStep()) return;
        Point p = MouseInfo.getPointerInfo().getLocation();
        long t = Step.elapsedMs();
        rows.add(String.format(
                "    {\"type\":%s,\"tMs\":%d,\"x\":%d,\"y\":%d,\"step\":%s}",
                jsonStr(type), t, p.x, p.y, jsonStr(Step.currentName())));
    }

    private static boolean inStep() {
        return Step.currentName() != null;
    }

    /**
     * Rewrite {@code events.json} from the current in-memory event list.
     * Called from {@link Step#end()} and the JVM shutdown hook; safe to
     * call manually too.
     */
    public static void flush() {
        if (!ENABLED) return;
        File f = new File(Assets.dir(), FILENAME);
        Rectangle b = Ui.recordingBoundsLogical();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"session\": ").append(jsonStr(Assets.demoName())).append(",\n");
        sb.append("  \"recordingBounds\": {\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"w\":").append(b.width)
                .append(",\"h\":").append(b.height).append("},\n");
        sb.append("  \"events\": [\n");
        for (int i = 0; i < rows.size(); i++) {
            sb.append(rows.get(i));
            if (i < rows.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + f, e);
        }
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
        }, "Events-shutdown-flush"));
    }
}