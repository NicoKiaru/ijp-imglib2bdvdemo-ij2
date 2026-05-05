package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandModule;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Future;

/**
 * Drives {@link RobotDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the harvester-dialog-driving pipeline end to end on the three
 * currently-supported parameter types: boolean, string, file.
 */
public class RobotDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        // A real file on disk so the file-chooser's "Open" actually has
        // something to select. Created in the system temp dir.
        File tmp = File.createTempFile("robot-demo-", ".txt");
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), "hi from RobotDemoTest".getBytes());
        System.out.println("[RobotDemoTest] tmp file = " + tmp.getAbsolutePath());

        Future<CommandModule> future = RobotCommands.run(RobotDemoCommand.class,
                "doIt", true,
                "name", "hello robot",
                "file", tmp);

        CommandModule module = future.get();
        System.out.println("[RobotDemoTest] result = " + module.getOutput("result"));
        System.out.println("[RobotDemoTest] file   = " + module.getInput("file"));

        RobotCommands.runViaSearch(RobotDemoCommand.class, "robot dem", "doIt", true,
                "name", "hello robot",
                "file", tmp);

    }
}