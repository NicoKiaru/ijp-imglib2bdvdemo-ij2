package ch.epfl.biop.ij2command;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

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

}
