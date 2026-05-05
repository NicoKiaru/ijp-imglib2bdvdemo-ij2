package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandModule;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Future;

/**
 * Drives {@link RobotDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the harvester-dialog-driving pipeline end to end on the four
 * currently-supported parameter types: boolean, string, File, File[].
 */
public class RobotDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        File tmp = makeTmp("robot-demo-", ".txt", "hi from RobotDemoTest");
        File[] tmps = new File[] {
                makeTmp("robot-demo-list-a-", ".txt", "list-a"),
                makeTmp("robot-demo-list-b-", ".txt", "list-b"),
                makeTmp("robot-demo-list-c-", ".txt", "list-c"),
        };
        System.out.println("[RobotDemoTest] tmp file = " + tmp.getAbsolutePath());
        for (File f : tmps) System.out.println("[RobotDemoTest] tmp list = " + f.getAbsolutePath());

        Future<CommandModule> future = RobotCommands.run(RobotDemoCommand.class,
                "doIt", true,
                "name", "hello robot",
                "file", tmp,
                "files", tmps);

        CommandModule module = future.get();
        System.out.println("[RobotDemoTest] result = " + module.getOutput("result"));
        System.out.println("[RobotDemoTest] file   = " + module.getInput("file"));
        System.out.println("[RobotDemoTest] files  = " + java.util.Arrays.toString((File[]) module.getInput("files")));

        RobotCommands.runViaSearch(RobotDemoCommand.class, "robot dem",
                "doIt", true,
                "name", "hello robot",
                "file", tmp,
                "files", tmps);
    }

    private static File makeTmp(String prefix, String suffix, String content) throws Exception {
        File f = File.createTempFile(prefix, suffix);
        f.deleteOnExit();
        Files.write(f.toPath(), content.getBytes());
        return f;
    }
}