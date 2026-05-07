package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandModule;

import java.util.concurrent.Future;

/**
 * Drives {@link RobotChoiceDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the choice-widget branches end to end across the styles SciJava
 * renders (default {@link javax.swing.JComboBox}, {@code listBox} which also
 * falls back to a combo, and the two {@link javax.swing.JRadioButton}-based
 * styles).
 *
 * Asserts the {@code result} output matches the values we asked for — so a
 * combo-popup that fails to open, a row-bounds mis-computation, or a wrong
 * radio click would fail the run rather than pass silently.
 */
public class RobotChoiceDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        String pixelType = "Float";
        String mode = "Accurate";
        String direction = "Down";
        String quality = "High";

        Future<CommandModule> future = RobotCommands.run(RobotChoiceDemoCommand.class,
                "pixelType", pixelType,
                "mode", mode,
                "direction", direction,
                "quality", quality);

        CommandModule module = future.get();
        String result = (String) module.getOutput("result");
        System.out.println("[RobotChoiceDemoTest] result = " + result);

        String expected = "pixelType=" + pixelType
                + ", mode=" + mode
                + ", direction=" + direction
                + ", quality=" + quality;
        if (!expected.equals(result)) {
            throw new AssertionError("Mismatch.\n  expected: " + expected + "\n  actual:   " + result);
        }
        System.out.println("[RobotChoiceDemoTest] OK — all choice values committed correctly.");
    }
}