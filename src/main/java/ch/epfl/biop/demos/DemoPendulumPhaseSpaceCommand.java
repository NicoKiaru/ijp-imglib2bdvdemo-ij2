package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.BdvHelper;
import ch.epfl.biop.demos.utils.ShiftConverterSetupSliderCommand;
import net.imagej.ImageJ;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorTable;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaSwingUI;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.ConverterChanger;

import static bdv.ui.BdvDefaultCards.*;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Pendulum Phase Space")
public class DemoPendulumPhaseSpaceCommand implements Command {

    @Parameter
    ConvertService cs;

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
                    double w = position.getDoublePosition(1);
                    value.set(w);
                }, DoubleType::new);

        FunctionRealRandomAccessible<DoubleType> dwdt =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    value.set(-Math.sin(theta));
                }, DoubleType::new);

        FunctionRealRandomAccessible<DoubleType> energy =
                new FunctionRealRandomAccessible<>(2, (position, value) -> {
                    double theta = position.getDoublePosition(0);
                    double w = position.getDoublePosition(1);
                    value.set(-Math.cos(theta)+1.0/2.0*w*w); // mind the .0 !
                }, DoubleType::new);

        BdvHandle bdvh = BdvHelper.display2D(dthetadt, 255, 0, 0, -2, 2, "d/dt[theta]", null);

        BdvHelper.display2D(dwdt, 0, 255, 0, -1, 1,"d/dt[w]", bdvh);

        BdvHelper.display2D(energy, 0, 255, 0, -1, 1,"Energy", bdvh);

        SourceAndConverter energySource = bdvh.getViewerPanel().state().getSources().get(2);

        // Change LUT of the energy source
        bdvh.getViewerPanel().state().removeSource(energySource);

        ColorTable table = BdvHelper.levels(10, 255);//ColorTables.SPECTRUM;//.ICE;

        Converter bdvLut = cs.convert(table, Converter.class);

        ConverterChanger cc = new ConverterChanger(energySource, bdvLut, bdvLut);
        cc.run();

        SourceAndConverter coloredEnergy = cc.get();

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, coloredEnergy);

        // Puts a command into the bdv panel, which sets the energy level in the phase space
        bdvh.getCardPanel().addCard("Set energy level",
                ScijavaSwingUI.getPanel(context, ShiftConverterSetupSliderCommand.class,
                        "converter",
                        SourceAndConverterServices
                                .getSourceAndConverterService()
                                .getConverterSetup(coloredEnergy),
                        "width",2, "min", -1, "max", 8),
                true);

        // Adjust view
        AffineTransform3D view  = new AffineTransform3D();

        view.scale( ((double) bdvh.getViewerPanel().getWidth()) / 10.0 );
        view.translate(0,
                0.5*bdvh.getViewerPanel().getHeight() ,0);

        bdvh.getViewerPanel().state().setViewerTransform(view);

        // Removes default cards for clarity
        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getSplitPanel().setDividerLocation(0.7);
        //bdvh.getCardPanel().removeCard(DEFAULT_SOURCES_CARD); // Cannot do this : errors
        bdvh.getCardPanel().removeCard(DEFAULT_SOURCEGROUPS_CARD);
        bdvh.getCardPanel().removeCard(DEFAULT_VIEWERMODES_CARD);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);

    }

    public static void main(final String... args) {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(DemoPendulumPhaseSpaceCommand.class, true);
    }

}
