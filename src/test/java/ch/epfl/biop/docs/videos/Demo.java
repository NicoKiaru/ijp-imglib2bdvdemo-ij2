package ch.epfl.biop.docs.videos;

import ch.epfl.biop.docs.CatchCommandForDocPostProcessor;
import ch.epfl.biop.docs.videos.core.Assets;
import ch.epfl.biop.docs.videos.core.CommandRef;
import ch.epfl.biop.docs.videos.core.Timeline;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Demo-side authoring facade for the intro / outro of a tutorial video.
 *
 * <p>Lives in {@code videos/} rather than {@code core/} on purpose: it
 * harvests the commands a demo actually ran from
 * {@link CatchCommandForDocPostProcessor} (a SciJava postprocessor), and
 * {@code core/} is kept Fiji-unaware. It converts that Fiji-side data into
 * neutral {@link CommandRef} DTOs and pushes everything into
 * {@link Timeline}, which owns the {@code timeline.json} writer.</p>
 *
 * <p>Two call sites per demo:</p>
 * <pre>
 * Assets.session("DeconvolutionVideoDemo");
 * Demo.intro("Lazy Richardson-Lucy Deconvolution in Fiji",
 *         "Deconvolve a multi-gigabyte LLS7 dataset without loading it into memory.",
 *         "Streams data block-by-block — multi-gigabyte datasets on a modest computer",
 *         "Scales across every available GPU",
 *         "Lazy by design — a block is computed only when a viewer asks for it");
 * // ... Step.begin / Step.end ...
 * Demo.outro("You've seen tiled, lazy deconvolution end to end.",
 *         "https://github.com/BIOP/ijp-imglib2bdvdemo-ij2");
 * </pre>
 *
 * <p>{@link #intro} feeds {@code timeline.json}'s {@code intro} block (the
 * on-screen title card). {@link #outro} feeds the {@code outro} block (the
 * closing card — closing line, the harvested command list, links) <em>and</em>
 * writes a ready-to-paste {@code youtube-description.md} next to the clips.</p>
 */
public class Demo {

    /** YouTube-description file written next to the clips by {@link #outro}. */
    public static String DESCRIPTION_FILENAME = "youtube-description.md";

    private static String title;
    private static String tagline;
    private static List<String> highlights = new ArrayList<>();

    /**
     * Set the title-card metadata. Call once, right after
     * {@link Assets#session(String)} and before the first {@code Step}.
     *
     * @param title      the demo title (large text on the intro card)
     * @param tagline    one sentence — what the viewer is about to watch
     * @param highlights the "why it matters" hook, a few short benefit-shaped
     *                   bullet strings (multi-GPU, memory efficient, lazy …)
     */
    public static void intro(String title, String tagline, String... highlights) {
        Demo.title = title;
        Demo.tagline = tagline;
        Demo.highlights = highlights == null ? new ArrayList<>() : Arrays.asList(highlights);
        Timeline.setIntro(title, tagline, Demo.highlights);
    }

    /**
     * Set the closing-card metadata and write {@link #DESCRIPTION_FILENAME}.
     * Call once at the end of the demo, after the last {@code Step.end()}.
     * The command list is harvested automatically from the Fiji commands the
     * demo actually invoked — never list them by hand.
     *
     * @param closing one closing sentence (recap / takeaway)
     * @param links   reference links — repo, update site, dataset DOI, …
     */
    public static void outro(String closing, String... links) {
        List<CommandRef> commands = CatchCommandForDocPostProcessor.usedCommandRefs();
        List<String> linkList = links == null ? new ArrayList<>() : Arrays.asList(links);
        Timeline.setOutro(closing, commands, linkList);
        writeYoutubeDescription(closing, commands, linkList);
    }

    /**
     * Render the harvested metadata as a Markdown blob ready to paste into
     * the YouTube description box: title, tagline, the "why it matters"
     * highlights, the closing line, every command used (name + version +
     * source link) and the reference links.
     */
    private static void writeYoutubeDescription(String closing,
                                                List<CommandRef> commands,
                                                List<String> links) {
        StringBuilder sb = new StringBuilder();
        String heading = (title != null && !title.isEmpty()) ? title : Assets.demoName();
        sb.append("# ").append(heading).append("\n\n");
        if (tagline != null && !tagline.isEmpty()) {
            sb.append(tagline).append("\n\n");
        }
        if (highlights != null && !highlights.isEmpty()) {
            sb.append("## Why it matters\n\n");
            for (String h : highlights) sb.append("- ").append(h).append("\n");
            sb.append("\n");
        }
        if (closing != null && !closing.isEmpty()) {
            sb.append(closing).append("\n\n");
        }
        if (commands != null && !commands.isEmpty()) {
            sb.append("## Commands used in this video\n\n");
            for (CommandRef c : commands) {
                sb.append("- **").append(c.name).append("**");
                if (c.version != null && !c.version.isEmpty()) {
                    sb.append(" (v").append(c.version).append(")");
                }
                if (c.sourceUrl != null && !c.sourceUrl.isEmpty()) {
                    sb.append(" — ").append(c.sourceUrl);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (links != null && !links.isEmpty()) {
            sb.append("## Links\n\n");
            for (String l : links) sb.append("- ").append(l).append("\n");
            sb.append("\n");
        }
        File f = new File(Assets.dir(), DESCRIPTION_FILENAME);
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + f, e);
        }
    }
}