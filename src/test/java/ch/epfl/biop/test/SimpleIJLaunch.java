package ch.epfl.biop.test;

import loci.common.DebugTools;
import net.imagej.ImageJ;

public class SimpleIJLaunch {
    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        //DebugTools.enableLogging("INFO");
        DebugTools.setRootLevel("INFO");
        ij.ui().showUI();

        //ij.command().run(PendulumInAction.class, true);
    }
}
