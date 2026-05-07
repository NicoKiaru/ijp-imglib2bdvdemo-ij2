package ch.epfl.biop.docs.videos;

import org.scijava.command.CommandModule;

import java.util.concurrent.Future;

/**
 * Drives {@link RobotNumberDemoCommand} via {@link RobotCommands#run(Class, Object...)}
 * to verify the numeric-spinner branch end to end across the styles SciJava
 * renders (plain spinner, bounded spinner, slider variant, scrollbar
 * variant, float, bounded double).
 *
 * Asserts the {@code result} output matches the values we asked for — so
 * a silent reversion (e.g. forgetting to commit the {@link javax.swing.JFormattedTextField}
 * with Enter) would fail the run instead of looking visually correct.
 */
public class RobotNumberDemoTest {

    public static void main(String[] args) throws Exception {
        FijiCommands.startFiji();

        int iterations = 25;
        int blockSize = 128;
        int threshold = 73;
        int threads = 8;
        float regularization = 0.005f;
        double sigma = 2.5;

        Future<CommandModule> future = RobotCommands.run(RobotNumberDemoCommand.class,
                "iterations", iterations,
                "blockSize", blockSize,
                "threshold", threshold,
                "threads", threads,
                "regularization", regularization,
                "sigma", sigma);

        CommandModule module = future.get();
        String result = (String) module.getOutput("result");
        System.out.println("[RobotNumberDemoTest] result = " + result);

        String expected = "iterations=" + iterations
                + ", blockSize=" + blockSize
                + ", threshold=" + threshold
                + ", threads=" + threads
                + ", regularization=" + regularization
                + ", sigma=" + sigma;
        if (!expected.equals(result)) {
            throw new AssertionError("Mismatch.\n  expected: " + expected + "\n  actual:   " + result);
        }
        System.out.println("[RobotNumberDemoTest] OK — all numeric values committed correctly.");
    }
}