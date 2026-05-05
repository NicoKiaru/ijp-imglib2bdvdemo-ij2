package ch.epfl.biop.docs.videos;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Trivial SciJava command used to exercise {@link RobotCommands#run(Class, Object...)}.
 *
 * Two inputs (one boolean, one string) and one string output — enough to
 * verify that the Robot fills the harvester dialog correctly and that the
 * resulting {@code Future<CommandModule>} carries the expected output back.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Robot Demo Command")
public class RobotDemoCommand implements Command {

    @Parameter(label = "Do it?")
    boolean doIt = false;

    @Parameter(label = "Name")
    String name = "";

    @Parameter(type = ItemIO.OUTPUT)
    String result;

    @Override
    public void run() {
        result = "doIt=" + doIt + ", name=" + name;
        System.out.println("[RobotDemoCommand] " + result);
    }
}