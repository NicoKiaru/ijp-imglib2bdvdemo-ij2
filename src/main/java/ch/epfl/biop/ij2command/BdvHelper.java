package ch.epfl.biop.ij2command;

import bdv.BigDataViewer;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;

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

}
