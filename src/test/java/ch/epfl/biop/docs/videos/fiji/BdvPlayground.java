package ch.epfl.biop.docs.videos.fiji;

import ch.epfl.biop.docs.videos.core.Timings;
import ch.epfl.biop.docs.videos.core.Ui;
import ch.epfl.biop.docs.videos.widgets.Harvester;
import ch.epfl.biop.docs.videos.widgets.Popup;
import ch.epfl.biop.docs.videos.widgets.Tree;

import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.SourceTree;

import javax.swing.*;
import java.awt.*;

public class BdvPlayground {

    public static void openBDVPlaygroundTree() {
        Harvester.runViaSearch(SourceServiceShowCommand.class, "show bdv play");
        Frame bdvSources = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
        Ui.resizeFrame(bdvSources, 450, 600);
        Ui.dragFrame(bdvSources, Ui.DEFAULT_FRAME_X, Ui.DEFAULT_FRAME_Y + 150);

    }

    public static void runFromBDVPlaygroundTree(String sourceTreePath,
                                                Class<? extends Command> command,
                                                Object... args) {
        SourceService bss = Fiji.context().service(SourceService.class);
        SourceTree sourceTree = bss.tree();
        JTree tree = sourceTree.getJTree();
        Tree.rightClickPath(tree, sourceTreePath);

        CommandService cs = Fiji.context().getService(CommandService.class);
        MenuPath menuPath = cs.getCommand(command).getMenuPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i< menuPath.size()-1;i++) {
            sb.append(menuPath.get(i).getName());
            sb.append(">");
        }
        sb.append(menuPath.getLeaf().getName());
        Popup.clickPath(sb.toString());

        // The command is now running — let it finish painting any UI it spawns
        // (a BDV viewer, in this case) before the test driver exits.
        Ui.pause(Timings.scaled(Timings.PAUSE_AFTER_CLICK_MS));

        Harvester.runOpenDialog(command, args);

    }
}
