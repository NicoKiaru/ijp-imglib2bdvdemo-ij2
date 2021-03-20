package ch.epfl.biop.ij2command;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.scijava.ui.swing.ScijavaSwingUI;
import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorTable;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.ConverterChanger;

import static bdv.ui.BdvDefaultCards.*;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Imglib2 Bdv>Pendulum phase space")
public class PendulumPhaseSpace implements Command {

    @Parameter
    ConvertService cs;

    @Parameter
    CommandService commandService;

    @Parameter
    Context context;

    @Override
    public void run() {
        // d/dt(theta) = w
        // d/dt(w) = -sin(theta)
        // x axis = theta
        // y axis = w

        FunctionRealRandomAccessible<DoubleType> dthetadt =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.set(w);
                }, () -> new DoubleType() );

        FunctionRealRandomAccessible<DoubleType> dwdt =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.set(-Math.sin(theta));
                }, () -> new DoubleType() );

        FunctionRealRandomAccessible<DoubleType> energy =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.set(-Math.cos(theta)+1.0/2.0*w*w); // mind the .0 !
                }, () -> new DoubleType() );

        BdvHandle bdvh = BdvHelper.display2D(dthetadt, 255, 0, 0, -2, 2, "d/dt[theta]", null);

        BdvHelper.display2D(dwdt, 0, 255, 0, -1, 1,"d/dt[w]", bdvh);

        BdvHelper.display2D(energy, 0, 255, 0, -1, 1,"Energy", bdvh);

        SourceAndConverter energySource = bdvh.getViewerPanel().state().getSources().get(2);

        bdvh.getViewerPanel().state().removeSource(energySource);

        ColorTable table = ColorTables.SPECTRUM;//.ICE;

        Converter bdvLut = cs.convert(table, Converter.class);

        ConverterChanger cc = new ConverterChanger(energySource, bdvLut, bdvLut);
        cc.run();

        SourceAndConverter coloredEnergy = cc.get();

        SourceAndConverterServices.getSourceAndConverterDisplayService().show(bdvh, coloredEnergy);

        /*commandService.run(ShiftConverterSetup.class, true,
                );*/

        bdvh.getCardPanel().addCard("Set energy level",
                ScijavaSwingUI.getPanel(context, ShiftConverterSetup.class,
                        "converter",
                        SourceAndConverterServices
                                .getSourceAndConverterDisplayService()
                                .getConverterSetup(coloredEnergy),
                        "width",
                        2),
                true);


        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getSplitPanel().setDividerLocation(0.7);

        AffineTransform3D view  = new AffineTransform3D();

        view.scale( ((double) bdvh.getViewerPanel().getWidth()) / 10.0 );
        view.translate(0,
                0.5*bdvh.getViewerPanel().getHeight() ,0);

        bdvh.getViewerPanel().state().setViewerTransform(view);

        //bdvh.getCardPanel().removeCard(DEFAULT_SOURCES_CARD); // Cannot do this : errors
        bdvh.getCardPanel().removeCard(DEFAULT_SOURCEGROUPS_CARD);
        bdvh.getCardPanel().removeCard(DEFAULT_VIEWERMODES_CARD);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);

    }

    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(PendulumPhaseSpace.class, true);
    }
}
