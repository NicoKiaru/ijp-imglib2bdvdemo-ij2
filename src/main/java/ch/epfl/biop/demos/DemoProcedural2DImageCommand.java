package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.service.SourceServices;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @org.scijava.plugin.Menu(label = BdvPgMenus.L1),
                @org.scijava.plugin.Menu(label = BdvPgMenus.L2),
                @org.scijava.plugin.Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Procedural 2D Image")
        })
public class DemoProcedural2DImageCommand implements BdvPlaygroundActionCommand {
    @Override
    public void run() {
        int nDimensions = 2;

        FunctionRealRandomAccessible<DoubleType> wave =
                new FunctionRealRandomAccessible<>(nDimensions,
                        (position, pixel) -> {
                            double px = position.getDoublePosition(0);
                            double py = position.getDoublePosition(1);
                            pixel.set(Math.cos(px)*Math.sin(py));
        }, DoubleType::new);

        BdvStackSource<DoubleType> bdvStack = BdvFunctions.show(wave, Intervals.createMinMax(0, 0, 0, 1, 1, 1), "Wave");
        bdvStack.setDisplayRange(-1,1);
        bdvStack.setColor(new ARGBType(ARGBType.rgba(255.0, 120.0, 0.0, 0.0)));

        SourceServices.getSourceService().register(bdvStack.getSources().get(0));

    }
}
