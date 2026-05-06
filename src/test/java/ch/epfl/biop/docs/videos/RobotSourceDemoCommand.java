package ch.epfl.biop.docs.videos;

import bdv.viewer.SourceAndConverter;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Trivial SciJava command used to exercise {@link RobotCommands} on the
 * BigDataViewer-Playground {@code SwingSourceWidget} (a {@link javax.swing.JTree}
 * over the {@code SourceService} tree model).
 *
 * Single {@link SourceAndConverter} input, single string output echoing the
 * selected source's name — enough to verify that the Robot can navigate the
 * tree and that the resulting {@code Future<CommandModule>} carries the
 * expected output back.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Robot Source Demo Command")
public class RobotSourceDemoCommand implements Command {

    @Parameter(label = "Source")
    SourceAndConverter<?> source;

    @Parameter(type = ItemIO.OUTPUT)
    String result;

    @Override
    public void run() {
        result = source == null ? "null" : source.getSpimSource().getName();
        System.out.println("[RobotSourceDemoCommand] " + result);
    }
}
