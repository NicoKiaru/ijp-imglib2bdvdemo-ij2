package ch.epfl.biop.docs.videos;

import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.*;
import java.awt.*;

public class Robot {

    public static void openBDVPlaygroundTree() {
        RobotCommands.runViaSearch(SourceServiceShowCommand.class, "show bdv play");
        Frame bdvSources = RobotHelper.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        RobotHelper.resizeFrame(bdvSources, 450, 600);
        RobotHelper.dragFrame(bdvSources, RobotHelper.DEFAULT_FRAME_X, RobotHelper.DEFAULT_FRAME_Y + 150);

    }

    public static void runFromBDVPlaygroundTree(String sourceTreePath,
                                                Class<? extends Command> command,
                                                Object... args) {
        SourceService bss = FijiCommands.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree tree = sourceTree.getJTree();
        RobotTree.rightClickPath(tree, sourceTreePath);

        CommandService cs = FijiCommands.context().getService(CommandService.class);
        MenuPath menuPath = cs.getCommand(command).getMenuPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i< menuPath.size()-1;i++) {
            sb.append(menuPath.get(i).getName());
            sb.append(">");
        }
        sb.append(menuPath.getLeaf().getName());
        RobotPopup.clickPath(sb.toString());

        // The command is now running — let it finish painting any UI it spawns
        // (a BDV viewer, in this case) before the test driver exits.
        RobotHelper.pause(2000);

        RobotCommands.runOpenDialog(command, args);

    }
}
