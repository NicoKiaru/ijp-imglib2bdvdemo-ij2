package ch.epfl.biop;

import io.scif.codec.JPEG2000Codec;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import javax.imageio.ImageIO;
import java.util.Arrays;

public class SimpleIJLaunch {
    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        //DebugTools.enableLogging("INFO");
        DebugTools.setRootLevel("INFO");
        ij.ui().showUI();

        System.out.println("JPEG2000 available: " +
                Arrays.asList(ImageIO.getReaderFormatNames()).contains("jpeg2000"));

        try {
            try {
                Class<?> clazz = Class.forName("com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReaderSpi");
                System.out.println("J2KImageReaderSpi found: " + clazz);
            } catch (ClassNotFoundException e) {
                System.err.println("J2KImageReaderSpi NOT found");
            }

            JPEG2000Codec codec = new JPEG2000Codec();
            System.out.println("Bio-Formats JPEG2000Codec instantiated: " + codec);

            // Check what service it finds
            loci.formats.services.JPEGXRServiceImpl jxr;
            loci.formats.services.JAIIIOServiceImpl jai;

            try {
                jai = new loci.formats.services.JAIIIOServiceImpl();
                System.out.println("JAIIIOService available: " + jai);
            } catch (Throwable t) {
                System.err.println("JAIIIOService failed: " + t.getMessage());
                t.printStackTrace();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
