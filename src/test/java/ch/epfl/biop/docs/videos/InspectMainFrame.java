package ch.epfl.biop.docs.videos;

import java.awt.Frame;

/**
 * Starts Fiji and dumps the IJ main frame's component tree —
 * useful to find class names and screen positions when scripting.
 */
public class InspectMainFrame {

    public static void main(String[] args) {
        Frame frame = FijiCommands.startFiji();
        System.out.print(ComponentInspector.prettyPrint(ComponentInspector.inspect(frame)));
    }
}