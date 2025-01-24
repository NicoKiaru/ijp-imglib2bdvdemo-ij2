package ch.epfl.biop.demos.utils;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.AbstractViewerPanel;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;

public class BdvHelper {

    public static BdvHandle display2D(RealRandomAccessible rra, int r, int g, int b, double min, double max, String name, BdvHandle bdvh) {
        FinalInterval interval = new FinalInterval(new long[]{0,0}, new long[]{1,1});
        BdvOptions options = BdvOptions.options().is2D();
        if (bdvh!=null) options.addTo(bdvh);
        BdvStackSource bss = BdvFunctions.show(rra, interval, name, options); // Issue : not accepting a RealInterval! TODO : mention
        bss.setColor(new ARGBType(ARGBType.rgba(r,g,b,0)));
        ((ConverterSetup)bss.getConverterSetups().get(0)).setDisplayRange(min,max); // Why cast to Converter Setup ? mention issue TODO
        return bss.getBdvHandle();
    }

    public static BdvHandle display3D(RealRandomAccessible rra, int r, int g, int b, double min, double max, String name, BdvHandle bdvh) {
        FinalInterval interval = new FinalInterval(new long[]{0,0,0}, new long[]{1,1,1});
        BdvOptions options = BdvOptions.options();//.is2D();
        if (bdvh!=null) options.addTo(bdvh);
        BdvStackSource bss = BdvFunctions.show(rra, interval, name, options); // Issue : not accepting a RealInterval! TODO : mention
        bss.setColor(new ARGBType(ARGBType.rgba(r,g,b,0)));
        ((ConverterSetup)bss.getConverterSetups().get(0)).setDisplayRange(min,max); // Why cast to Converter Setup ? mention issue TODO
        return bss.getBdvHandle();
    }

    public static ColorTable8 levels(int number, int maxWhite) {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];

        for(int i = 0; i < 256; ++i) {
            double gray = Math.sin((((double) i) / 256.0) * Math.PI * (double) number);
            gray = gray*gray;
            r[i] = (byte) (maxWhite * gray);
            g[i] = (byte) (maxWhite * gray);
            b[i] = (byte) (maxWhite * gray);
        }

        return new ColorTable8(r, g, b);
    }


    // Method to create the left panel with synchronized vertical dividers
    public static JPanel createQuadrant(Component topleft,
                                        Component topRight,
                                        Component bottomLeft,
                                        Component bottomRight) {
        // Create JPanel to hold the entire left side
        JPanel panel = new JPanel(new BorderLayout());

        // Split the top part (topLeft and topRight)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, topleft, topRight);
        topSplitPane.setDividerLocation(200); // Initial divider position
        topSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Split the bottom part (bottomLeft and bottomRight)
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bottomLeft, bottomRight);
        bottomSplitPane.setDividerLocation(200); // Initial divider position
        bottomSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Synchronize the vertical dividers of top and bottom
        synchronizeVerticalDividers(topSplitPane, bottomSplitPane);

        // Finally, split the top and bottom parts vertically
        JSplitPane verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, bottomSplitPane);
        verticalSplitPane.setDividerLocation(150); // Initial divider position
        verticalSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Add the vertical split pane to the left panel
        panel.add(verticalSplitPane, BorderLayout.CENTER);

        return panel;
    }

    // Method to create the left panel with synchronized vertical dividers
    public static JPanel createTri(AbstractViewerPanel topleft,
                                        AbstractViewerPanel topRight,
                                        AbstractViewerPanel bottom) {
        // Create JPanel to hold the entire left side
        JPanel panel = new JPanel(new BorderLayout());

        // Split the top part (topLeft and topRight)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, topleft, topRight);
        topSplitPane.setDividerLocation(200); // Initial divider position
        topSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Split the bottom part (bottomLeft and bottomRight)
        //JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bottom);
        //bottomSplitPane.setDividerLocation(200); // Initial divider position
        //bottomSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Synchronize the vertical dividers of top and bottom
        //synchronizeVerticalDividers(topSplitPane, bottomSplitPane);

        // Finally, split the top and bottom parts vertically
        JSplitPane verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, bottom);
        verticalSplitPane.setDividerLocation(150); // Initial divider position
        verticalSplitPane.setResizeWeight(0.5); // Balance resizing between panels

        // Add the vertical split pane to the left panel
        panel.add(verticalSplitPane, BorderLayout.CENTER);

        return panel;
    }


    // Method to synchronize the vertical dividers between two JSplitPanes
    public static void synchronizeVerticalDividers(JSplitPane topSplitPane, JSplitPane bottomSplitPane) {
        // Listen for changes in the top split pane's divider
        topSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            // When the top divider changes, update the bottom divider to the same position
            int newLocation = (int) evt.getNewValue();
            bottomSplitPane.setDividerLocation(newLocation);
        });

        // Listen for changes in the bottom split pane's divider
        bottomSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            // When the bottom divider changes, update the top divider to the same position
            int newLocation = (int) evt.getNewValue();
            topSplitPane.setDividerLocation(newLocation);
        });
    }

}
