package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandModule;

import java.util.concurrent.Future;

/**
 * Drives {@link RobotDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the harvester-dialog-driving pipeline end to end:
 * <ol>
 *   <li>Boot Fiji and place its main frame.</li>
 *   <li>Launch the command via the CommandService — its dialog appears.</li>
 *   <li>Robot fills the boolean and string parameters by label, then clicks OK.</li>
 *   <li>The returned Future yields a {@link CommandModule}; we read the
 *       {@code result} output and confirm it reflects the values typed.</li>
 * </ol>
 */
public class RobotDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        // 1) Direct via CommandService — returns a Future so we can pull the output.
        Future<CommandModule> future = RobotCommands.run(RobotDemoCommand.class,
                "doIt", true,
                "name", "hello robot");
        CommandModule module = future.get();
        System.out.println("[RobotDemoTest][direct] result = " + module.getOutput("result"));

        RobotHelper.pause(800);

        // 2) Natural via the IJ search bar — what a viewer sees in a tutorial video.
        // No Future is returned (the launch goes through legacy plumbing).
        RobotCommands.runViaSearch(RobotDemoCommand.class, "robot dem",
                "doIt", false,
                "name", "via search");
    }
}