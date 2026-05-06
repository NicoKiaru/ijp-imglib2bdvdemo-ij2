package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Trivial SciJava command used to exercise {@link RobotCommands} on the
 * BigDataViewer-Playground {@code SwingSourceListWidget} — the multi-source
 * {@link javax.swing.JTree} variant ({@code SourceAndConverter[]} parameter,
 * default unsorted style).
 *
 * <p>Same shape as {@link RobotSourceDemoCommand} but the output joins every
 * selected source's name. Useful to confirm that selecting a parent node in
 * the tree yields all descendant sources, since {@code SwingSourceListWidget}
 * recursively walks the subtree of any non-leaf selected node.</p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Robot Source List Demo Command")
public class RobotSourceListDemoCommand implements Command {

    @Parameter(label = "Sources")
    SourceAndConverter<?>[] sources;

    @Parameter(type = ItemIO.OUTPUT)
    String result;

    @Parameter(type = ItemIO.OUTPUT)
    int count;

    @Override
    public void run() {
        if (sources == null) {
            result = "null";
            count = 0;
        } else {
            count = sources.length;
            result = Stream.of(sources)
                    .map(s -> s.getSpimSource().getName())
                    .collect(Collectors.joining(", "));
        }
        System.out.println("[RobotSourceListDemoCommand] " + count + " source(s): " + result);
    }
}
