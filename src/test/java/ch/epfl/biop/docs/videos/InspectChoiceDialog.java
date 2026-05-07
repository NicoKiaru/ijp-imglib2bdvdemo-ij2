package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandService;

import java.awt.Dialog;

/**
 * Starts Fiji, opens the {@link RobotChoiceDemoCommand} harvester dialog
 * (without filling), and dumps its component tree to stdout — used to
 * identify the exact Swing classes SciJava emits for each choice
 * {@code @Parameter} style, so {@link RobotCommands} can be extended to
 * drive them.
 *
 * Run from the IDE: right-click → Run 'main()'. The dialog is left open
 * so you can also poke at it manually after the dump (e.g. to see what
 * happens when a {@code JComboBox} popup is opened).
 */
public class InspectChoiceDialog {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        CommandService cs = FijiCommands.context().service(CommandService.class);
        // Don't .get() — that would block until the user clicks OK and we
        // want the dialog open while we inspect it.
        cs.run(RobotChoiceDemoCommand.class, true);

        Dialog dialog = RobotHelper.waitForActiveDialog(Timings.FRAME_WAIT_TIMEOUT_MS);
        if (dialog == null) {
            throw new IllegalStateException("No dialog appeared for RobotChoiceDemoCommand");
        }
        // Let layout settle before reading bounds / screen positions.
        RobotHelper.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

        System.out.println("===== RobotChoiceDemoCommand dialog =====");
        System.out.print(ComponentInspector.prettyPrint(ComponentInspector.inspect(dialog)));
        System.out.println("==========================================");
        System.out.println("Dialog left open for manual inspection. Close it to exit.");
    }
}