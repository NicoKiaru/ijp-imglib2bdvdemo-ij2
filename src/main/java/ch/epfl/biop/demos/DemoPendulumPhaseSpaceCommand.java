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
import org.scijava.ItemVisibility;
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

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Demo Pendulum Phase Space</h1>\n" +
            "    <p>This demo illustrates the visualization of a pendulum's phase space using ImgLib2 and BigDataViewer (BDV). The phase space is visualized by showing the rate of change of the pendulum's angle and angular velocity, along with the total energy of the system. The visualization is interactive, allowing users to explore how energy levels affect the pendulum's dynamics.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Define Pendulum Dynamics</h3>\n" +
            "    <p>The demo begins by defining the differential equations governing the pendulum's motion. Specifically, it sets up equations for the rate of change of the pendulum's angle (theta) and angular velocity (omega).</p>\n" +
            "\n" +
            "    <h3>2. Energy Calculation</h3>\n" +
            "    <p>The total mechanical energy of the pendulum is calculated using its current angle and angular velocity. This energy is visualized to show how it varies across different states in the phase space.</p>\n" +
            "\n" +
            "    <h3>3. Visualization Setup</h3>\n" +
            "    <p>A BDV viewer is set up to display the phase space of the pendulum. This includes showing the rate of change of theta and omega, as well as the energy distribution within the phase space.</p>\n" +
            "\n" +
            "    <h3>4. Energy Visualization</h3>\n" +
            "    <p>The energy of the pendulum system is visualized using a color table to provide an intuitive understanding of energy distribution in the phase space. A converter is used to apply this color mapping.</p>\n" +
            "\n" +
            "    <h3>5. Interactive Adjustments</h3>\n" +
            "    <p>An interactive control is added to adjust the energy level displayed in the phase space. This allows users to dynamically explore how different energy levels affect the pendulum's motion.</p>\n" +
            "\n" +
            "    <h3>6. View Adjustments</h3>\n" +
            "    <p>The view of the phase space is adjusted for better visualization, including scaling and translation transformations. Some default UI components are removed to focus on the essential visualizations.</p>\n" +
            "\n" +
            "    <p>This demo showcases how BDV and ImgLib2 can be used to visualize and interactively explore the dynamics of a pendulum in phase space, offering insights into the system's behavior under different energy conditions.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

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
